package tr.com.tatmetal.proxy.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Redis'te tutulan route tanımı: proxy_route satırı + ona bağlı istek/cevap alan eşleştirmeleri.
 * Metot alanları HTTP metot adı olarak tutulur ("ANY", "GET", "POST", ...; hedef için "PASS_THROUGH").
 */
public class ProxyRouteDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String ANY_METHOD = "ANY";

    private UUID id;
    private String routeCode;
    private String sourcePath;
    private String sourceMethod;
    private String targetUrl;
    private String targetMethod;
    private int connectTimeoutMs;
    private int readTimeoutMs;
    private boolean logBody;
    private boolean passUnmappedFields;
    private List<ProxyFieldMapDefinition> requestFieldMaps = new ArrayList<>();
    private List<ProxyFieldMapDefinition> responseFieldMaps = new ArrayList<>();

    public ProxyRouteDefinition() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRouteCode() {
        return routeCode;
    }

    public void setRouteCode(String routeCode) {
        this.routeCode = routeCode;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getSourceMethod() {
        return sourceMethod;
    }

    public void setSourceMethod(String sourceMethod) {
        this.sourceMethod = sourceMethod;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public String getTargetMethod() {
        return targetMethod;
    }

    public void setTargetMethod(String targetMethod) {
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

    public boolean isLogBody() {
        return logBody;
    }

    public void setLogBody(boolean logBody) {
        this.logBody = logBody;
    }

    public boolean isPassUnmappedFields() {
        return passUnmappedFields;
    }

    public void setPassUnmappedFields(boolean passUnmappedFields) {
        this.passUnmappedFields = passUnmappedFields;
    }

    public List<ProxyFieldMapDefinition> getRequestFieldMaps() {
        return requestFieldMaps;
    }

    public void setRequestFieldMaps(List<ProxyFieldMapDefinition> requestFieldMaps) {
        this.requestFieldMaps = requestFieldMaps == null ? new ArrayList<>() : requestFieldMaps;
    }

    public List<ProxyFieldMapDefinition> getResponseFieldMaps() {
        return responseFieldMaps;
    }

    public void setResponseFieldMaps(List<ProxyFieldMapDefinition> responseFieldMaps) {
        this.responseFieldMaps = responseFieldMaps == null ? new ArrayList<>() : responseFieldMaps;
    }

    @Override
    public String toString() {
        return "ProxyRouteDefinition{"
                + "id=" + id
                + ", routeCode='" + routeCode + '\''
                + ", sourcePath='" + sourcePath + '\''
                + ", sourceMethod='" + sourceMethod + '\''
                + ", targetUrl='" + targetUrl + '\''
                + ", targetMethod='" + targetMethod + '\''
                + ", connectTimeoutMs=" + connectTimeoutMs
                + ", readTimeoutMs=" + readTimeoutMs
                + ", logBody=" + logBody
                + ", passUnmappedFields=" + passUnmappedFields
                + ", requestFieldMaps=" + requestFieldMaps.size()
                + ", responseFieldMaps=" + responseFieldMaps.size()
                + '}';
    }
}
