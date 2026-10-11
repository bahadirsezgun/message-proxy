package tr.com.tatmetal.proxy.swagger;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import feign.Request;
import tr.com.tatmetal.proxy.client.SwaggerDocClient;

/**
 * Verilen adresten swagger/OpenAPI doküman(lar)ını bulur ve okur.
 * <p>
 * Kabul edilen adresler:
 * <ul>
 *   <li>swagger-ui sayfası: {@code http://host:8080/swagger-ui/index.html}, {@code .../swagger-ui.html}</li>
 *   <li>swagger-ui'nin {@code ?url=} veya {@code ?configUrl=} parametresi olan adresi</li>
 *   <li>springdoc swagger-config: {@code .../v3/api-docs/swagger-config} (gruplu dokümanlar desteklenir)</li>
 *   <li>Doğrudan doküman: {@code .../v3/api-docs}, {@code .../v2/api-docs}, {@code .../swagger.json}, YAML dahil</li>
 *   <li>Servis kök adresi: {@code http://host:8080} (standart doküman adresleri sırayla denenir)</li>
 * </ul>
 */
@Component
public class SwaggerDocLoader {

    /** swagger-ui sayfası verildiğinde, servis kök adresine eklenip sırayla denenecek doküman adresleri. */
    private static final String[] CANDIDATE_PATHS = {
            "/v3/api-docs/swagger-config", "/v3/api-docs", "/v2/api-docs", "/swagger.json", "/openapi.json"};

    private static final String NO_GROUP = "-";

    private final SwaggerDocClient swaggerDocClient;
    private final Request.Options options;
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final YAMLMapper yamlMapper = new YAMLMapper();
    private final Logger logger = LoggerFactory.getLogger(SwaggerDocLoader.class);

    public SwaggerDocLoader(SwaggerDocClient swaggerDocClient,
                            @Value("${swagger.sync.connect-timeout-ms:5000}") int connectTimeoutMs,
                            @Value("${swagger.sync.read-timeout-ms:30000}") int readTimeoutMs) {
        this.swaggerDocClient = swaggerDocClient;
        this.options = new Request.Options(connectTimeoutMs, TimeUnit.MILLISECONDS,
                readTimeoutMs, TimeUnit.MILLISECONDS, true);
    }

    /**
     * @return en az bir doküman; bulunamazsa {@link IllegalStateException}
     */
    public List<SwaggerDocument> load(String swaggerUrl) {
        return load(swaggerUrl, Map.of());
    }

    /**
     * @param extraHeaders her doküman isteğine eklenecek header'lar (ör. Authorization: Bearer ...).
     *                     swagger-config ve grup dokümanları dahil tüm isteklere eklenir.
     * @return en az bir doküman; bulunamazsa {@link IllegalStateException}. Denemelerden biri 401/403 döndüyse
     *         {@link SwaggerUnauthorizedException} (çağıran token'ı yenileyip tekrar deneyebilir).
     */
    public List<SwaggerDocument> load(String swaggerUrl, Map<String, String> extraHeaders) {
        if (swaggerUrl == null || swaggerUrl.isBlank()) {
            throw new IllegalArgumentException("swagger_url boş olamaz");
        }
        URI input = URI.create(swaggerUrl.trim());
        if (input.getScheme() == null || input.getHost() == null) {
            throw new IllegalArgumentException("swagger_url tam adres olmalı (http://... ya da https://...): " + swaggerUrl);
        }
        Attempts attempts = new Attempts(extraHeaders);

        // 1) swagger-ui adresinde ?url= / ?configUrl= varsa doğrudan onu kullan
        Map<String, String> query = parseQuery(input.getRawQuery());
        if (query.containsKey("configUrl")) {
            URI configUri = input.resolve(query.get("configUrl"));
            JsonNode config = fetch(configUri, attempts);
            if (config != null && isSwaggerConfig(config)) {
                return fromSwaggerConfig(config, configUri, attempts);
            }
        }
        if (query.containsKey("url")) {
            URI specUri = input.resolve(query.get("url"));
            JsonNode spec = fetch(specUri, attempts);
            if (spec != null && isSpec(spec)) {
                return List.of(new SwaggerDocument(NO_GROUP, specUri, spec));
            }
        }

        // 2) Verilen adresin kendisi doküman ya da swagger-config olabilir
        URI direct = withoutQueryAndFragment(input);
        JsonNode node = fetch(direct, attempts);
        if (node != null) {
            if (isSpec(node)) {
                return List.of(new SwaggerDocument(NO_GROUP, direct, node));
            }
            if (isSwaggerConfig(node)) {
                return fromSwaggerConfig(node, direct, attempts);
            }
        }

        // Sunucuya hiç ulaşılamıyorsa diğer adresleri denemek boşuna (her biri timeout bekletir)
        if (attempts.unreachable) {
            throw attempts.fail("Swagger adresine ulaşılamadı: " + attempts);
        }

        // 3) swagger-ui sayfası (HTML) ya da servis kök adresi: standart doküman adreslerini dene
        String base = serviceBase(input);
        for (String candidatePath : CANDIDATE_PATHS) {
            URI candidate = URI.create(base + candidatePath);
            if (candidate.equals(direct)) {
                continue;
            }
            JsonNode candidateNode = fetch(candidate, attempts);
            if (candidateNode == null) {
                continue;
            }
            if (isSpec(candidateNode)) {
                return List.of(new SwaggerDocument(NO_GROUP, candidate, candidateNode));
            }
            if (isSwaggerConfig(candidateNode)) {
                return fromSwaggerConfig(candidateNode, candidate, attempts);
            }
        }

        throw attempts.fail("Swagger/OpenAPI dokümanı bulunamadı. Denenen adresler: " + attempts);
    }

    // ------------------------------------------------------------------
    // springdoc swagger-config
    // ------------------------------------------------------------------

    private List<SwaggerDocument> fromSwaggerConfig(JsonNode config, URI configUri, Attempts attempts) {
        List<SwaggerDocument> result = new ArrayList<>();
        JsonNode urls = config.path("urls");
        if (urls.isArray() && urls.size() > 0) {
            for (JsonNode entry : urls) {
                String url = entry.path("url").asText("");
                if (url.isBlank()) {
                    continue;
                }
                String group = entry.path("name").asText(NO_GROUP);
                URI specUri = configUri.resolve(url);
                JsonNode spec = fetch(specUri, attempts);
                if (spec == null || !isSpec(spec)) {
                    throw attempts.fail("Grup dokümanı okunamadı. grup=" + group
                            + ", adres=" + specUri + ", denemeler: " + attempts);
                }
                result.add(new SwaggerDocument(group.isBlank() ? NO_GROUP : group, specUri, spec));
            }
        } else if (config.hasNonNull("url")) {
            URI specUri = configUri.resolve(config.get("url").asText());
            JsonNode spec = fetch(specUri, attempts);
            if (spec == null || !isSpec(spec)) {
                throw attempts.fail("Doküman okunamadı. adres=" + specUri + ", denemeler: " + attempts);
            }
            result.add(new SwaggerDocument(NO_GROUP, specUri, spec));
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("swagger-config içinde doküman adresi yok: " + configUri);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // HTTP + parse
    // ------------------------------------------------------------------

    /** Adresi okur; JSON/YAML değilse (HTML vb.), 2xx dönmezse ya da ulaşılamazsa null döner. */
    private JsonNode fetch(URI uri, Attempts attempts) {
        try {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Accept", "application/json, application/yaml;q=0.9, */*;q=0.8");
            headers.putAll(attempts.extraHeaders);
            ResponseEntity<byte[]> response = swaggerDocClient.get(uri, options, headers);
            if (!response.getStatusCode().is2xxSuccessful()) {
                attempts.add(uri, "HTTP " + response.getStatusCodeValue());
                return null;
            }
            JsonNode node = parse(response.getBody());
            attempts.add(uri, node == null ? "JSON/YAML değil" : "OK");
            return node;
        } catch (feign.FeignException e) {
            if (e.status() <= 0) {
                attempts.unreachable = true;
            }
            if (e.status() == 401 || e.status() == 403) {
                attempts.unauthorized = true;
                attempts.add(uri, "HTTP " + e.status() + " (yetkisiz)");
            } else {
                attempts.add(uri, e.status() > 0 ? "HTTP " + e.status() : "ulaşılamadı (" + rootMessage(e) + ")");
            }
            logger.debug("Swagger adresi okunamadı. uri={}, error={}", uri, e.toString());
            return null;
        } catch (Exception e) {
            attempts.add(uri, rootMessage(e));
            logger.debug("Swagger adresi okunamadı. uri={}, error={}", uri, e.toString());
            return null;
        }
    }

    JsonNode parse(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        String text = new String(body, StandardCharsets.UTF_8).trim();
        if (text.startsWith("﻿")) {
            text = text.substring(1).trim();
        }
        if (text.isEmpty() || text.startsWith("<")) {
            return null;
        }
        try {
            JsonNode node = text.startsWith("{") ? jsonMapper.readTree(text) : yamlMapper.readTree(text);
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    static boolean isSpec(JsonNode node) {
        return (node.has("openapi") || node.has("swagger")) && node.path("paths").isObject();
    }

    static boolean isSwaggerConfig(JsonNode node) {
        return !isSpec(node) && (node.path("urls").isArray() || node.path("url").isTextual());
    }

    // ------------------------------------------------------------------
    // URL yardımcıları
    // ------------------------------------------------------------------

    /**
     * swagger-ui sayfasının ait olduğu servisin kök adresi (context path dahil).
     * Örn: http://h:8080/ctx/swagger-ui/index.html → http://h:8080/ctx
     */
    static String serviceBase(URI input) {
        String path = input.getRawPath() == null ? "" : input.getRawPath();
        int idx = path.indexOf("/swagger-ui");
        if (idx >= 0) {
            path = path.substring(0, idx);
        } else {
            for (String known : new String[]{"/v3/api-docs", "/v2/api-docs", "/swagger.json", "/openapi.json",
                    "/swagger-resources"}) {
                int k = path.indexOf(known);
                if (k >= 0) {
                    path = path.substring(0, k);
                    break;
                }
            }
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return input.getScheme() + "://" + input.getRawAuthority() + path;
    }

    private static URI withoutQueryAndFragment(URI uri) {
        String s = uri.toString();
        int cut = s.length();
        int q = s.indexOf('?');
        int f = s.indexOf('#');
        if (q >= 0) {
            cut = q;
        }
        if (f >= 0 && f < cut) {
            cut = f;
        }
        return URI.create(s.substring(0, cut));
    }

    static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            if (!value.isBlank()) {
                result.put(key, value);
            }
        }
        return result;
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
    }

    /** Hata mesajında hangi adreslerin denendiğini göstermek için. */
    private static final class Attempts {
        private final Map<String, String> results = new LinkedHashMap<>();
        /** Her isteğe eklenecek header'lar (değerleri hata mesajına yazılmaz). */
        private final Map<String, String> extraHeaders;
        /** Bağlantı kurulamadı / timeout (HTTP cevabı hiç alınamadı). */
        private boolean unreachable;
        /** En az bir adres 401 / 403 döndü. */
        private boolean unauthorized;

        Attempts(Map<String, String> extraHeaders) {
            this.extraHeaders = extraHeaders == null ? Map.of() : extraHeaders;
        }

        /** 401/403 görüldüyse {@link SwaggerUnauthorizedException}, aksi halde {@link IllegalStateException}. */
        IllegalStateException fail(String message) {
            if (!unauthorized) {
                return new IllegalStateException(message);
            }
            boolean hasAuthorization = extraHeaders.keySet().stream().anyMatch("Authorization"::equalsIgnoreCase);
            return new SwaggerUnauthorizedException(message + (hasAuthorization
                    ? " — Gönderilen token kabul edilmedi (süresi dolmuş ya da yetkisi yok)."
                    : " — Swagger dokümanı yetki istiyor; kaynağa bir token tanımı bağlayın (authProfileId)."),
                    hasAuthorization);
        }

        void add(URI uri, String result) {
            results.put(uri.toString(), result);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            results.forEach((uri, result) -> {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(uri).append(" → ").append(result);
            });
            return sb.toString();
        }
    }
}
