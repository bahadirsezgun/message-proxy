package tr.com.tatmetal.proxy.entity;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import tr.com.zenergy.mini_jpa.annotation.dao.Column;
import tr.com.zenergy.mini_jpa.annotation.dao.Entity;
import tr.com.zenergy.mini_jpa.entity.BaseEntity;
import tr.com.zenergy.mini_jpa.util.TypeUtil;
import tr.com.tatmetal.proxy.enums.YesNoEnum;

/**
 * Entegre olunan uygulama: adres, base path, swagger bilgisi, amaç.
 * swagger_source (1:1, app_registry_id) ve proxy_route (target_ / source_app_registry_id) bu kayda bağlanır.
 * Standart alanlar (id, id_serial, etag, status, ...) BaseEntity'den gelir.
 * <p>
 * {@code fullBaseUrl} ve {@code swaggerUrl} hesaplanan, sadece okunabilen JSON alanlarıdır.
 */
@Entity(tableName = "message_proxy.app_registry")
public class AppRegistryEntity extends BaseEntity {

    private static final String NONE = "-";

    @Column(name = "app_code") private String appCode;
    @Column(name = "app_name") private String appName;
    @Column(name = "app_purpose") private String appPurpose;
    @Column(name = "app_owner") private String appOwner;
    @Column(name = "base_url") private String baseUrl;
    @Column(name = "base_path") private String basePath;
    @Column(name = "has_swagger", isTyped = true, type = TypeUtil.I_ENUM_NUM)
    private YesNoEnum hasSwagger;
    @Column(name = "swagger_path") private String swaggerPath;
    @Column(name = "auth_profile_id", isTyped = true, type = "UUID") private UUID authProfileId;

    public AppRegistryEntity() {
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getAppPurpose() {
        return appPurpose;
    }

    public void setAppPurpose(String appPurpose) {
        this.appPurpose = appPurpose;
    }

    public String getAppOwner() {
        return appOwner;
    }

    public void setAppOwner(String appOwner) {
        this.appOwner = appOwner;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public YesNoEnum getHasSwagger() {
        return hasSwagger;
    }

    public void setHasSwagger(YesNoEnum hasSwagger) {
        this.hasSwagger = hasSwagger;
    }

    public String getSwaggerPath() {
        return swaggerPath;
    }

    public void setSwaggerPath(String swaggerPath) {
        this.swaggerPath = swaggerPath;
    }

    public UUID getAuthProfileId() {
        return authProfileId;
    }

    public void setAuthProfileId(UUID authProfileId) {
        this.authProfileId = authProfileId;
    }

    /** Uygulamanın tam adresi: base_url + base_path. Örn: http://erp:8081/erp */
    @JsonProperty(value = "fullBaseUrl", access = JsonProperty.Access.READ_ONLY)
    public String getFullBaseUrl() {
        return fullBaseUrl(baseUrl, basePath);
    }

    /** Swagger adresi: tam adres + swagger_path. has_swagger = 0 ise null. */
    @JsonProperty(value = "swaggerUrl", access = JsonProperty.Access.READ_ONLY)
    public String getSwaggerUrl() {
        if (hasSwagger != YesNoEnum.YES || baseUrl == null) {
            return null;
        }
        String path = swaggerPath == null || swaggerPath.isBlank() || NONE.equals(swaggerPath.trim())
                ? "" : swaggerPath.trim();
        return getFullBaseUrl() + (path.isEmpty() || path.startsWith("/") ? path : "/" + path);
    }

    /** base_url + base_path; sondaki "/" kaldırılır, base_path "-" ise yok sayılır. */
    public static String fullBaseUrl(String baseUrl, String basePath) {
        if (baseUrl == null) {
            return null;
        }
        String base = trimTrailingSlash(baseUrl.trim());
        if (basePath == null || basePath.isBlank() || NONE.equals(basePath.trim())) {
            return base;
        }
        String path = trimTrailingSlash(basePath.trim());
        if (path.isEmpty()) {
            return base;
        }
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /** appPurpose uzun olabileceği için yazılmaz. */
    @Override
    public String toString() {
        return "AppRegistryEntity{"
                + "appCode='" + appCode + '\''
                + ", appName='" + appName + '\''
                + ", appOwner='" + appOwner + '\''
                + ", baseUrl='" + baseUrl + '\''
                + ", basePath='" + basePath + '\''
                + ", hasSwagger=" + hasSwagger
                + ", swaggerPath='" + swaggerPath + '\''
                + ", authProfileId=" + authProfileId
                + '}';
    }
}
