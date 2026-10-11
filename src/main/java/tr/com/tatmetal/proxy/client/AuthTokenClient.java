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
 * auth_profile tanımlarına göre token isteği atan genel Feign client.
 * {@link URI} parametresi hedef adresi (auth_profile.token_url), {@link Request.Options} timeout'ları
 * çağrı bazında belirler. Gövde hazır byte[] olarak gönderilir (JSON veya form). Retry kapalıdır.
 */
@FeignClient(name = "authTokenClient",
        url = "${proxy.forward.default-url:http://localhost}",
        configuration = ProxyFeignConfig.class)
public interface AuthTokenClient {

    @RequestMapping(method = RequestMethod.POST)
    ResponseEntity<byte[]> post(URI target, Request.Options options,
                                @RequestHeader Map<String, String> headers,
                                @RequestBody byte[] body);

    @RequestMapping(method = RequestMethod.PUT)
    ResponseEntity<byte[]> put(URI target, Request.Options options,
                               @RequestHeader Map<String, String> headers,
                               @RequestBody byte[] body);
}
