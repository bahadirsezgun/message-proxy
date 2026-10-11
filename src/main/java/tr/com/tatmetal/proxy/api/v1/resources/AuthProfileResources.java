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

import tr.com.tatmetal.proxy.auth.AuthTokenTestResult;
import tr.com.tatmetal.proxy.entity.AuthProfileEntity;
import tr.com.tatmetal.proxy.services.AuthProfileService;

/**
 * Token alma tanımları. password ve clientSecret cevaplarda hiç dönmez (passwordSet / clientSecretSet döner).
 */
@RestController
@RequestMapping("/api/v1/auth-profile")
public class AuthProfileResources {

    private final AuthProfileService authProfileService;

    public AuthProfileResources(AuthProfileService authProfileService) {
        this.authProfileService = authProfileService;
    }

    @GetMapping("/{id}/{etag}")
    public ResponseEntity<AuthProfileEntity> getOne(@PathVariable UUID id, @PathVariable UUID etag) {
        AuthProfileEntity entity = authProfileService.getOne(id, etag);
        return entity == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(entity);
    }

    @PostMapping("/search")
    public ResponseEntity<List<AuthProfileEntity>> getByCondition(@RequestBody(required = false) Map<String, Object> params) {
        return ResponseEntity.ok(authProfileService.getByCondition(params));
    }

    @PostMapping
    public ResponseEntity<AuthProfileEntity> save(@RequestBody AuthProfileEntity entity) {
        return ResponseEntity.ok(authProfileService.save(entity));
    }

    @PostMapping("/all")
    public ResponseEntity<List<AuthProfileEntity>> saveAll(@RequestBody List<AuthProfileEntity> entities) {
        return ResponseEntity.ok(authProfileService.saveAll(entities));
    }

    /** password / clientSecret boş gönderilirse eski değer korunur, "-" gönderilirse silinir. */
    @PutMapping
    public ResponseEntity<AuthProfileEntity> update(@RequestBody AuthProfileEntity entity) {
        return ResponseEntity.ok(authProfileService.update(entity));
    }

    @DeleteMapping
    public ResponseEntity<AuthProfileEntity> delete(@RequestBody AuthProfileEntity entity) {
        return ResponseEntity.ok(authProfileService.delete(entity));
    }

    /** Tanımla token almayı dener; token'ın tamamı dönmez. */
    @PostMapping("/{id}/test")
    public ResponseEntity<AuthTokenTestResult> test(@PathVariable UUID id) {
        AuthTokenTestResult result = authProfileService.test(id);
        return result == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(result);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", String.valueOf(e.getMessage())));
    }
}
