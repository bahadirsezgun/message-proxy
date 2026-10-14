package tr.com.tatmetal.proxy.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import tr.com.tatmetal.proxy.dao.AuthProfileDaoImpl;
import tr.com.tatmetal.proxy.dao.SwaggerSourceDaoImpl;
import tr.com.tatmetal.proxy.entity.SwaggerSourceEntity;
import tr.com.tatmetal.proxy.enums.SyncResultEnum;
import tr.com.tatmetal.proxy.enums.YesNoEnum;
import tr.com.zenergy.mini_jpa.util.StatusEnum;

@Service
public class SwaggerSourceService {

    private final SwaggerSourceDaoImpl swaggerSourceDao;
    private final SwaggerSyncService swaggerSyncService;
    private final AuthProfileDaoImpl authProfileDao;

    public SwaggerSourceService(SwaggerSourceDaoImpl swaggerSourceDao, SwaggerSyncService swaggerSyncService,
                                AuthProfileDaoImpl authProfileDao) {
        this.swaggerSourceDao = swaggerSourceDao;
        this.swaggerSyncService = swaggerSyncService;
        this.authProfileDao = authProfileDao;
    }

    /**
     * Kaydeder; auto_sync = 1 ise endpoint'ler hemen okunur. Okuma hatası kaydı engellemez,
     * sonuç last_sync_result / last_sync_message alanlarına yazılır.
     */
    public SwaggerSourceEntity save(SwaggerSourceEntity entity) {
        rejectAppLink(entity);
        validate(entity);
        applyDefaults(entity);
        SwaggerSourceEntity saved = swaggerSourceDao.save(entity);
        return syncIfAuto(saved);
    }

    public List<SwaggerSourceEntity> saveAll(List<SwaggerSourceEntity> entities) {
        entities.forEach(e -> {
            rejectAppLink(e);
            validate(e);
            applyDefaults(e);
        });
        return swaggerSourceDao.saveAll(entities);
    }

    /**
     * Uygulamaya bağlı kaynaklarda sourceCode, swaggerUrl, targetBaseUrl, authProfileId ve appRegistryId
     * değiştirilemez (uygulama üzerinden yönetilir); autoSync, açıklama ve status değiştirilebilir.
     */
    public SwaggerSourceEntity update(SwaggerSourceEntity entity) {
        SwaggerSourceEntity existing = entity.getId() == null || entity.getEtag() == null
                ? null : swaggerSourceDao.getOne(entity.getId(), entity.getEtag());
        if (existing != null && existing.getAppRegistryId() != null) {
            applyDefaults(entity);
            if (!Objects.equals(existing.getAppRegistryId(), entity.getAppRegistryId())
                    || !Objects.equals(existing.getSourceCode(), entity.getSourceCode())
                    || !Objects.equals(existing.getSwaggerUrl(), entity.getSwaggerUrl())
                    || !Objects.equals(existing.getTargetBaseUrl(), entity.getTargetBaseUrl())
                    || !Objects.equals(existing.getAuthProfileId(), entity.getAuthProfileId())) {
                throw new IllegalArgumentException("Bu swagger kaynağı bir uygulamaya bağlı; kod, adres ve token alanlarını "
                        + "uygulama üzerinden güncelleyin (PUT /api/v1/app-registry)");
            }
        } else {
            rejectAppLink(entity);
        }
        validate(entity);
        applyDefaults(entity);
        return swaggerSourceDao.update(entity);
    }

    // ------------------------------------------------------------------
    // Uygulama (app_registry) tarafından kullanılan işlemler — koruma kuralı uygulanmaz
    // ------------------------------------------------------------------

    /** Uygulama için yeni kaynak oluşturur; autoSync açıksa hemen senkronize eder. */
    public SwaggerSourceEntity saveForApp(SwaggerSourceEntity entity) {
        validate(entity);
        applyDefaults(entity);
        return syncIfAuto(swaggerSourceDao.save(entity));
    }

    /** Uygulamanın kaynağını günceller; {@code sync} true ve kaynak aktifse hemen senkronize eder. */
    public SwaggerSourceEntity updateForApp(SwaggerSourceEntity entity, boolean sync) {
        validate(entity);
        applyDefaults(entity);
        SwaggerSourceEntity updated = swaggerSourceDao.update(entity);
        if (sync && updated != null && updated.getStatus() == StatusEnum.ACTIVE) {
            return syncIfAuto(updated);
        }
        return updated;
    }

    public SwaggerSourceEntity getByAppRegistryId(UUID appRegistryId) {
        return swaggerSourceDao.getByAppRegistryId(appRegistryId);
    }

    public SwaggerSourceEntity getBySourceCode(String sourceCode) {
        return swaggerSourceDao.getBySourceCode(sourceCode);
    }

    private void rejectAppLink(SwaggerSourceEntity entity) {
        if (entity.getAppRegistryId() != null) {
            throw new IllegalArgumentException("Uygulamaya bağlı swagger kaynağı uygulama üzerinden oluşturulur / bağlanır "
                    + "(/api/v1/app-registry, hasSwagger = 1)");
        }
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
        if (entity.getAuthProfileId() != null && authProfileDao.getById(entity.getAuthProfileId()) == null) {
            throw new IllegalArgumentException("authProfileId ile tanım bulunamadı ya da iptal edilmiş: "
                    + entity.getAuthProfileId());
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
