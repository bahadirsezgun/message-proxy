package tr.com.tatmetal.proxy.services;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import feign.FeignException;
import tr.com.tatmetal.proxy.exception.ProxyMappingException;
import tr.com.tatmetal.proxy.model.ProxyFieldMapDefinition;
import tr.com.tatmetal.proxy.model.ProxyRouteDefinition;
import tr.com.tatmetal.proxy.model.ProxyRouteMatch;

/**
 * Proxy akışının orkestrasyonu:
 * <ol>
 *   <li>Gelen path + metot ile route Redis'ten bulunur (yoksa 404).</li>
 *   <li>Hedef URL hazırlanır: {değişken}'ler gelen path'ten doldurulur, query string eklenir.</li>
 *   <li>REQUEST eşleştirmeleri gövdeye uygulanır (JSON ise; hata → 400).</li>
 *   <li>{@link ProxyForwardService#forward} çağrılır.</li>
 *   <li>RESPONSE eşleştirmeleri 2xx JSON cevaba uygulanır (hata → 502).</li>
 *   <li>Cevap (status, header, gövde) isteği gönderen sisteme döner.</li>
 * </ol>
 */
@Service
public class ProxyGatewayService {

    /** Çağırana dönerken taşınmayacak header'lar (gövde değişebileceği için Content-Length dahil). */
    private static final Set<String> EXCLUDED_RESPONSE_HEADERS = Set.of(
            "content-length", "transfer-encoding", "connection", "keep-alive", "te", "trailer", "upgrade",
            "proxy-authenticate");

    private final ProxyRouteCacheService proxyRouteCacheService;
    private final ProxyFieldMappingService proxyFieldMappingService;
    private final ProxyForwardService proxyForwardService;
    private final ProxyMessageLogService proxyMessageLogService;
    private final ObjectMapper objectMapper;
    private final Logger logger = LoggerFactory.getLogger(ProxyGatewayService.class);

    public ProxyGatewayService(ProxyRouteCacheService proxyRouteCacheService,
                               ProxyFieldMappingService proxyFieldMappingService,
                               ProxyForwardService proxyForwardService,
                               ProxyMessageLogService proxyMessageLogService,
                               ObjectMapper objectMapper) {
        this.proxyRouteCacheService = proxyRouteCacheService;
        this.proxyFieldMappingService = proxyFieldMappingService;
        this.proxyForwardService = proxyForwardService;
        this.proxyMessageLogService = proxyMessageLogService;
        this.objectMapper = objectMapper;
    }

    /**
     * @param method      gelen isteğin metodu
     * @param path        gelen isteğin gateway prefix'i çıkarılmış path'i (örn: /erp/order)
     * @param queryString gelen isteğin query string'i (? hariç), yoksa null
     * @param clientIp    isteği gönderen IP
     * @param headers     gelen header'lar
     * @param body        gelen ham gövde
     */
    public ResponseEntity<byte[]> handle(HttpMethod method, String path, String queryString, String clientIp,
                                         Map<String, String> headers, byte[] body) {
        String requestPath = queryString == null ? path : path + "?" + queryString;

        Optional<ProxyRouteMatch> match;
        try {
            match = proxyRouteCacheService.find(method.name(), path);
        } catch (Exception e) {
            logger.error("Route tanımları okunamadı (Redis/DB). path={}, error={}", path, e.toString());
            return error(HttpStatus.SERVICE_UNAVAILABLE, "ROUTE_CACHE_UNAVAILABLE",
                    "Route tanımları okunamadı", path, null);
        }
        if (!match.isPresent()) {
            logger.warn("Tanımlı route bulunamadı. method={}, path={}", method, path);
            return error(HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND",
                    "Bu path için tanımlı route yok: " + method + " " + path, path, null);
        }

        ProxyRouteDefinition route = match.get().getRoute();
        Map<String, String> requestHeaders = new LinkedHashMap<>(headers);
        String correlationId = ensureCorrelationId(requestHeaders);
        URI target;
        try {
            target = buildTargetUri(route.getTargetUrl(), match.get().getPathVariables(), queryString);
        } catch (IllegalArgumentException e) {
            logger.error("Hedef URL oluşturulamadı. route={}, targetUrl={}, error={}",
                    route.getRouteCode(), route.getTargetUrl(), e.toString());
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_TARGET_URL",
                    "Route hedef adresi geçersiz", path, correlationId);
        }

        // 1) İstek gövdesi eşleştirmesi
        byte[] mappedBody;
        try {
            mappedBody = mapBody(body, requestHeaders.get(findHeader(requestHeaders, HttpHeaders.CONTENT_TYPE)),
                    route.getRequestFieldMaps(), route.isPassUnmappedFields());
        } catch (ProxyMappingException | JsonProcessingException e) {
            String message = e instanceof JsonProcessingException
                    ? "İstek gövdesi geçerli JSON değil: " + ((JsonProcessingException) e).getOriginalMessage()
                    : e.getMessage();
            logger.warn("İstek eşleştirmesi yapılamadı. route={}, correlationId={}, error={}",
                    route.getRouteCode(), correlationId, message);
            ResponseEntity<byte[]> response = error(HttpStatus.BAD_REQUEST, "REQUEST_MAPPING_ERROR",
                    message, path, correlationId);
            proxyMessageLogService.recordMappingError(route, target.toString(), correlationId, clientIp,
                    method.name(), requestPath, requestHeaders, body, response.getStatusCodeValue(),
                    response.getBody(), message);
            return response;
        }

        // 2) Hedefe iletim (forward mapping sonrası çağrılır)
        ResponseEntity<byte[]> response;
        try {
            response = proxyForwardService.forward(route, target, method, requestPath, clientIp,
                    requestHeaders, body, mappedBody);
        } catch (FeignException e) {
            boolean timeout = e.getCause() instanceof SocketTimeoutException;
            return error(timeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY,
                    timeout ? "TARGET_TIMEOUT" : "TARGET_UNREACHABLE",
                    "Hedef sisteme ulaşılamadı: " + route.getRouteCode(), path, correlationId);
        }

        // 3) Cevap gövdesi eşleştirmesi (sadece başarılı cevaplarda)
        byte[] responseBody = response.getBody();
        if (response.getStatusCode().is2xxSuccessful() && !route.getResponseFieldMaps().isEmpty()) {
            try {
                responseBody = mapBody(responseBody,
                        response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE),
                        route.getResponseFieldMaps(), route.isPassUnmappedFields());
            } catch (ProxyMappingException | JsonProcessingException e) {
                String message = "Hedef cevabı eşleştirilemedi: " + e.getMessage();
                logger.error("Cevap eşleştirmesi yapılamadı. route={}, correlationId={}, error={}",
                        route.getRouteCode(), correlationId, e.getMessage());
                ResponseEntity<byte[]> errorResponse = error(HttpStatus.BAD_GATEWAY, "RESPONSE_MAPPING_ERROR",
                        message, path, correlationId);
                proxyMessageLogService.recordMappingError(route, target.toString(), correlationId, clientIp,
                        method.name(), requestPath, requestHeaders, body, response.getStatusCodeValue(),
                        response.getBody(), message);
                return errorResponse;
            }
        }

        return ResponseEntity.status(response.getStatusCode())
                .headers(responseHeaders(response.getHeaders(), correlationId))
                .body(responseBody);
    }

    // ------------------------------------------------------------------
    // Yardımcılar
    // ------------------------------------------------------------------

    /** Eşleştirme tanımı varsa ve gövde JSON ise dönüştürür; aksi halde gövdeyi aynen döner. */
    private byte[] mapBody(byte[] body, String contentType, List<ProxyFieldMapDefinition> fieldMaps,
                           boolean passUnmappedFields) throws JsonProcessingException {
        if (fieldMaps == null || fieldMaps.isEmpty() || body == null || body.length == 0
                || !isJson(contentType, body)) {
            return body;
        }
        JsonNode source = readJson(body);
        JsonNode mapped = proxyFieldMappingService.map(source, fieldMaps, passUnmappedFields);
        return objectMapper.writeValueAsBytes(mapped);
    }

    private JsonNode readJson(byte[] body) throws JsonProcessingException {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw e;
        } catch (java.io.IOException e) {
            throw new ProxyMappingException("Gövde okunamadı: " + e.getMessage(), e);
        }
    }

    private boolean isJson(String contentType, byte[] body) {
        if (contentType != null) {
            return contentType.toLowerCase(Locale.ROOT).contains("json");
        }
        for (byte b : body) {
            if (!Character.isWhitespace(b)) {
                return b == '{' || b == '[';
            }
        }
        return false;
    }

    /**
     * target_url içindeki {değişken}'ler gelen path'ten doldurulur, gelen query string hedefe eklenir.
     * Örn: target_url = http://erp/api/orders/{id}, gelen = /erp/order/42?x=1 → http://erp/api/orders/42?x=1
     */
    private URI buildTargetUri(String targetUrl, Map<String, String> pathVariables, String queryString) {
        String url = targetUrl;
        for (Map.Entry<String, String> variable : pathVariables.entrySet()) {
            String encoded = URLEncoder.encode(variable.getValue(), StandardCharsets.UTF_8).replace("+", "%20");
            url = url.replace("{" + variable.getKey() + "}", encoded);
        }
        if (queryString != null && !queryString.isEmpty()) {
            url = url + (url.contains("?") ? "&" : "?") + queryString;
        }
        return URI.create(url);
    }

    private String ensureCorrelationId(Map<String, String> headers) {
        String key = findHeader(headers, ProxyForwardService.CORRELATION_ID_HEADER);
        if (key != null && headers.get(key) != null && !headers.get(key).isBlank()) {
            return headers.get(key);
        }
        String correlationId = UUID.randomUUID().toString();
        headers.put(ProxyForwardService.CORRELATION_ID_HEADER, correlationId);
        return correlationId;
    }

    /** Header adını büyük/küçük harf duyarsız bulur; yoksa null. */
    private String findHeader(Map<String, String> headers, String name) {
        for (String key : headers.keySet()) {
            if (key.equalsIgnoreCase(name)) {
                return key;
            }
        }
        return null;
    }

    private HttpHeaders responseHeaders(HttpHeaders source, String correlationId) {
        HttpHeaders result = new HttpHeaders();
        source.forEach((key, values) -> {
            if (!EXCLUDED_RESPONSE_HEADERS.contains(key.toLowerCase(Locale.ROOT))) {
                result.addAll(key, values);
            }
        });
        if (!result.containsKey(ProxyForwardService.CORRELATION_ID_HEADER)) {
            result.set(ProxyForwardService.CORRELATION_ID_HEADER, correlationId);
        }
        return result;
    }

    private ResponseEntity<byte[]> error(HttpStatus status, String code, String message, String path,
                                         String correlationId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("status", status.value());
        node.put("error", code);
        node.put("message", message);
        node.put("path", path);
        if (correlationId != null) {
            node.put("correlationId", correlationId);
        }
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(node);
        } catch (JsonProcessingException e) {
            bytes = message.getBytes(StandardCharsets.UTF_8);
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON);
        if (correlationId != null) {
            builder.header(ProxyForwardService.CORRELATION_ID_HEADER, correlationId);
        }
        return builder.body(bytes);
    }
}
