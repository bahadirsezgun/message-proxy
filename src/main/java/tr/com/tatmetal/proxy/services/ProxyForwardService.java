package tr.com.tatmetal.proxy.services;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import feign.FeignException;
import feign.Request;
import tr.com.tatmetal.proxy.client.ProxyForwardClient;
import tr.com.tatmetal.proxy.entity.ProxyRouteEntity;
import tr.com.tatmetal.proxy.enums.HttpMethodEnum;

/**
 * Tablo bağımsız servis: bir mesajı verilen route tanımına göre hedefe iletir ve
 * her iletimi proxy_message_log tablosuna kaydeder.
 * Hedefin 4xx/5xx cevapları exception'a dönüştürülmez; status, header ve gövde aynen çağırana döner.
 */
@Service
public class ProxyForwardService {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    /** Hedefe aktarılmayacak hop-by-hop / bağlantıya özel header'lar. */
    private static final Set<String> EXCLUDED_HEADERS = Set.of(
            "host", "content-length", "connection", "keep-alive", "transfer-encoding",
            "te", "trailer", "upgrade", "proxy-authorization", "proxy-authenticate");

    private final ProxyForwardClient proxyForwardClient;
    private final ProxyMessageLogService proxyMessageLogService;
    private final Logger logger = LoggerFactory.getLogger(ProxyForwardService.class);

    public ProxyForwardService(ProxyForwardClient proxyForwardClient,
                               ProxyMessageLogService proxyMessageLogService) {
        this.proxyForwardClient = proxyForwardClient;
        this.proxyMessageLogService = proxyMessageLogService;
    }

    /**
     * @param route           kullanılacak route tanımı
     * @param incomingMethod  gelen isteğin HTTP metodu
     * @param requestPath     gelen isteğin path + query bilgisi (log için)
     * @param clientIp        isteği gönderen IP (log için)
     * @param incomingHeaders gelen isteğin header'ları
     * @param body            gelen isteğin ham gövdesi
     */
    public ResponseEntity<byte[]> forward(ProxyRouteEntity route, HttpMethod incomingMethod,
                                          String requestPath, String clientIp,
                                          Map<String, String> incomingHeaders, byte[] body) {
        URI target = URI.create(route.getTargetUrl());
        HttpMethod method = resolveMethod(route.getTargetMethod(), incomingMethod);
        Map<String, String> headers = filterHeaders(incomingHeaders);
        String correlationId = resolveCorrelationId(headers);
        Request.Options options = new Request.Options(
                route.getConnectTimeoutMs(), TimeUnit.MILLISECONDS,
                route.getReadTimeoutMs(), TimeUnit.MILLISECONDS,
                true);

        long start = System.nanoTime();
        try {
            ResponseEntity<byte[]> response = execute(method, target, options, headers, body);
            proxyMessageLogService.record(route, correlationId, clientIp, method.name(), requestPath,
                    headers, body, response.getStatusCode().value(), toHeaderMap(response.getHeaders()),
                    response.getBody(), elapsedMs(start), null);
            return response;
        } catch (FeignException e) {
            long duration = elapsedMs(start);
            if (e.status() <= 0) {
                // Bağlantı hatası / timeout: hedefe ulaşılamadı
                logger.error("Hedefe ulaşılamadı. route={}, target={}, correlationId={}, error={}",
                        route.getRouteCode(), target, correlationId, e.toString());
                proxyMessageLogService.record(route, correlationId, clientIp, method.name(), requestPath,
                        headers, body, 0, null, null, duration, e.getMessage());
                throw e;
            }
            logger.warn("Hedef hata döndü. route={}, target={}, correlationId={}, status={}",
                    route.getRouteCode(), target, correlationId, e.status());
            ResponseEntity<byte[]> response = toResponseEntity(e);
            proxyMessageLogService.record(route, correlationId, clientIp, method.name(), requestPath,
                    headers, body, e.status(), e.responseHeaders(), response.getBody(), duration,
                    e.getMessage());
            return response;
        }
    }

    private ResponseEntity<byte[]> execute(HttpMethod method, URI target, Request.Options options,
                                           Map<String, String> headers, byte[] body) {
        if (HttpMethod.GET.equals(method)) {
            return proxyForwardClient.get(target, options, headers);
        } else if (HttpMethod.POST.equals(method)) {
            return proxyForwardClient.post(target, options, headers, body);
        } else if (HttpMethod.PUT.equals(method)) {
            return proxyForwardClient.put(target, options, headers, body);
        } else if (HttpMethod.PATCH.equals(method)) {
            return proxyForwardClient.patch(target, options, headers, body);
        } else if (HttpMethod.DELETE.equals(method)) {
            return proxyForwardClient.delete(target, options, headers, body);
        }
        throw new IllegalArgumentException("Desteklenmeyen HTTP metodu: " + method);
    }

    private HttpMethod resolveMethod(HttpMethodEnum routeMethod, HttpMethod incomingMethod) {
        if (routeMethod == null || routeMethod == HttpMethodEnum.PASS_THROUGH) {
            return incomingMethod;
        }
        return HttpMethod.valueOf(routeMethod.name());
    }

    /** Gelen istekte X-Correlation-Id varsa kullanılır, yoksa üretilip hedefe de eklenir. */
    private String resolveCorrelationId(Map<String, String> headers) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (CORRELATION_ID_HEADER.equalsIgnoreCase(entry.getKey())
                    && entry.getValue() != null && !entry.getValue().isBlank()) {
                return entry.getValue();
            }
        }
        String correlationId = UUID.randomUUID().toString();
        headers.put(CORRELATION_ID_HEADER, correlationId);
        return correlationId;
    }

    private Map<String, String> filterHeaders(Map<String, String> incomingHeaders) {
        Map<String, String> result = new LinkedHashMap<>();
        if (incomingHeaders != null) {
            incomingHeaders.forEach((key, value) -> {
                if (!EXCLUDED_HEADERS.contains(key.toLowerCase(Locale.ROOT))) {
                    result.put(key, value);
                }
            });
        }
        return result;
    }

    private Map<String, List<String>> toHeaderMap(HttpHeaders headers) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        headers.forEach(result::put);
        return result;
    }

    private long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private ResponseEntity<byte[]> toResponseEntity(FeignException e) {
        HttpHeaders headers = new HttpHeaders();
        Map<String, Collection<String>> responseHeaders = e.responseHeaders();
        if (responseHeaders != null) {
            responseHeaders.forEach((key, values) -> {
                if (!EXCLUDED_HEADERS.contains(key.toLowerCase(Locale.ROOT))) {
                    values.forEach(value -> headers.add(key, value));
                }
            });
        }
        byte[] body = e.responseBody().map(buffer -> {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        }).orElse(new byte[0]);
        return ResponseEntity.status(e.status()).headers(headers).body(body);
    }
}
