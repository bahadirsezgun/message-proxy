package tr.com.tatmetal.proxy.app;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bir uygulamanın başka bir uygulamayla route üzerinden entegrasyonu (genel bakış cevabında).
 */
public class AppIntegration {

    /** Karşı uygulama; route'ta çağıran uygulama tanımlı değilse null. */
    private UUID appRegistryId;
    /** Karşı uygulamanın kodu; tanımsızsa "-". */
    private String appCode;
    private String appName;
    private int routeCount;
    private List<AppRouteLink> routes = new ArrayList<>();

    public AppIntegration() {
    }

    public UUID getAppRegistryId() {
        return appRegistryId;
    }

    public void setAppRegistryId(UUID appRegistryId) {
        this.appRegistryId = appRegistryId;
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

    public int getRouteCount() {
        return routeCount;
    }

    public void setRouteCount(int routeCount) {
        this.routeCount = routeCount;
    }

    public List<AppRouteLink> getRoutes() {
        return routes;
    }

    public void setRoutes(List<AppRouteLink> routes) {
        this.routes = routes;
    }

    @Override
    public String toString() {
        return "AppIntegration{"
                + "appRegistryId=" + appRegistryId
                + ", appCode='" + appCode + '\''
                + ", appName='" + appName + '\''
                + ", routeCount=" + routeCount
                + '}';
    }
}
