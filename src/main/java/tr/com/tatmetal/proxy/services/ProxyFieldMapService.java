package tr.com.tatmetal.proxy.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tr.com.tatmetal.proxy.dao.ProxyFieldMapDaoImpl;
import tr.com.tatmetal.proxy.entity.ProxyFieldMapEntity;

/**
 * proxy_field_map CRUD. Kayıt öncesi alan yolu söz dizimi doğrulanır,
 * her değişiklikten sonra Redis'teki tanımlar yenilenir.
 */
@Service
public class ProxyFieldMapService {

    private final ProxyFieldMapDaoImpl proxyFieldMapDao;
    private final ProxyRouteCacheService proxyRouteCacheService;
    private final Logger logger = LoggerFactory.getLogger(ProxyFieldMapService.class);

    public ProxyFieldMapService(ProxyFieldMapDaoImpl proxyFieldMapDao,
                                ProxyRouteCacheService proxyRouteCacheService) {
        this.proxyFieldMapDao = proxyFieldMapDao;
        this.proxyRouteCacheService = proxyRouteCacheService;
    }

    public ProxyFieldMapEntity save(ProxyFieldMapEntity entity) {
        validate(entity);
        return refreshCache(proxyFieldMapDao.save(entity));
    }

    public List<ProxyFieldMapEntity> saveAll(List<ProxyFieldMapEntity> entities) {
        entities.forEach(this::validate);
        return refreshCache(proxyFieldMapDao.saveAll(entities));
    }

    public ProxyFieldMapEntity update(ProxyFieldMapEntity entity) {
        validate(entity);
        return refreshCache(proxyFieldMapDao.update(entity));
    }

    public ProxyFieldMapEntity delete(ProxyFieldMapEntity entity) {
        return refreshCache(proxyFieldMapDao.delete(entity));
    }

    public ProxyFieldMapEntity getOne(UUID id, UUID etag) {
        return proxyFieldMapDao.getOne(id, etag);
    }

    public List<ProxyFieldMapEntity> getByCondition(Map<String, Object> params) {
        return proxyFieldMapDao.getByCondition(params == null ? new HashMap<>() : params);
    }

    private void validate(ProxyFieldMapEntity entity) {
        if (entity.getProxyRouteId() == null) {
            throw new IllegalArgumentException("proxyRouteId zorunludur");
        }
        ProxyFieldMappingService.validate(entity.getSourceField(), entity.getTargetField());
        entity.setSourceField(entity.getSourceField().trim());
        entity.setTargetField(entity.getTargetField().trim());
    }

    private <T> T refreshCache(T result) {
        try {
            proxyRouteCacheService.reload();
        } catch (Exception e) {
            logger.error("Alan eşleştirme değişikliği sonrası Redis yenilenemedi. error={}", e.toString());
        }
        return result;
    }
}
