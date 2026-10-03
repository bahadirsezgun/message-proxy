package tr.com.tatmetal.proxy.services;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tr.com.tatmetal.proxy.dao.ProxyMessageLogDaoImpl;
import tr.com.tatmetal.proxy.entity.ProxyMessageLogEntity;
import tr.com.tatmetal.proxy.entity.ProxyRouteEntity;
import tr.com.tatmetal.proxy.enums.ProxyResultEnum;
import tr.com.tatmetal.proxy.enums.YesNoEnum;

@Service
public class ProxyMessageLogService {

    /** Değeri log'a yazılmayacak header'lar. */
    private static final Set<String> MASKED_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key");
    private static final String MASK = "******";

    private final ProxyMessageLogDaoImpl proxyMessageLogDao;
    private final int maxBodyLength;
    private final int maxErrorLength = 2000;
    private final Logger logger = LoggerFactory.getLogger(ProxyMessageLogService.class);

    public ProxyMessageLogService(ProxyMessageLogDaoImpl proxyMessageLogDao,
                                  @Value("${proxy.log.max-body-length:100000}") int maxBodyLength) {
        this.proxyMessageLogDao = proxyMessageLogDao;
        this.maxBodyLength = maxBodyLength;
    }

    // ------------------------------------------------------------------
    // Standart işlemler
    // ------------------------------------------------------------------

    public ProxyMessageLogEntity save(ProxyMessageLogEntity entity) {
        return proxyMessageLogDao.save(entity);
    }

    public ProxyMessageLogEntity getOne(UUID id, UUID etag) {
        return proxyMessageLogDao.getOne(id, etag);
    }

    public List<ProxyMessageLogEntity> getByCondition(Map<String, Object> params) {
        return proxyMessageLogDao.getByCondition(params == null ? new HashMap<>() : params);
    }

    // ------------------------------------------------------------------
    // İletim kaydı
    // ------------------------------------------------------------------

    /**
     * Bir iletimin kaydını yazar. Log yazımındaki hata asla iletimi bozmamalıdır;
     * bu yüzden tüm hatalar yakalanır ve sadece uygulama loguna düşülür.
     */
    public void record(ProxyRouteEntity route, String correlationId, String clientIp,
                       String requestMethod, String requestPath,
                       Map<String, String> requestHeaders, byte[] requestBody,
                       int responseStatus, Map<String, ? extends Collection<String>> responseHeaders,
                       byte[] responseBody, long durationMs, String errorMessage) {
        try {
            boolean logBody = route.getLogBody() == null || route.getLogBody() == YesNoEnum.YES;

            ProxyMessageLogEntity log = new ProxyMessageLogEntity();
            log.setProxyRouteId(route.getId());
            log.setRouteCode(route.getRouteCode());
            log.setCorrelationId(correlationId);
            log.setClientIp(defaultIfBlank(clientIp));
            log.setRequestMethod(requestMethod);
            log.setRequestPath(truncate(defaultIfBlank(requestPath), 1000));
            log.setTargetUrl(truncate(route.getTargetUrl(), 1000));
            log.setRequestHeaders(singleValueHeadersToText(requestHeaders));
            log.setRequestBody(logBody ? bodyToText(requestBody) : null);
            log.setResponseStatus(responseStatus);
            log.setResponseHeaders(multiValueHeadersToText(responseHeaders));
            log.setResponseBody(logBody ? bodyToText(responseBody) : null);
            log.setDurationMs(durationMs);
            log.setResultType(ProxyResultEnum.fromHttpStatus(responseStatus));
            log.setErrorMessage(truncate(defaultIfBlank(errorMessage), maxErrorLength));

            proxyMessageLogDao.save(log);
        } catch (Exception e) {
            logger.error("Mesaj logu yazılamadı. route={}, correlationId={}, error={}",
                    route.getRouteCode(), correlationId, e.toString());
        }
    }

    private String bodyToText(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        return truncate(new String(body, StandardCharsets.UTF_8), maxBodyLength);
    }

    private String singleValueHeadersToText(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        headers.forEach((key, value) -> sb.append(key).append(": ").append(maskIfNeeded(key, value)).append('\n'));
        return sb.toString();
    }

    private String multiValueHeadersToText(Map<String, ? extends Collection<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        headers.forEach((key, values) -> {
            for (String value : values) {
                sb.append(key).append(": ").append(maskIfNeeded(key, value)).append('\n');
            }
        });
        return sb.toString();
    }

    private String maskIfNeeded(String key, String value) {
        return key != null && MASKED_HEADERS.contains(key.toLowerCase(Locale.ROOT)) ? MASK : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String defaultIfBlank(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
