package tr.com.tatmetal.proxy.services;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import feign.FeignException;
import feign.Request;
import tr.com.tatmetal.proxy.auth.AuthSecretCipher;
import tr.com.tatmetal.proxy.auth.AuthTokenException;
import tr.com.tatmetal.proxy.auth.AuthTokenTestResult;
import tr.com.tatmetal.proxy.client.AuthTokenClient;
import tr.com.tatmetal.proxy.entity.AuthProfileEntity;
import tr.com.tatmetal.proxy.enums.HttpMethodEnum;
import tr.com.tatmetal.proxy.enums.TokenContentTypeEnum;

/**
 * Tablo bağımsız servis: auth_profile tanımına göre token alır ve Authorization header değerini üretir.
 * <ul>
 *   <li>Gövde tanımın alanlarından kurulur: grant_type, client_id, client_secret, username, password
 *       ('-' veya boş olanlar gönderilmez) + extra_body_json. JSON veya form olarak gönderilir.</li>
 *   <li>custom_headers_json içindeki header'lar isteğe eklenir (ör. realm).</li>
 *   <li>Token bellekte saklanır; süresi (expires_in − 30 sn) dolana ya da tanım değişene (etag) kadar
 *       aynı token kullanılır. Bu yüzden SSO'ya her senkronizasyonda gidilmez.</li>
 *   <li>Şifre, client_secret ve token hiçbir log'a / hata mesajına yazılmaz.</li>
 * </ul>
 */
@Service
public class AuthTokenService {

    /** Token, sunucudaki süresi dolmadan bu kadar önce yenilenir. */
    private static final long EXPIRY_SKEW_SECONDS = 30;
    private static final int MAX_ERROR_BODY = 300;
    private static final String NONE = "-";

    private final AuthTokenClient authTokenClient;
    private final AuthSecretCipher authSecretCipher;
    private final ObjectMapper objectMapper;
    private final Map<UUID, CachedToken> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Object> locks = new ConcurrentHashMap<>();
    private final Logger logger = LoggerFactory.getLogger(AuthTokenService.class);

    public AuthTokenService(AuthTokenClient authTokenClient, AuthSecretCipher authSecretCipher,
                            ObjectMapper objectMapper) {
        this.authTokenClient = authTokenClient;
        this.authSecretCipher = authSecretCipher;
        this.objectMapper = objectMapper;
    }

    /**
     * Authorization header değeri (ör. {@code Bearer eyJ...}). Bellekte geçerli token varsa SSO'ya gidilmez.
     *
     * @throws AuthTokenException token alınamazsa
     */
    public String getAuthorizationHeader(AuthProfileEntity profile) {
        CachedToken cached = cache.get(profile.getId());
        if (cached != null && cached.isValidFor(profile)) {
            return cached.headerValue;
        }
        synchronized (lockFor(profile.getId())) {
            cached = cache.get(profile.getId());
            if (cached != null && cached.isValidFor(profile)) {
                return cached.headerValue;
            }
            TokenResponse token = requestToken(profile);
            CachedToken fresh = new CachedToken(profile.getEtag(), headerValue(profile, token.accessToken),
                    System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(cacheSeconds(token.expiresInSeconds)));
            cache.put(profile.getId(), fresh);
            logger.info("Token alındı. profile={}, expiresIn={}s", profile.getProfileCode(), token.expiresInSeconds);
            return fresh.headerValue;
        }
    }

    /** Bellekteki token'ı siler (ör. hedef 401 döndüyse); bir sonraki çağrıda yeni token alınır. */
    public void invalidate(UUID authProfileId) {
        if (authProfileId != null && cache.remove(authProfileId) != null) {
            logger.info("Bellekteki token silindi. authProfileId={}", authProfileId);
        }
    }

    /** Bellekteki token'ı kullanmadan yeni token almayı dener. Başarılıysa bellekteki token da yenilenir. */
    public AuthTokenTestResult test(AuthProfileEntity profile) {
        long start = System.nanoTime();
        AuthTokenTestResult result = new AuthTokenTestResult();
        result.setAuthProfileId(profile.getId());
        result.setProfileCode(profile.getProfileCode());
        try {
            TokenResponse token = requestToken(profile);
            cache.put(profile.getId(), new CachedToken(profile.getEtag(), headerValue(profile, token.accessToken),
                    System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(cacheSeconds(token.expiresInSeconds))));
            result.setSuccess(true);
            result.setHttpStatus(token.httpStatus);
            result.setExpiresInSeconds(token.expiresInSeconds);
            result.setTokenPreview(preview(token.accessToken));
            result.setMessage("Token alındı");
        } catch (AuthTokenException e) {
            result.setSuccess(false);
            result.setHttpStatus(e.getHttpStatus());
            result.setMessage(e.getMessage());
        }
        result.setDurationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        return result;
    }

    // ------------------------------------------------------------------
    // Token isteği
    // ------------------------------------------------------------------

    TokenResponse requestToken(AuthProfileEntity profile) {
        String code = profile.getProfileCode();
        URI target;
        try {
            target = URI.create(profile.getTokenUrl().trim());
        } catch (RuntimeException e) {
            throw new AuthTokenException("Token adresi geçersiz (" + code + "): " + profile.getTokenUrl(), 0, e);
        }
        TokenContentTypeEnum contentType = profile.getContentType() == null
                ? TokenContentTypeEnum.JSON : profile.getContentType();
        Map<String, String> headers = buildHeaders(profile, contentType);
        byte[] body = buildBody(profile, contentType);
        Request.Options options = new Request.Options(
                positiveOr(profile.getConnectTimeoutMs(), 5000), TimeUnit.MILLISECONDS,
                positiveOr(profile.getReadTimeoutMs(), 30000), TimeUnit.MILLISECONDS, true);

        ResponseEntity<byte[]> response;
        try {
            response = profile.getTokenMethod() == HttpMethodEnum.PUT
                    ? authTokenClient.put(target, options, headers, body)
                    : authTokenClient.post(target, options, headers, body);
        } catch (FeignException e) {
            if (e.status() <= 0) {
                throw new AuthTokenException("Token servisine ulaşılamadı (" + code + "): " + target
                        + " (" + rootMessage(e) + ")", 0, e);
            }
            throw new AuthTokenException("Token alınamadı (" + code + "): HTTP " + e.status()
                    + errorBody(e), e.status(), e);
        }

        int status = response.getStatusCodeValue();
        JsonNode json;
        try {
            byte[] responseBody = response.getBody();
            json = responseBody == null || responseBody.length == 0 ? null : objectMapper.readTree(responseBody);
        } catch (Exception e) {
            json = null;
        }
        if (json == null || !json.isObject()) {
            throw new AuthTokenException("Token cevabı JSON nesnesi değil (" + code + "), HTTP " + status, status);
        }
        String tokenField = defaultIfBlank(profile.getTokenField(), "access_token");
        JsonNode tokenNode = path(json, tokenField);
        if (tokenNode == null || !tokenNode.isValueNode() || tokenNode.asText().isBlank()) {
            throw new AuthTokenException("Token cevabında '" + tokenField + "' alanı yok (" + code
                    + "). Cevaptaki alanlar: " + fieldNames(json), status);
        }
        long expiresIn = 0;
        JsonNode expiresNode = path(json, defaultIfBlank(profile.getExpiresField(), "expires_in"));
        if (expiresNode != null && expiresNode.isValueNode()) {
            expiresIn = expiresNode.asLong(0);
        }
        if (expiresIn <= 0) {
            expiresIn = positiveOr(profile.getDefaultTtlSeconds(), 300);
        }
        return new TokenResponse(tokenNode.asText(), expiresIn, status);
    }

    private Map<String, String> buildHeaders(AuthProfileEntity profile, TokenContentTypeEnum contentType) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", contentType.mediaType());
        headers.put("Accept", "application/json");
        JsonNode custom = readObject(profile.getCustomHeadersJson(), "custom_headers_json", profile.getProfileCode());
        if (custom != null) {
            custom.fields().forEachRemaining(e -> headers.put(e.getKey(),
                    e.getValue().isValueNode() ? e.getValue().asText() : e.getValue().toString()));
        }
        return headers;
    }

    /** Alan sırası: grant_type, client_id, client_secret, username, password, sonra extra_body_json (aynı ad varsa ezer). */
    byte[] buildBody(AuthProfileEntity profile, TokenContentTypeEnum contentType) {
        ObjectNode body = objectMapper.createObjectNode();
        putIfPresent(body, "grant_type", profile.getGrantType());
        putIfPresent(body, "client_id", profile.getClientId());
        putIfPresent(body, "client_secret", secret(profile.getClientSecret(), "client_secret", profile));
        putIfPresent(body, "username", profile.getUsername());
        putIfPresent(body, "password", secret(profile.getPassword(), "password", profile));
        JsonNode extra = readObject(profile.getExtraBodyJson(), "extra_body_json", profile.getProfileCode());
        if (extra != null) {
            body.setAll((ObjectNode) extra);
        }
        if (contentType == TokenContentTypeEnum.FORM) {
            StringBuilder sb = new StringBuilder();
            Iterator<Map.Entry<String, JsonNode>> fields = body.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (sb.length() > 0) {
                    sb.append('&');
                }
                String value = field.getValue().isValueNode() ? field.getValue().asText() : field.getValue().toString();
                sb.append(URLEncoder.encode(field.getKey(), StandardCharsets.UTF_8)).append('=')
                        .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            throw new AuthTokenException("Token isteği gövdesi oluşturulamadı (" + profile.getProfileCode() + ")", 0, e);
        }
    }

    private String secret(String stored, String field, AuthProfileEntity profile) {
        try {
            return authSecretCipher.decrypt(stored);
        } catch (IllegalStateException e) {
            throw new AuthTokenException(field + " çözülemedi (" + profile.getProfileCode() + "): " + e.getMessage(), 0, e);
        }
    }

    private JsonNode readObject(String json, String column, String code) {
        if (json == null || json.isBlank() || NONE.equals(json.trim())) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw new AuthTokenException(column + " bir JSON nesnesi olmalı (" + code + ")", 0);
            }
            return node;
        } catch (AuthTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthTokenException(column + " geçerli JSON değil (" + code + ")", 0, e);
        }
    }

    // ------------------------------------------------------------------
    // Yardımcılar
    // ------------------------------------------------------------------

    private static void putIfPresent(ObjectNode body, String name, String value) {
        if (value != null && !value.isBlank() && !NONE.equals(value.trim())) {
            body.put(name, value);
        }
    }

    /** "data.access_token" gibi nokta ile ayrılmış yol. */
    static JsonNode path(JsonNode root, String dottedPath) {
        JsonNode node = root;
        for (String part : dottedPath.trim().split("\\.")) {
            if (node == null || !node.isObject()) {
                return null;
            }
            node = node.get(part);
        }
        return node;
    }

    private static String headerValue(AuthProfileEntity profile, String token) {
        String prefix = profile.getTokenPrefix() == null ? "Bearer" : profile.getTokenPrefix().trim();
        return prefix.isEmpty() || NONE.equals(prefix) ? token : prefix + " " + token;
    }

    /** Sunucudaki süreden biraz önce yenilensin; çok kısa sürelerde sürenin %80'i. */
    static long cacheSeconds(long expiresIn) {
        long seconds = expiresIn > EXPIRY_SKEW_SECONDS * 2 ? expiresIn - EXPIRY_SKEW_SECONDS : (long) (expiresIn * 0.8);
        return Math.max(1, seconds);
    }

    private static String preview(String token) {
        return token.length() <= 10 ? "…" : token.substring(0, 10) + "…";
    }

    private static String errorBody(FeignException e) {
        String body = e.contentUTF8();
        if (body == null || body.isBlank()) {
            return "";
        }
        body = body.replaceAll("\\s+", " ").trim();
        return " - " + (body.length() > MAX_ERROR_BODY ? body.substring(0, MAX_ERROR_BODY) + "…" : body);
    }

    private static String fieldNames(JsonNode json) {
        StringBuilder sb = new StringBuilder();
        json.fieldNames().forEachRemaining(n -> sb.append(sb.length() == 0 ? "" : ", ").append(n));
        return sb.length() == 0 ? "(yok)" : sb.toString();
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() || NONE.equals(value.trim()) ? defaultValue : value.trim();
    }

    private static int positiveOr(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private Object lockFor(UUID id) {
        return locks.computeIfAbsent(id, k -> new Object());
    }

    /** Alınan token (değer sadece bellekte tutulur). */
    static final class TokenResponse {
        final String accessToken;
        final long expiresInSeconds;
        final int httpStatus;

        TokenResponse(String accessToken, long expiresInSeconds, int httpStatus) {
            this.accessToken = accessToken;
            this.expiresInSeconds = expiresInSeconds;
            this.httpStatus = httpStatus;
        }

        @Override
        public String toString() {
            return "TokenResponse{expiresInSeconds=" + expiresInSeconds + ", httpStatus=" + httpStatus + '}';
        }
    }

    private static final class CachedToken {
        final UUID etag;
        final String headerValue;
        final long expiresAtMillis;

        CachedToken(UUID etag, String headerValue, long expiresAtMillis) {
            this.etag = etag;
            this.headerValue = headerValue;
            this.expiresAtMillis = expiresAtMillis;
        }

        /** Süresi dolmadıysa ve tanım değişmediyse (etag aynıysa) geçerli. */
        boolean isValidFor(AuthProfileEntity profile) {
            return System.currentTimeMillis() < expiresAtMillis && Objects.equals(etag, profile.getEtag());
        }

        @Override
        public String toString() {
            return "CachedToken{expiresAtMillis=" + expiresAtMillis + '}';
        }
    }
}
