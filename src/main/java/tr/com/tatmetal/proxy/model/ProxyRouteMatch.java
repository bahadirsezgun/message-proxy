package tr.com.tatmetal.proxy.model;

import java.util.Collections;
import java.util.Map;

/**
 * Gelen isteğin path/metot bilgisine göre bulunan route ve path'ten çıkarılan değişkenler.
 * Örn: source_path = /erp/order/{id}, gelen path = /erp/order/42 → pathVariables = {id=42}
 */
public class ProxyRouteMatch {

    private final ProxyRouteDefinition route;
    private final Map<String, String> pathVariables;

    public ProxyRouteMatch(ProxyRouteDefinition route, Map<String, String> pathVariables) {
        this.route = route;
        this.pathVariables = pathVariables == null ? Collections.emptyMap() : pathVariables;
    }

    public ProxyRouteDefinition getRoute() {
        return route;
    }

    public Map<String, String> getPathVariables() {
        return pathVariables;
    }

    @Override
    public String toString() {
        return "ProxyRouteMatch{route=" + route + ", pathVariables=" + pathVariables + '}';
    }
}
