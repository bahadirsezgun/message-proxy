package tr.com.tatmetal.proxy.api.v1.resources;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tr.com.tatmetal.proxy.entity.SwaggerEndpointEntity;
import tr.com.tatmetal.proxy.services.SwaggerEndpointService;

/**
 * Swagger'dan okunan endpoint'ler. Kayıtlar senkronizasyon ile oluşur; ekleme/silme yoktur.
 */
@RestController
@RequestMapping("/api/v1/swagger-endpoint")
public class SwaggerEndpointResources {

    private final SwaggerEndpointService swaggerEndpointService;

    public SwaggerEndpointResources(SwaggerEndpointService swaggerEndpointService) {
        this.swaggerEndpointService = swaggerEndpointService;
    }

    @GetMapping("/{id}/{etag}")
    public ResponseEntity<SwaggerEndpointEntity> getOne(@PathVariable UUID id, @PathVariable UUID etag) {
        SwaggerEndpointEntity entity = swaggerEndpointService.getOne(id, etag);
        return entity == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(entity);
    }

    /** Örn: {"swaggerSourceId": "...", "httpMethod": "POST"} */
    @PostMapping("/search")
    public ResponseEntity<List<SwaggerEndpointEntity>> getByCondition(@RequestBody(required = false) Map<String, Object> params) {
        return ResponseEntity.ok(swaggerEndpointService.getByCondition(params));
    }

    /** Örn. bir endpoint'i PASSIVE yapmak için; senkronizasyon status'a dokunmaz. */
    @PutMapping
    public ResponseEntity<SwaggerEndpointEntity> update(@RequestBody SwaggerEndpointEntity entity) {
        return ResponseEntity.ok(swaggerEndpointService.update(entity));
    }
}
