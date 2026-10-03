package tr.com.tatmetal.proxy.api.v1.resources;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tr.com.tatmetal.proxy.entity.ProxyMessageLogEntity;
import tr.com.tatmetal.proxy.services.ProxyMessageLogService;

/**
 * Mesaj logları sadece okunur; kayıtlar ProxyForwardService tarafından yazılır.
 */
@RestController
@RequestMapping("/api/v1/proxy-message-log")
public class ProxyMessageLogResources {

    private final ProxyMessageLogService proxyMessageLogService;

    public ProxyMessageLogResources(ProxyMessageLogService proxyMessageLogService) {
        this.proxyMessageLogService = proxyMessageLogService;
    }

    @GetMapping("/{id}/{etag}")
    public ResponseEntity<ProxyMessageLogEntity> getOne(@PathVariable UUID id, @PathVariable UUID etag) {
        ProxyMessageLogEntity entity = proxyMessageLogService.getOne(id, etag);
        return entity == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(entity);
    }

    /** Örn: {"routeCode": "ERP_ORDER"} veya {"correlationId": "..."} */
    @PostMapping("/search")
    public ResponseEntity<List<ProxyMessageLogEntity>> getByCondition(@RequestBody(required = false) Map<String, Object> params) {
        return ResponseEntity.ok(proxyMessageLogService.getByCondition(params));
    }
}
