package tr.com.tatmetal.proxy.services;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import tr.com.tatmetal.proxy.auth.AuthSecretCipher;
import tr.com.tatmetal.proxy.auth.AuthTokenTestResult;
import tr.com.tatmetal.proxy.dao.AuthProfileDaoImpl;
import tr.com.tatmetal.proxy.entity.AuthProfileEntity;
import tr.com.tatmetal.proxy.enums.HttpMethodEnum;
import tr.com.tatmetal.proxy.enums.TokenContentTypeEnum;

/**
 * auth_profile CRUD.
 * <ul>
 *   <li>password ve client_secret kayıtta AES ile şifrelenir (bkz. {@link AuthSecretCipher}).</li>
 *   <li>Güncellemede password / clientSecret boş ya da hiç gönderilmezse eski değer korunur;
 *       "-" gönderilirse değer silinir.</li>
 *   <li>Her güncelleme / silmede bellekteki token silinir.</li>
 * </ul>
 */
@Service
public class AuthProfileService {

    /** password / clientSecret için "değeri sil" işareti. */
    public static final String CLEAR = "-";

    private final AuthProfileDaoImpl authProfileDao;
    private final AuthSecretCipher authSecretCipher;
    private final AuthTokenService authTokenService;
    private final ObjectMapper objectMapper;

    public AuthProfileService(AuthProfileDaoImpl authProfileDao, AuthSecretCipher authSecretCipher,
                              AuthTokenService authTokenService, ObjectMapper objectMapper) {
        this.authProfileDao = authProfileDao;
        this.authSecretCipher = authSecretCipher;
        this.authTokenService = authTokenService;
        this.objectMapper = objectMapper;
    }

    public AuthProfileEntity save(AuthProfileEntity entity) {
        prepare(entity, null);
        return authProfileDao.save(entity);
    }

    public List<AuthProfileEntity> saveAll(List<AuthProfileEntity> entities) {
        entities.forEach(e -> prepare(e, null));
        return authProfileDao.saveAll(entities);
    }

    public AuthProfileEntity update(AuthProfileEntity entity) {
        if (entity.getId() == null || entity.getEtag() == null) {
            throw new IllegalArgumentException("Güncelleme için id ve etag zorunludur");
        }
        AuthProfileEntity existing = authProfileDao.getOne(entity.getId(), entity.getEtag());
        if (existing == null) {
            throw new IllegalArgumentException("Kayıt bulunamadı ya da etag güncel değil: " + entity.getId());
        }
        prepare(entity, existing);
        AuthProfileEntity updated = authProfileDao.update(entity);
        authTokenService.invalidate(entity.getId());
        return updated;
    }

    public AuthProfileEntity delete(AuthProfileEntity entity) {
        AuthProfileEntity deleted = authProfileDao.delete(entity);
        authTokenService.invalidate(entity.getId());
        return deleted;
    }

    public AuthProfileEntity getOne(UUID id, UUID etag) {
        return authProfileDao.getOne(id, etag);
    }

    public AuthProfileEntity getById(UUID id) {
        return authProfileDao.getById(id);
    }

    public List<AuthProfileEntity> getByCondition(Map<String, Object> params) {
        return authProfileDao.getByCondition(params == null ? new HashMap<>() : params);
    }

    /** Tanımla token almayı dener; kaynak yoksa (ya da CANCELLED ise) null. */
    public AuthTokenTestResult test(UUID id) {
        AuthProfileEntity profile = authProfileDao.getById(id);
        return profile == null ? null : authTokenService.test(profile);
    }

    // ------------------------------------------------------------------
    // Doğrulama + varsayılanlar + şifreleme
    // ------------------------------------------------------------------

    private void prepare(AuthProfileEntity entity, AuthProfileEntity existing) {
        if (isBlank(entity.getProfileCode())) {
            throw new IllegalArgumentException("profileCode zorunludur");
        }
        if (isBlank(entity.getTokenUrl())) {
            throw new IllegalArgumentException("tokenUrl zorunludur");
        }
        entity.setTokenUrl(entity.getTokenUrl().trim());
        String url = entity.getTokenUrl().toLowerCase(Locale.ROOT);
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("tokenUrl http:// veya https:// ile başlamalıdır");
        }
        if (entity.getTokenMethod() == null || entity.getTokenMethod() == HttpMethodEnum.PASS_THROUGH) {
            entity.setTokenMethod(HttpMethodEnum.POST);
        }
        if (entity.getTokenMethod() != HttpMethodEnum.POST && entity.getTokenMethod() != HttpMethodEnum.PUT) {
            throw new IllegalArgumentException("tokenMethod sadece POST veya PUT olabilir");
        }
        if (entity.getContentType() == null) {
            entity.setContentType(TokenContentTypeEnum.JSON);
        }
        validateJsonObject(entity.getExtraBodyJson(), "extraBodyJson");
        validateJsonObject(entity.getCustomHeadersJson(), "customHeadersJson");

        // NOT NULL kolonlar: tablo default'larıyla aynı değerler
        entity.setProfileDescription(defaultIfBlank(entity.getProfileDescription(), "-"));
        entity.setGrantType(defaultIfBlank(entity.getGrantType(), "password"));
        entity.setClientId(defaultIfBlank(entity.getClientId(), "-"));
        entity.setUsername(defaultIfBlank(entity.getUsername(), "-"));
        entity.setTokenField(defaultIfBlank(entity.getTokenField(), "access_token"));
        entity.setExpiresField(defaultIfBlank(entity.getExpiresField(), "expires_in"));
        entity.setTokenPrefix(entity.getTokenPrefix() == null ? "Bearer" : entity.getTokenPrefix().trim());
        if (entity.getDefaultTtlSeconds() <= 0) {
            entity.setDefaultTtlSeconds(300);
        }
        if (entity.getConnectTimeoutMs() <= 0) {
            entity.setConnectTimeoutMs(5000);
        }
        if (entity.getReadTimeoutMs() <= 0) {
            entity.setReadTimeoutMs(30000);
        }

        entity.setPassword(resolveSecret(entity.getPassword(), existing == null ? null : existing.getPassword()));
        entity.setClientSecret(resolveSecret(entity.getClientSecret(),
                existing == null ? null : existing.getClientSecret()));
    }

    /**
     * Gelen değer boşsa eski (şifreli) değer korunur, "-" ise silinir, aksi halde şifrelenir.
     * Anahtar tanımlı değilse düz metin kaydetmek yerine hata verilir.
     */
    private String resolveSecret(String incoming, String existingStored) {
        if (incoming == null || incoming.isEmpty()) {
            return existingStored;
        }
        if (CLEAR.equals(incoming)) {
            return null;
        }
        if (AuthSecretCipher.isEncrypted(incoming)) {
            throw new IllegalArgumentException("Şifreli (enc:v1:) değer doğrudan gönderilemez; düz metin gönderin");
        }
        if (!authSecretCipher.isConfigured()) {
            throw new IllegalArgumentException("Şifre / client_secret kaydetmek için uygulamada "
                    + "AUTH_PROFILE_SECRET_KEY ortam değişkeni (auth.profile.secret-key) tanımlı olmalıdır");
        }
        return authSecretCipher.encrypt(incoming);
    }

    private void validateJsonObject(String json, String field) {
        if (json == null || json.isBlank() || "-".equals(json.trim())) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException(field + " bir JSON nesnesi olmalıdır. Örn: {\"realm\":\"zenergy\"}");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(field + " geçerli JSON değil");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }
}
