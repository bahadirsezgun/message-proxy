package tr.com.tatmetal.proxy.client;

import java.net.URI;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import feign.Request;
import tr.com.tatmetal.proxy.config.ProxyFeignConfig;

/**
 * Swagger/OpenAPI dokümanlarını (ve springdoc swagger-config'i) okumak için genel Feign client.
 * {@link URI} parametresi hedef adresi, {@link Request.Options} timeout'ları çağrı bazında belirler.
 * Retry kapalıdır (ProxyFeignConfig).
 */
@FeignClient(name = "swaggerDocClient",
        url = "${proxy.forward.default-url:http://localhost}",
        configuration = ProxyFeignConfig.class)
public interface SwaggerDocClient {

    @RequestMapping(method = RequestMethod.GET)
    ResponseEntity<byte[]> get(URI target, Request.Options options,
                               @RequestHeader Map<String, String> headers);
}
