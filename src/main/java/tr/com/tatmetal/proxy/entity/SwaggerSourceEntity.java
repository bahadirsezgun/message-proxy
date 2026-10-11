package tr.com.tatmetal.proxy.entity;

import java.sql.Timestamp;
import java.util.UUID;

import tr.com.zenergy.mini_jpa.annotation.dao.Column;
import tr.com.zenergy.mini_jpa.annotation.dao.Entity;
import tr.com.zenergy.mini_jpa.entity.BaseEntity;
import tr.com.zenergy.mini_jpa.util.TypeUtil;
import tr.com.tatmetal.proxy.enums.SyncResultEnum;
import tr.com.tatmetal.proxy.enums.YesNoEnum;

/**
 * Endpoint listesi swagger/OpenAPI dokümanından okunacak servis tanımı.
 * Standart alanlar (id, id_serial, etag, status, ...) BaseEntity'den gelir.
 * last_sync_* ve endpoint_count alanları senkron servisi tarafından yazılır.
 */
@Entity(tableName = "message_proxy.swagger_source")
public class SwaggerSourceEntity extends BaseEntity {

    @Column(name = "source_code") private String sourceCode;
    @Column(name = "source_description") private String sourceDescription;
    @Column(name = "swagger_url") private String swaggerUrl;
    @Column(name = "target_base_url") private String targetBaseUrl;
    @Column(name = "auto_sync", isTyped = true, type = TypeUtil.I_ENUM_NUM)
    private YesNoEnum autoSync;
    @Column(name = "last_sync_date") private Timestamp lastSyncDate;
    @Column(name = "last_sync_result", isTyped = true, type = TypeUtil.I_ENUM_NUM)
    private SyncResultEnum lastSyncResult;
    @Column(name = "last_sync_message") private String lastSyncMessage;
    @Column(name = "endpoint_count") private int endpointCount;
    /** Doküman okunurken kullanılacak token tanımı (auth_profile.id); null ise token gönderilmez. */
    @Column(name = "auth_profile_id", isTyped = true, type = "UUID") private UUID authProfileId;
    /** Kaynağın ait olduğu uygulama; doluysa adres / token alanları uygulamadan yönetilir. */
    @Column(name = "app_registry_id", isTyped = true, type = "UUID") private UUID appRegistryId;

    public SwaggerSourceEntity() {
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getSourceDescription() {
        return sourceDescription;
    }

    public void setSourceDescription(String sourceDescription) {
        this.sourceDescription = sourceDescription;
    }

    public String getSwaggerUrl() {
        return swaggerUrl;
    }

    public void setSwaggerUrl(String swaggerUrl) {
        this.swaggerUrl = swaggerUrl;
    }

    public String getTargetBaseUrl() {
        return targetBaseUrl;
    }

    public void setTargetBaseUrl(String targetBaseUrl) {
        this.targetBaseUrl = targetBaseUrl;
    }

    public YesNoEnum getAutoSync() {
        return autoSync;
    }

    public void setAutoSync(YesNoEnum autoSync) {
        this.autoSync = autoSync;
    }

    public Timestamp getLastSyncDate() {
        return lastSyncDate;
    }

    public void setLastSyncDate(Timestamp lastSyncDate) {
        this.lastSyncDate = lastSyncDate;
    }

    public SyncResultEnum getLastSyncResult() {
        return lastSyncResult;
    }

    public void setLastSyncResult(SyncResultEnum lastSyncResult) {
        this.lastSyncResult = lastSyncResult;
    }

    public String getLastSyncMessage() {
        return lastSyncMessage;
    }

    public void setLastSyncMessage(String lastSyncMessage) {
        this.lastSyncMessage = lastSyncMessage;
    }

    public int getEndpointCount() {
        return endpointCount;
    }

    public void setEndpointCount(int endpointCount) {
        this.endpointCount = endpointCount;
    }

    public UUID getAuthProfileId() {
        return authProfileId;
    }

    public void setAuthProfileId(UUID authProfileId) {
        this.authProfileId = authProfileId;
    }

    public UUID getAppRegistryId() {
        return appRegistryId;
    }

    public void setAppRegistryId(UUID appRegistryId) {
        this.appRegistryId = appRegistryId;
    }

    @Override
    public String toString() {
        return "SwaggerSourceEntity{"
                + "sourceCode='" + sourceCode + '\''
                + ", sourceDescription='" + sourceDescription + '\''
                + ", swaggerUrl='" + swaggerUrl + '\''
                + ", targetBaseUrl='" + targetBaseUrl + '\''
                + ", autoSync=" + autoSync
                + ", lastSyncDate=" + lastSyncDate
                + ", lastSyncResult=" + lastSyncResult
                + ", lastSyncMessage='" + lastSyncMessage + '\''
                + ", endpointCount=" + endpointCount
                + ", authProfileId=" + authProfileId
                + ", appRegistryId=" + appRegistryId
                + '}';
    }
}
