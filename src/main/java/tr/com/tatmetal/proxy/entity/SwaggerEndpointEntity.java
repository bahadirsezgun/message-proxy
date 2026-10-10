package tr.com.tatmetal.proxy.entity;

import java.sql.Timestamp;
import java.util.UUID;

import tr.com.zenergy.mini_jpa.annotation.dao.Column;
import tr.com.zenergy.mini_jpa.annotation.dao.Entity;
import tr.com.zenergy.mini_jpa.entity.BaseEntity;
import tr.com.zenergy.mini_jpa.util.TypeUtil;
import tr.com.tatmetal.proxy.enums.YesNoEnum;

/**
 * Swagger/OpenAPI dokümanından okunan endpoint. Kayıtlar SwaggerSyncService tarafından yazılır.
 * Standart alanlar (id, id_serial, etag, status, ...) BaseEntity'den gelir.
 */
@Entity(tableName = "message_proxy.swagger_endpoint")
public class SwaggerEndpointEntity extends BaseEntity {

    @Column(name = "swagger_source_id", isTyped = true, type = "UUID") private UUID swaggerSourceId;
    @Column(name = "api_group") private String apiGroup;
    @Column(name = "http_method") private String httpMethod;
    @Column(name = "path") private String path;
    @Column(name = "full_url") private String fullUrl;
    @Column(name = "operation_id") private String operationId;
    @Column(name = "summary") private String summary;
    @Column(name = "description") private String description;
    @Column(name = "tags") private String tags;
    @Column(name = "deprecated", isTyped = true, type = TypeUtil.I_ENUM_NUM)
    private YesNoEnum deprecated;
    @Column(name = "request_content_type") private String requestContentType;
    @Column(name = "parameters_json") private String parametersJson;
    @Column(name = "request_body_json") private String requestBodyJson;
    @Column(name = "responses_json") private String responsesJson;
    @Column(name = "last_seen_date") private Timestamp lastSeenDate;

    public SwaggerEndpointEntity() {
    }

    public UUID getSwaggerSourceId() {
        return swaggerSourceId;
    }

    public void setSwaggerSourceId(UUID swaggerSourceId) {
        this.swaggerSourceId = swaggerSourceId;
    }

    public String getApiGroup() {
        return apiGroup;
    }

    public void setApiGroup(String apiGroup) {
        this.apiGroup = apiGroup;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getFullUrl() {
        return fullUrl;
    }

    public void setFullUrl(String fullUrl) {
        this.fullUrl = fullUrl;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public YesNoEnum getDeprecated() {
        return deprecated;
    }

    public void setDeprecated(YesNoEnum deprecated) {
        this.deprecated = deprecated;
    }

    public String getRequestContentType() {
        return requestContentType;
    }

    public void setRequestContentType(String requestContentType) {
        this.requestContentType = requestContentType;
    }

    public String getParametersJson() {
        return parametersJson;
    }

    public void setParametersJson(String parametersJson) {
        this.parametersJson = parametersJson;
    }

    public String getRequestBodyJson() {
        return requestBodyJson;
    }

    public void setRequestBodyJson(String requestBodyJson) {
        this.requestBodyJson = requestBodyJson;
    }

    public String getResponsesJson() {
        return responsesJson;
    }

    public void setResponsesJson(String responsesJson) {
        this.responsesJson = responsesJson;
    }

    public Timestamp getLastSeenDate() {
        return lastSeenDate;
    }

    public void setLastSeenDate(Timestamp lastSeenDate) {
        this.lastSeenDate = lastSeenDate;
    }

    /** JSON alanları bilerek dışarıda bırakılır (log'u şişirmesin). */
    @Override
    public String toString() {
        return "SwaggerEndpointEntity{"
                + "swaggerSourceId=" + swaggerSourceId
                + ", apiGroup='" + apiGroup + '\''
                + ", httpMethod='" + httpMethod + '\''
                + ", path='" + path + '\''
                + ", fullUrl='" + fullUrl + '\''
                + ", operationId='" + operationId + '\''
                + ", summary='" + summary + '\''
                + ", tags='" + tags + '\''
                + ", deprecated=" + deprecated
                + ", requestContentType='" + requestContentType + '\''
                + ", lastSeenDate=" + lastSeenDate
                + '}';
    }
}
