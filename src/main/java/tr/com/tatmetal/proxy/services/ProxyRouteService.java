package tr.com.tatmetal.proxy.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tr.com.tatmetal.proxy.dao.AppRegistryDaoImpl;
import tr.com.tatmetal.proxy.dao.ProxyRouteDaoImpl;
import tr.com.tatmetal.proxy.entity.ProxyRouteEntity;

/**
 * proxy_route CRUD. Her değişiklikten sonra Redis'teki tanımlar yenilenir.
 */
@Service
public class ProxyRouteService {

    private final ProxyRouteDaoImpl proxyRouteDao;
    private final ProxyRouteCacheService proxyRouteCacheService;
    private final Logger logger = LoggerFactory.getLogger(ProxyRouteService.class);

    private final AppRegistryDaoImpl appRegistryDao;

    public ProxyRouteService(ProxyRouteDaoImpl proxyRouteDao, ProxyRouteCacheService proxyRouteCacheService,
                             AppRegistryDaoImpl appRegistryDao) {
        this.appRegistryDao = appRegistryDao;
        this.proxyRouteDao = proxyRouteDao;
        this.proxyRouteCacheService = proxyRouteCacheService;
    }

    public ProxyRouteEntity save(ProxyRouteEntity entity) {
        normalize(entity);
        return refreshCache(proxyRouteDao.save(entity));
    }

    public List<ProxyRouteEntity> saveAll(List<ProxyRouteEntity> entities) {
        entities.forEach(this::normalize);
        return refreshCache(proxyRouteDao.saveAll(entities));
    }

    public ProxyRouteEntity update(ProxyRouteEntity entity) {
        normalize(entity);
        return refreshCache(proxyRouteDao.update(entity));
    }

    public ProxyRouteEntity delete(ProxyRouteEntity entity) {
        return refreshCache(proxyRouteDao.delete(entity));
    }

    public ProxyRouteEntity getOne(UUID id, UUID etag) {
        return proxyRouteDao.getOne(id, etag);
    }

    public List<ProxyRouteEntity> getByCondition(Map<String, Object> params) {
        return proxyRouteDao.getByCondition(params == null ? new HashMap<>() : params);
    }

    /** Gelen path aynı biçimde saklansın: baştaki "/" eklenir, sondaki "/" kaldırılır. */
    private void normalize(ProxyRouteEntity entity) {
        if (entity != null && entity.getSourcePath() != null && !"-".equals(entity.getSourcePath())) {
            entity.setSourcePath(ProxyRouteCacheService.normalizePath(entity.getSourcePath()));
        }
        if (entity != null) {
            validateAppLinks(entity);
        }
    }

    /**
     * Uygulama bağlantılarını doğrular: verilen uygulamalar var ve iptal edilmemiş olmalı;
     * göreli target_url ("/..." ile başlayan) için hedef uygulama zorunludur.
     */
    private void validateAppLinks(ProxyRouteEntity entity) {
        if (entity.getTargetAppRegistryId() != null && appRegistryDao.getById(entity.getTargetAppRegistryId()) == null) {
            throw new IllegalArgumentException("targetAppRegistryId ile uygulama bulunamadı ya da iptal edilmiş: "
                    + entity.getTargetAppRegistryId());
        }
        if (entity.getSourceAppRegistryId() != null && appRegistryDao.getById(entity.getSourceAppRegistryId()) == null) {
            throw new IllegalArgumentException("sourceAppRegistryId ile uygulama bulunamadı ya da iptal edilmiş: "
                    + entity.getSourceAppRegistryId());
        }
        String targetUrl = entity.getTargetUrl();
        if (targetUrl != null && !targetUrl.isBlank()) {
            entity.setTargetUrl(targetUrl.trim());
            if (!ProxyRouteCacheService.isAbsoluteUrl(targetUrl) && entity.getTargetAppRegistryId() == null) {
                throw new IllegalArgumentException("targetUrl göreli (" + targetUrl.trim() + "); göreli adres için "
                        + "targetAppRegistryId zorunludur. Ya tam adres (http://...) yazın ya da hedef uygulamayı seçin.");
            }
        }
    }

    /** Kayıt başarılı olduysa Redis yenilenir; Redis hatası kaydı geri almaz, sadece loglanır. */
    private <T> T refreshCache(T result) {
        try {
            proxyRouteCacheService.reload();
        } catch (Exception e) {
            logger.error("Route değişikliği sonrası Redis yenilenemedi. error={}", e.toString());
        }
        return result;
    }
}
