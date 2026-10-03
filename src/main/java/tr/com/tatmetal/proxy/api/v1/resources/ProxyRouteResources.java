package tr.com.tatmetal.proxy.api.v1.resources;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tr.com.tatmetal.proxy.entity.ProxyRouteEntity;
import tr.com.tatmetal.proxy.services.ProxyRouteService;

@RestController
@RequestMapping("/api/v1/proxy-route")
public class ProxyRouteResources {

    private final ProxyRouteService proxyRouteService;

    public ProxyRouteResources(ProxyRouteService proxyRouteService) {
        this.proxyRouteService = proxyRouteService;
    }

    @GetMapping("/{id}/{etag}")
    public ResponseEntity<ProxyRouteEntity> getOne(@PathVariable UUID id, @PathVariable UUID etag) {
        ProxyRouteEntity entity = proxyRouteService.getOne(id, etag);
        return entity == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(entity);
    }

    @PostMapping("/search")
    public ResponseEntity<List<ProxyRouteEntity>> getByCondition(@RequestBody(required = false) Map<String, Object> params) {
        return ResponseEntity.ok(proxyRouteService.getByCondition(params));
    }

    @PostMapping
    public ResponseEntity<ProxyRouteEntity> save(@RequestBody ProxyRouteEntity entity) {
        return ResponseEntity.ok(proxyRouteService.save(entity));
    }

    @PostMapping("/all")
    public ResponseEntity<List<ProxyRouteEntity>> saveAll(@RequestBody List<ProxyRouteEntity> entities) {
        return ResponseEntity.ok(proxyRouteService.saveAll(entities));
    }

    @PutMapping
    public ResponseEntity<ProxyRouteEntity> update(@RequestBody ProxyRouteEntity entity) {
        return ResponseEntity.ok(proxyRouteService.update(entity));
    }

    @DeleteMapping
    public ResponseEntity<ProxyRouteEntity> delete(@RequestBody ProxyRouteEntity entity) {
        return ResponseEntity.ok(proxyRouteService.delete(entity));
    }
}
