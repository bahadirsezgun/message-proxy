package tr.com.tatmetal.proxy.entity;

import java.util.UUID;

import tr.com.zenergy.mini_jpa.annotation.dao.Column;
import tr.com.zenergy.mini_jpa.annotation.dao.Entity;
import tr.com.zenergy.mini_jpa.entity.BaseEntity;
import tr.com.zenergy.mini_jpa.util.TypeUtil;
import tr.com.tatmetal.proxy.enums.ProxyResultEnum;

/**
 * İletilen her mesajın istek/cevap kaydı.
 * Standart alanlar (id, id_serial, etag, status, ...) BaseEntity'den gelir.
 */
@Entity(tableName = "message_proxy.proxy_message_log")
public class ProxyMessageLogEntity extends BaseEntity {

    @Column(name = "proxy_route_id", isTyped = true, type = "UUID") private UUID proxyRouteId;
    @Column(name = "route_code") private String routeCode;
    @Column(name = "correlation_id") private String correlationId;
    @Column(name = "client_ip") private String clientIp;
    @Column(name = "request_method") private String requestMethod;
    @Column(name = "request_path") private String requestPath;
    @Column(name = "target_url") private String targetUrl;
    @Column(name = "request_headers") private String requestHeaders;
    @Column(name = "request_body") private String requestBody;
    @Column(name = "response_status") private int responseStatus;
    @Column(name = "response_headers") private String responseHeaders;
    @Column(name = "response_body") private String responseBody;
    @Column(name = "duration_ms") private long durationMs;
    @Column(name = "result_type", isTyped = true, type = TypeUtil.I_ENUM_NUM)
    private ProxyResultEnum resultType;
    @Column(name = "error_message") private String errorMessage;

    public ProxyMessageLogEntity() {
    }

    public UUID getProxyRouteId() {
        return proxyRouteId;
    }

    public void setProxyRouteId(UUID proxyRouteId) {
        this.proxyRouteId = proxyRouteId;
    }

    public String getRouteCode() {
        return routeCode;
    }

    public void setRouteCode(String routeCode) {
        this.routeCode = routeCode;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public String getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(String requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(int responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(String responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public ProxyResultEnum getResultType() {
        return resultType;
    }

    public void setResultType(ProxyResultEnum resultType) {
        this.resultType = resultType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /** Gövde ve header alanları bilerek dışarıda bırakılır (büyük/hassas veri log'a düşmesin). */
    @Override
    public String toString() {
        return "ProxyMessageLogEntity{"
                + "proxyRouteId=" + proxyRouteId
                + ", routeCode='" + routeCode + '\''
                + ", correlationId='" + correlationId + '\''
                + ", clientIp='" + clientIp + '\''
                + ", requestMethod='" + requestMethod + '\''
                + ", requestPath='" + requestPath + '\''
                + ", targetUrl='" + targetUrl + '\''
                + ", responseStatus=" + responseStatus
                + ", durationMs=" + durationMs
                + ", resultType=" + resultType
                + ", errorMessage='" + errorMessage + '\''
                + '}';
    }
}
