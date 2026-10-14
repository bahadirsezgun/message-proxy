package tr.com.tatmetal.proxy.app;

import java.util.ArrayList;
import java.util.List;
import tr.com.tatmetal.proxy.entity.AppRegistryEntity;
import tr.com.tatmetal.proxy.entity.SwaggerSourceEntity;

/**
 * Uygulamanın genel bakışı: tanım, swagger durumu, hangi uygulamaları çağırdığı ve kimlerin onu çağırdığı.
 */
public class AppOverview {

    private AppRegistryEntity app;
    /** Uygulamanın swagger kaynağı ve son senkronizasyon durumu; yoksa null. */
    private SwaggerSourceEntity swaggerSource;
    /** Bu uygulamanın proxy üzerinden çağırdığı uygulamalar (route.source_app = bu uygulama). */
    private List<AppIntegration> outgoing = new ArrayList<>();
    /** Bu uygulamayı proxy üzerinden çağıranlar (route.target_app = bu uygulama). */
    private List<AppIntegration> incoming = new ArrayList<>();

    public AppOverview() {
    }

    public AppRegistryEntity getApp() {
        return app;
    }

    public void setApp(AppRegistryEntity app) {
        this.app = app;
    }

    public SwaggerSourceEntity getSwaggerSource() {
        return swaggerSource;
    }

    public void setSwaggerSource(SwaggerSourceEntity swaggerSource) {
        this.swaggerSource = swaggerSource;
    }

    public List<AppIntegration> getOutgoing() {
        return outgoing;
    }

    public void setOutgoing(List<AppIntegration> outgoing) {
        this.outgoing = outgoing;
    }

    public List<AppIntegration> getIncoming() {
        return incoming;
    }

    public void setIncoming(List<AppIntegration> incoming) {
        this.incoming = incoming;
    }

    @Override
    public String toString() {
        return "AppOverview{"
                + "app=" + app
                + ", outgoing=" + outgoing
                + ", incoming=" + incoming
                + '}';
    }
}
