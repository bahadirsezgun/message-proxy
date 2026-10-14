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

import tr.com.tatmetal.proxy.entity.SwaggerSourceEntity;
import tr.com.tatmetal.proxy.services.SwaggerSourceService;
import tr.com.tatmetal.proxy.services.SwaggerSyncService;
import tr.com.tatmetal.proxy.swagger.SwaggerSyncResult;

@RestController
@RequestMapping("/api/v1/swagger-source")
public class SwaggerSourceResources {

    private final SwaggerSourceService swaggerSourceService;
    private final SwaggerSyncService swaggerSyncService;

    public SwaggerSourceResources(SwaggerSourceService swaggerSourceService,
                                  SwaggerSyncService swaggerSyncService) {
        this.swaggerSourceService = swaggerSourceService;
        this.swaggerSyncService = swaggerSyncService;
    }

    @GetMapping("/{id}/{etag}")
    public ResponseEntity<SwaggerSourceEntity> getOne(@PathVariable UUID id, @PathVariable UUID etag) {
        SwaggerSourceEntity entity = swaggerSourceService.getOne(id, etag);
        return entity == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(entity);
    }

    @PostMapping("/search")
    public ResponseEntity<List<SwaggerSourceEntity>> getByCondition(@RequestBody(required = false) Map<String, Object> params) {
        return ResponseEntity.ok(swaggerSourceService.getByCondition(params));
    }

    /** Kaydeder; autoSync = 1 (varsayılan) ise endpoint'ler hemen okunur ve sonuç kayıtta döner. */
    @PostMapping
    public ResponseEntity<SwaggerSourceEntity> save(@RequestBody SwaggerSourceEntity entity) {
        return ResponseEntity.ok(swaggerSourceService.save(entity));
    }

    @PostMapping("/all")
    public ResponseEntity<List<SwaggerSourceEntity>> saveAll(@RequestBody List<SwaggerSourceEntity> entities) {
        return ResponseEntity.ok(swaggerSourceService.saveAll(entities));
    }

    @PutMapping
    public ResponseEntity<SwaggerSourceEntity> update(@RequestBody SwaggerSourceEntity entity) {
        return ResponseEntity.ok(swaggerSourceService.update(entity));
    }

    @DeleteMapping
    public ResponseEntity<SwaggerSourceEntity> delete(@RequestBody SwaggerSourceEntity entity) {
        return ResponseEntity.ok(swaggerSourceService.delete(entity));
    }

    /** Tek kaynağı hemen senkronize eder. */
    @PostMapping("/{id}/sync")
    public ResponseEntity<SwaggerSyncResult> sync(@PathVariable UUID id) {
        SwaggerSyncResult result = swaggerSyncService.sync(id);
        return result == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(result);
    }

    /** ACTIVE ve autoSync = 1 olan tüm kaynakları hemen senkronize eder. */
    @PostMapping("/sync")
    public ResponseEntity<List<SwaggerSyncResult>> syncAll() {
        return ResponseEntity.ok(swaggerSyncService.syncAllAuto());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", String.valueOf(e.getMessage())));
    }
}
