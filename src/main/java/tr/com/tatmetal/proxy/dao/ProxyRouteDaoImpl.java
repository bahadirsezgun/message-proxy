package tr.com.tatmetal.proxy.dao;

import static tr.com.tatmetal.proxy.dao.sql.ProxyRouteSql.PROXY_ROUTE_GET_ONE_SQL;
import static tr.com.tatmetal.proxy.dao.sql.ProxyRouteSql.PROXY_ROUTE_GET_SQL;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import tr.com.zenergy.mini_jpa.annotation.aop.ExtendsBaseEntity;
import tr.com.zenergy.mini_jpa.jdbc.params_to_condition.ParamsToCondition;
import tr.com.zenergy.mini_jpa.jdbc.template.ZenergyNamedJdbcTemplate;

import tr.com.tatmetal.proxy.entity.ProxyRouteEntity;

@Repository
public class ProxyRouteDaoImpl {

    private final ZenergyNamedJdbcTemplate<ProxyRouteEntity> exdbJdbcTemplate;
    private Logger logger = LoggerFactory.getLogger(ProxyRouteDaoImpl.class);

    public ProxyRouteDaoImpl(@Qualifier("exdbJdbcTemplate")
                             ZenergyNamedJdbcTemplate<ProxyRouteEntity> exdbJdbcTemplate) {
        this.exdbJdbcTemplate = exdbJdbcTemplate;
    }

    @ExtendsBaseEntity
    public List<ProxyRouteEntity> saveAll(List<ProxyRouteEntity> entity) {
        return exdbJdbcTemplate.saveAll(entity);
    }

    @ExtendsBaseEntity
    public ProxyRouteEntity save(ProxyRouteEntity entity) {
        return exdbJdbcTemplate.save(entity);
    }

    @ExtendsBaseEntity
    public ProxyRouteEntity update(ProxyRouteEntity entity) {
        return exdbJdbcTemplate.update(entity);
    }

    public ProxyRouteEntity delete(ProxyRouteEntity entity) {
        return exdbJdbcTemplate.delete(entity);
    }

    public ProxyRouteEntity getOne(UUID id, UUID etag) {
        try {
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("id", id);
            queryMap.put("etag", etag);
            return exdbJdbcTemplate.findEntity(PROXY_ROUTE_GET_ONE_SQL, queryMap,
                    ProxyRouteEntity.class);
        } catch (Exception e) {
            logger.error(e.toString());
            return null;
        }
    }

    public List<ProxyRouteEntity> getByCondition(Map<String, Object> params) {
        try {
            return exdbJdbcTemplate.findEntityList(PROXY_ROUTE_GET_SQL +
                    ParamsToCondition.convertParamToCondition(params, ProxyRouteEntity.class, true),
                    params, ProxyRouteEntity.class);
        } catch (Exception e) {
            logger.error(e.toString());
            return null;
        }
    }
}
