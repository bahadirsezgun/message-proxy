package tr.com.tatmetal.proxy.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import tr.com.tatmetal.proxy.dao.SwaggerEndpointDaoImpl;
import tr.com.tatmetal.proxy.entity.SwaggerEndpointEntity;

/**
 * Endpoint kayıtları SwaggerSyncService tarafından yazılır; burada sadece okuma ve
 * (örn. bir endpoint'i PASSIVE yapmak için) güncelleme vardır.
 */
@Service
public class SwaggerEndpointService {

    private final SwaggerEndpointDaoImpl swaggerEndpointDao;

    public SwaggerEndpointService(SwaggerEndpointDaoImpl swaggerEndpointDao) {
        this.swaggerEndpointDao = swaggerEndpointDao;
    }

    public SwaggerEndpointEntity update(SwaggerEndpointEntity entity) {
        return swaggerEndpointDao.update(entity);
    }

    public SwaggerEndpointEntity getOne(UUID id, UUID etag) {
        return swaggerEndpointDao.getOne(id, etag);
    }

    public List<SwaggerEndpointEntity> getByCondition(Map<String, Object> params) {
        return swaggerEndpointDao.getByCondition(params == null ? new HashMap<>() : params);
    }
}
