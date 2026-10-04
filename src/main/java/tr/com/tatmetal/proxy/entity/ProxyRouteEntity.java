package tr.com.tatmetal.proxy.entity;

import tr.com.zenergy.mini_jpa.annotation.dao.Column;
import tr.com.zenergy.mini_jpa.annotation.dao.Entity;
import tr.com.zenergy.mini_jpa.entity.BaseEntity;
import tr.com.zenergy.mini_jpa.util.TypeUtil;
import tr.com.tatmetal.proxy.enums.HttpMethodEnum;
import tr.com.tatmetal.proxy.enums.YesNoEnum;

/**
 * Gelen mesajın hangi hedefe iletileceğini tanımlayan yönlendirme kaydı.
 * Gelen isteğin path (+ metot) bilgisi ile eşleşir.
 * Standart alanlar (id, id_serial, etag, status, ...) BaseEntity'den gelir.
 */
@Entity(tableName = "message_proxy.proxy_route")
public class ProxyRouteEntity extends BaseEntity {

    @Column(name = "route_code") private String routeCode;
    @Column(name = "route_description") private String routeDescription;
    @Column(name = "source_path") private String sourcePath;
    /** PASS_THROUGH (0) = gelen tüm metotlar için geçerli. */
    @Column(name = "source_method", isTyped = true, type = TypeUtil.I_ENUM_NUM)
    private HttpMethodEnum sourceMethod;
    @Column(name = "target_url") private String targetUrl;
    @Column(name = "target_method", isTyped = true, type = TypeUtil.I_ENUM_NUM)
    private HttpMethodEnum targetMethod;
    @Column(name = "connect_timeout_ms") private int connectTimeoutMs;
    @Column(name = "read_timeout_ms") private int readTimeoutMs;
    @Column(name = "log_body", isTyped = true, type = TypeUtil.I_ENUM_NUM)
    private YesNoEnum logBody;
    @Column(name = "pass_unmapped_fields", isTyped = true, type = TypeUtil.I_ENUM_NUM)
    private YesNoEnum passUnmappedFields;

    public ProxyRouteEntity() {
    }

    public String getRouteCode() {
        return routeCode;
    }

    public void setRouteCode(String routeCode) {
        this.routeCode = routeCode;
    }

    public String getRouteDescription() {
        return routeDescription;
    }

    public void setRouteDescription(String routeDescription) {
        this.routeDescription = routeDescription;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public HttpMethodEnum getSourceMethod() {
        return sourceMethod;
    }

    public void setSourceMethod(HttpMethodEnum sourceMethod) {
        this.sourceMethod = sourceMethod;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public HttpMethodEnum getTargetMethod() {
        return targetMethod;
    }

    public void setTargetMethod(HttpMethodEnum targetMethod) {
        this.targetMethod = targetMethod;
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

    public YesNoEnum getLogBody() {
        return logBody;
    }

    public void setLogBody(YesNoEnum logBody) {
        this.logBody = logBody;
    }

    public YesNoEnum getPassUnmappedFields() {
        return passUnmappedFields;
    }

    public void setPassUnmappedFields(YesNoEnum passUnmappedFields) {
        this.passUnmappedFields = passUnmappedFields;
    }

    @Override
    public String toString() {
        return "ProxyRouteEntity{"
                + "routeCode='" + routeCode + '\''
                + ", routeDescription='" + routeDescription + '\''
                + ", sourcePath='" + sourcePath + '\''
                + ", sourceMethod=" + sourceMethod
                + ", targetUrl='" + targetUrl + '\''
                + ", targetMethod=" + targetMethod
                + ", connectTimeoutMs=" + connectTimeoutMs
                + ", readTimeoutMs=" + readTimeoutMs
                + ", logBody=" + logBody
                + ", passUnmappedFields=" + passUnmappedFields
                + '}';
    }
}
