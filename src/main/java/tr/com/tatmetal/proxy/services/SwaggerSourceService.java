package tr.com.tatmetal.proxy.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import tr.com.tatmetal.proxy.dao.SwaggerSourceDaoImpl;
import tr.com.tatmetal.proxy.entity.SwaggerSourceEntity;
import tr.com.tatmetal.proxy.enums.SyncResultEnum;
import tr.com.tatmetal.proxy.enums.YesNoEnum;

@Service
public class SwaggerSourceService {

    private final SwaggerSourceDaoImpl swaggerSourceDao;
    private final SwaggerSyncService swaggerSyncService;

    public SwaggerSourceService(SwaggerSourceDaoImpl swaggerSourceDao, SwaggerSyncService swaggerSyncService) {
        this.swaggerSourceDao = swaggerSourceDao;
        this.swaggerSyncService = swaggerSyncService;
    }

    /**
     * Kaydeder; auto_sync = 1 ise endpoint'ler hemen okunur. Okuma hatası kaydı engellemez,
     * sonuç last_sync_result / last_sync_message alanlarına yazılır.
     */
    public SwaggerSourceEntity save(SwaggerSourceEntity entity) {
        validate(entity);
        applyDefaults(entity);
        SwaggerSourceEntity saved = swaggerSourceDao.save(entity);
        return syncIfAuto(saved);
    }

    public List<SwaggerSourceEntity> saveAll(List<SwaggerSourceEntity> entities) {
        entities.forEach(e -> {
            validate(e);
            applyDefaults(e);
        });
        return swaggerSourceDao.saveAll(entities);
    }

    public SwaggerSourceEntity update(SwaggerSourceEntity entity) {
        validate(entity);
        applyDefaults(entity);
        return swaggerSourceDao.update(entity);
    }

    public SwaggerSourceEntity delete(SwaggerSourceEntity entity) {
        return swaggerSourceDao.delete(entity);
    }

    public SwaggerSourceEntity getOne(UUID id, UUID etag) {
        return swaggerSourceDao.getOne(id, etag);
    }

    public List<SwaggerSourceEntity> getByCondition(Map<String, Object> params) {
        return swaggerSourceDao.getByCondition(params == null ? new HashMap<>() : params);
    }

    private SwaggerSourceEntity syncIfAuto(SwaggerSourceEntity saved) {
        if (saved == null || saved.getId() == null || saved.getAutoSync() == YesNoEnum.NO) {
            return saved;
        }
        swaggerSyncService.sync(saved);
        SwaggerSourceEntity refreshed = swaggerSourceDao.getById(saved.getId());
        return refreshed == null ? saved : refreshed;
    }

    private void validate(SwaggerSourceEntity entity) {
        if (entity.getSourceCode() == null || entity.getSourceCode().isBlank()) {
            throw new IllegalArgumentException("sourceCode zorunludur");
        }
        if (entity.getSwaggerUrl() == null || entity.getSwaggerUrl().isBlank()) {
            throw new IllegalArgumentException("swaggerUrl zorunludur");
        }
        String url = entity.getSwaggerUrl().trim().toLowerCase();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("swaggerUrl http:// veya https:// ile başlamalıdır");
        }
    }

    /** NOT NULL kolonlara null gitmesin diye tablo default'larıyla aynı değerler. */
    private void applyDefaults(SwaggerSourceEntity entity) {
        entity.setSwaggerUrl(entity.getSwaggerUrl().trim());
        if (entity.getSourceDescription() == null || entity.getSourceDescription().isBlank()) {
            entity.setSourceDescription("-");
        }
        if (entity.getTargetBaseUrl() == null || entity.getTargetBaseUrl().isBlank()) {
            entity.setTargetBaseUrl("-");
        }
        if (entity.getAutoSync() == null) {
            entity.setAutoSync(YesNoEnum.YES);
        }
        if (entity.getLastSyncResult() == null) {
            entity.setLastSyncResult(SyncResultEnum.NEVER);
        }
        if (entity.getLastSyncMessage() == null || entity.getLastSyncMessage().isBlank()) {
            entity.setLastSyncMessage("-");
        }
    }
}
