package tr.com.tatmetal.proxy.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import tr.com.tatmetal.proxy.dao.ProxyRouteDaoImpl;
import tr.com.tatmetal.proxy.entity.ProxyRouteEntity;

@Service
public class ProxyRouteService {

    private final ProxyRouteDaoImpl proxyRouteDao;

    public ProxyRouteService(ProxyRouteDaoImpl proxyRouteDao) {
        this.proxyRouteDao = proxyRouteDao;
    }

    public ProxyRouteEntity save(ProxyRouteEntity entity) {
        return proxyRouteDao.save(entity);
    }

    public List<ProxyRouteEntity> saveAll(List<ProxyRouteEntity> entities) {
        return proxyRouteDao.saveAll(entities);
    }

    public ProxyRouteEntity update(ProxyRouteEntity entity) {
        return proxyRouteDao.update(entity);
    }

    public ProxyRouteEntity delete(ProxyRouteEntity entity) {
        return proxyRouteDao.delete(entity);
    }

    public ProxyRouteEntity getOne(UUID id, UUID etag) {
        return proxyRouteDao.getOne(id, etag);
    }

    public List<ProxyRouteEntity> getByCondition(Map<String, Object> params) {
        return proxyRouteDao.getByCondition(params == null ? new HashMap<>() : params);
    }
}
