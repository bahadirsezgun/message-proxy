package tr.com.tatmetal.proxy.api.v1.resources;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tr.com.tatmetal.proxy.entity.ProxyFieldMapEntity;
import tr.com.tatmetal.proxy.exception.ProxyMappingException;
import tr.com.tatmetal.proxy.services.ProxyFieldMapService;

@RestController
@RequestMapping("/api/v1/proxy-field-map")
public class ProxyFieldMapResources {

    private final ProxyFieldMapService proxyFieldMapService;

    public ProxyFieldMapResources(ProxyFieldMapService proxyFieldMapService) {
        this.proxyFieldMapService = proxyFieldMapService;
    }

    @GetMapping("/{id}/{etag}")
    public ResponseEntity<ProxyFieldMapEntity> getOne(@PathVariable UUID id, @PathVariable UUID etag) {
        ProxyFieldMapEntity entity = proxyFieldMapService.getOne(id, etag);
        return entity == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(entity);
    }

    /** Örn: {"proxyRouteId": "..."} */
    @PostMapping("/search")
    public ResponseEntity<List<ProxyFieldMapEntity>> getByCondition(@RequestBody(required = false) Map<String, Object> params) {
        return ResponseEntity.ok(proxyFieldMapService.getByCondition(params));
    }

    @PostMapping
    public ResponseEntity<ProxyFieldMapEntity> save(@RequestBody ProxyFieldMapEntity entity) {
        return ResponseEntity.ok(proxyFieldMapService.save(entity));
    }

    @PostMapping("/all")
    public ResponseEntity<List<ProxyFieldMapEntity>> saveAll(@RequestBody List<ProxyFieldMapEntity> entities) {
        return ResponseEntity.ok(proxyFieldMapService.saveAll(entities));
    }

    @PutMapping
    public ResponseEntity<ProxyFieldMapEntity> update(@RequestBody ProxyFieldMapEntity entity) {
        return ResponseEntity.ok(proxyFieldMapService.update(entity));
    }

    @DeleteMapping
    public ResponseEntity<ProxyFieldMapEntity> delete(@RequestBody ProxyFieldMapEntity entity) {
        return ResponseEntity.ok(proxyFieldMapService.delete(entity));
    }

    @ExceptionHandler({ProxyMappingException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> handleValidation(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "VALIDATION_ERROR", "message", e.getMessage()));
    }
}
