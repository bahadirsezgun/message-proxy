package tr.com.tatmetal.proxy.api.v1.resources;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UrlPathHelper;

import tr.com.tatmetal.proxy.services.ProxyGatewayService;

/**
 * Proxy giriş noktası. {proxy.gateway.prefix} altındaki tüm istekler karşılanır;
 * prefix çıkarıldıktan sonra kalan path, proxy_route.source_path ile eşleştirilir.
 * <p>
 * Örn: prefix=/proxy, istek POST /proxy/erp/order → source_path=/erp/order, source_method=POST (veya 0)
 */
@RestController
public class ProxyGatewayResources {

    private final ProxyGatewayService proxyGatewayService;
    private final String gatewayPrefix;
    private final UrlPathHelper urlPathHelper = new UrlPathHelper();

    public ProxyGatewayResources(ProxyGatewayService proxyGatewayService,
                                 @Value("${proxy.gateway.prefix:/proxy}") String gatewayPrefix) {
        this.proxyGatewayService = proxyGatewayService;
        this.gatewayPrefix = gatewayPrefix.endsWith("/")
                ? gatewayPrefix.substring(0, gatewayPrefix.length() - 1) : gatewayPrefix;
    }

    @RequestMapping(value = "${proxy.gateway.prefix:/proxy}/**",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                    RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<byte[]> handle(HttpServletRequest request,
                                         @RequestBody(required = false) byte[] body) {
        String pathWithinApp = urlPathHelper.getPathWithinApplication(request);
        String path = pathWithinApp.startsWith(gatewayPrefix)
                ? pathWithinApp.substring(gatewayPrefix.length()) : pathWithinApp;

        return proxyGatewayService.handle(HttpMethod.resolve(request.getMethod()), path,
                request.getQueryString(), resolveClientIp(request), readHeaders(request), body);
    }

    private Map<String, String> readHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : Collections.list(request.getHeaderNames())) {
            headers.put(name, String.join(", ", Collections.list(request.getHeaders(name))));
        }
        return headers;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
