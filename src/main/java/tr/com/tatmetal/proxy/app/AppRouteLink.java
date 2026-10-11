package tr.com.tatmetal.proxy.app;

import java.util.UUID;

/**
 * Bir uygulama ile ilgili tek route özeti (genel bakış cevabında).
 */
public class AppRouteLink {

    private UUID proxyRouteId;
    private String routeCode;
    /** Gelen istek metodu; tüm metotlar için ANY. */
    private String sourceMethod;
    /** Proxy giriş path'i (/proxy ön eki olmadan). */
    private String sourcePath;
    /** proxy_route.target_url (göreli olabilir). */
    private String targetUrl;
    /** Gerçek hedef adres; çözülemiyorsa null. */
    private String resolvedTargetUrl;
    /** ACTIVE / PASSIVE / CLOSED. */
    private String status;

    public AppRouteLink() {
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

    public String getSourceMethod() {
        return sourceMethod;
    }

    public void setSourceMethod(String sourceMethod) {
        this.sourceMethod = sourceMethod;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public String getResolvedTargetUrl() {
        return resolvedTargetUrl;
    }

    public void setResolvedTargetUrl(String resolvedTargetUrl) {
        this.resolvedTargetUrl = resolvedTargetUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "AppRouteLink{"
                + "proxyRouteId=" + proxyRouteId
                + ", routeCode='" + routeCode + '\''
                + ", sourceMethod='" + sourceMethod + '\''
                + ", sourcePath='" + sourcePath + '\''
                + ", targetUrl='" + targetUrl + '\''
                + ", resolvedTargetUrl='" + resolvedTargetUrl + '\''
                + ", status='" + status + '\''
                + '}';
    }
}
