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

import tr.com.tatmetal.proxy.app.AppOverview;
import tr.com.tatmetal.proxy.entity.AppRegistryEntity;
import tr.com.tatmetal.proxy.services.AppRegistryService;
import tr.com.tatmetal.proxy.swagger.SwaggerSyncResult;

/**
 * Entegre olunan uygulamalar. Swagger kaynağı uygulamadan yönetilir; route'lar uygulamaya bağlanabilir.
 */
@RestController
@RequestMapping("/api/v1/app-registry")
public class AppRegistryResources {

    private final AppRegistryService appRegistryService;

    public AppRegistryResources(AppRegistryService appRegistryService) {
        this.appRegistryService = appRegistryService;
    }

    @GetMapping("/{id}/{etag}")
    public ResponseEntity<AppRegistryEntity> getOne(@PathVariable UUID id, @PathVariable UUID etag) {
        AppRegistryEntity entity = appRegistryService.getOne(id, etag);
        return entity == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(entity);
    }

    @PostMapping("/search")
    public ResponseEntity<List<AppRegistryEntity>> getByCondition(@RequestBody(required = false) Map<String, Object> params) {
        return ResponseEntity.ok(appRegistryService.getByCondition(params));
    }

    /** Kaydeder; hasSwagger = 1 ise swagger kaynağı oluşturulur ve hemen senkronize edilir. */
    @PostMapping
    public ResponseEntity<AppRegistryEntity> save(@RequestBody AppRegistryEntity entity) {
        return ResponseEntity.ok(appRegistryService.save(entity));
    }

    @PostMapping("/all")
    public ResponseEntity<List<AppRegistryEntity>> saveAll(@RequestBody List<AppRegistryEntity> entities) {
        return ResponseEntity.ok(appRegistryService.saveAll(entities));
    }

    @PutMapping
    public ResponseEntity<AppRegistryEntity> update(@RequestBody AppRegistryEntity entity) {
        return ResponseEntity.ok(appRegistryService.update(entity));
    }

    /** İptal eder (CANCELLED); bağlı route varsa 400. */
    @DeleteMapping
    public ResponseEntity<AppRegistryEntity> delete(@RequestBody AppRegistryEntity entity) {
        return ResponseEntity.ok(appRegistryService.delete(entity));
    }

    /** Uygulamanın swagger'ını hemen senkronize eder. */
    @PostMapping("/{id}/sync")
    public ResponseEntity<SwaggerSyncResult> sync(@PathVariable UUID id) {
        SwaggerSyncResult result = appRegistryService.sync(id);
        return result == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(result);
    }

    /** Uygulama + swagger durumu + çağırdığı ve onu çağıran uygulamalar. */
    @GetMapping("/{id}/overview")
    public ResponseEntity<AppOverview> overview(@PathVariable UUID id) {
        AppOverview overview = appRegistryService.overview(id);
        return overview == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(overview);
    }

    /** Tüm uygulamaların genel bakışı (entegrasyon haritası). */
    @PostMapping("/overview")
    public ResponseEntity<List<AppOverview>> overviewAll() {
        return ResponseEntity.ok(appRegistryService.overviewAll());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", String.valueOf(e.getMessage())));
    }
}
