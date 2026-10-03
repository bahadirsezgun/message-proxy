package tr.com.tatmetal.proxy.client;

import java.net.URI;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import feign.Request;
import tr.com.tatmetal.proxy.config.ProxyFeignConfig;

/**
 * Mesajları dinamik hedeflere ileten genel Feign client.
 * <ul>
 *   <li>{@link URI} parametresi, client'ın varsayılan url'ini çağrı bazında ezer (hedef = proxy_route.target_url).</li>
 *   <li>{@link Request.Options} parametresi, timeout'ları çağrı bazında belirler (route bazlı timeout).</li>
 * </ul>
 * Gövde ham byte[] olarak taşınır; içerik tipi ne olursa olsun (JSON, XML, ...) dönüştürülmeden iletilir.
 */
@FeignClient(name = "proxyForwardClient",
        url = "${proxy.forward.default-url:http://localhost}",
        configuration = ProxyFeignConfig.class)
public interface ProxyForwardClient {

    @RequestMapping(method = RequestMethod.GET)
    ResponseEntity<byte[]> get(URI target, Request.Options options,
                               @RequestHeader Map<String, String> headers);

    @RequestMapping(method = RequestMethod.POST)
    ResponseEntity<byte[]> post(URI target, Request.Options options,
                                @RequestHeader Map<String, String> headers,
                                @RequestBody(required = false) byte[] body);

    @RequestMapping(method = RequestMethod.PUT)
    ResponseEntity<byte[]> put(URI target, Request.Options options,
                               @RequestHeader Map<String, String> headers,
                               @RequestBody(required = false) byte[] body);

    @RequestMapping(method = RequestMethod.PATCH)
    ResponseEntity<byte[]> patch(URI target, Request.Options options,
                                 @RequestHeader Map<String, String> headers,
                                 @RequestBody(required = false) byte[] body);

    @RequestMapping(method = RequestMethod.DELETE)
    ResponseEntity<byte[]> delete(URI target, Request.Options options,
                                  @RequestHeader Map<String, String> headers,
                                  @RequestBody(required = false) byte[] body);
}
