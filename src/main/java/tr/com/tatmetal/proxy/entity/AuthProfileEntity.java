package tr.com.tatmetal.proxy.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import tr.com.zenergy.mini_jpa.annotation.dao.Column;
import tr.com.zenergy.mini_jpa.annotation.dao.Entity;
import tr.com.zenergy.mini_jpa.entity.BaseEntity;
import tr.com.zenergy.mini_jpa.util.TypeUtil;
import tr.com.tatmetal.proxy.enums.HttpMethodEnum;
import tr.com.tatmetal.proxy.enums.TokenContentTypeEnum;

/**
 * Korunan bir servise erişmek için token alma tanımı.
 * Standart alanlar (id, id_serial, etag, status, ...) BaseEntity'den gelir.
 * <p>
 * {@code password} ve {@code clientSecret} JSON cevaplarında hiç dönmez (WRITE_ONLY); dolu olup olmadıkları
 * {@code passwordSet} / {@code clientSecretSet} ile görülür. Veritabanında şifreli ({@code enc:v1:...}) saklanır.
 */
@Entity(tableName = "message_proxy.auth_profile")
public class AuthProfileEntity extends BaseEntity {
    @Column(name = "profile_code") private String profileCode;
    @Column(name = "profile_description") private String profileDescription;
    @Column(name = "token_url") private String tokenUrl;
    @Column(name = "token_method", isTyped = true, type = TypeUtil.I_ENUM_NUM)
    private HttpMethodEnum tokenMethod;
    @Column(name = "content_type", isTyped = true, type = TypeUtil.I_ENUM_NUM)
    private TokenContentTypeEnum contentType;
    @Column(name = "grant_type") private String grantType;
    @Column(name = "client_id") private String clientId;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "client_secret") private String clientSecret;
    @Column(name = "username") private String username;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "password") private String password;
    @Column(name = "extra_body_json") private String extraBodyJson;
    @Column(name = "custom_headers_json") private String customHeadersJson;
    @Column(name = "token_field") private String tokenField;
    @Column(name = "expires_field") private String expiresField;
    @Column(name = "default_ttl_seconds") private int defaultTtlSeconds;
    @Column(name = "token_prefix") private String tokenPrefix;
    @Column(name = "connect_timeout_ms") private int connectTimeoutMs;
    @Column(name = "read_timeout_ms") private int readTimeoutMs;

    public AuthProfileEntity() {
    }

    public String getProfileCode() {
        return profileCode;
    }

    public void setProfileCode(String profileCode) {
        this.profileCode = profileCode;
    }

    public String getProfileDescription() {
        return profileDescription;
    }

    public void setProfileDescription(String profileDescription) {
        this.profileDescription = profileDescription;
    }

    public String getTokenUrl() {
        return tokenUrl;
    }

    public void setTokenUrl(String tokenUrl) {
        this.tokenUrl = tokenUrl;
    }

    public HttpMethodEnum getTokenMethod() {
        return tokenMethod;
    }

    public void setTokenMethod(HttpMethodEnum tokenMethod) {
        this.tokenMethod = tokenMethod;
    }

    public TokenContentTypeEnum getContentType() {
        return contentType;
    }

    public void setContentType(TokenContentTypeEnum contentType) {
        this.contentType = contentType;
    }

    public String getGrantType() {
        return grantType;
    }

    public void setGrantType(String grantType) {
        this.grantType = grantType;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getExtraBodyJson() {
        return extraBodyJson;
    }

    public void setExtraBodyJson(String extraBodyJson) {
        this.extraBodyJson = extraBodyJson;
    }

    public String getCustomHeadersJson() {
        return customHeadersJson;
    }

    public void setCustomHeadersJson(String customHeadersJson) {
        this.customHeadersJson = customHeadersJson;
    }

    public String getTokenField() {
        return tokenField;
    }

    public void setTokenField(String tokenField) {
        this.tokenField = tokenField;
    }

    public String getExpiresField() {
        return expiresField;
    }

    public void setExpiresField(String expiresField) {
        this.expiresField = expiresField;
    }

    public int getDefaultTtlSeconds() {
        return defaultTtlSeconds;
    }

    public void setDefaultTtlSeconds(int defaultTtlSeconds) {
        this.defaultTtlSeconds = defaultTtlSeconds;
    }

    public String getTokenPrefix() {
        return tokenPrefix;
    }

    public void setTokenPrefix(String tokenPrefix) {
        this.tokenPrefix = tokenPrefix;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    /** Şifre tanımlı mı (değerin kendisi dışarı verilmez). */
    @JsonProperty(value = "passwordSet", access = JsonProperty.Access.READ_ONLY)
    public boolean isPasswordSet() {
        return password != null && !password.isBlank();
    }

    /** client_secret tanımlı mı (değerin kendisi dışarı verilmez). */
    @JsonProperty(value = "clientSecretSet", access = JsonProperty.Access.READ_ONLY)
    public boolean isClientSecretSet() {
        return clientSecret != null && !clientSecret.isBlank();
    }

    /** password, clientSecret ve extraBodyJson (gizli alan içerebilir) bilerek yazılmaz. */
    @Override
    public String toString() {
        return "AuthProfileEntity{"
                + "profileCode='" + profileCode + '\''
                + ", profileDescription='" + profileDescription + '\''
                + ", tokenUrl='" + tokenUrl + '\''
                + ", tokenMethod=" + tokenMethod
                + ", contentType=" + contentType
                + ", grantType='" + grantType + '\''
                + ", clientId='" + clientId + '\''
                + ", username='" + username + '\''
                + ", customHeadersJson='" + customHeadersJson + '\''
                + ", tokenField='" + tokenField + '\''
                + ", expiresField='" + expiresField + '\''
                + ", defaultTtlSeconds=" + defaultTtlSeconds
                + ", tokenPrefix='" + tokenPrefix + '\''
                + ", connectTimeoutMs=" + connectTimeoutMs
                + ", readTimeoutMs=" + readTimeoutMs
                + ", passwordSet=" + isPasswordSet()
                + ", clientSecretSet=" + isClientSecretSet()
                + '}';
    }
}
