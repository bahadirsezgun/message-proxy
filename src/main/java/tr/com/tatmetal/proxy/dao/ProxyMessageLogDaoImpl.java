package tr.com.tatmetal.proxy.dao;

import static tr.com.tatmetal.proxy.dao.sql.ProxyMessageLogSql.PROXY_MESSAGE_LOG_GET_ONE_SQL;
import static tr.com.tatmetal.proxy.dao.sql.ProxyMessageLogSql.PROXY_MESSAGE_LOG_GET_SQL;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import tr.com.zenergy.mini_jpa.annotation.aop.ExtendsBaseEntity;
import tr.com.zenergy.mini_jpa.jdbc.template.ZenergyNamedJdbcTemplate;
import tr.com.zenergy.mini_jpa.jdbc.params_to_condition.ParamsToCondition;
import tr.com.tatmetal.proxy.entity.ProxyMessageLogEntity;

@Repository
public class ProxyMessageLogDaoImpl {

    private final ZenergyNamedJdbcTemplate<ProxyMessageLogEntity> exdbJdbcTemplate;
    private Logger logger = LoggerFactory.getLogger(ProxyMessageLogDaoImpl.class);

    public ProxyMessageLogDaoImpl(@Qualifier("exdbJdbcTemplate")
            ZenergyNamedJdbcTemplate<ProxyMessageLogEntity> exdbJdbcTemplate) {
        this.exdbJdbcTemplate = exdbJdbcTemplate;
    }

    @ExtendsBaseEntity
    public List<ProxyMessageLogEntity> saveAll(List<ProxyMessageLogEntity> entity) {
        return exdbJdbcTemplate.saveAll(entity);
    }

    @ExtendsBaseEntity
    public ProxyMessageLogEntity save(ProxyMessageLogEntity entity) {
        return exdbJdbcTemplate.save(entity);
    }

    @ExtendsBaseEntity
    public ProxyMessageLogEntity update(ProxyMessageLogEntity entity) {
        return exdbJdbcTemplate.update(entity);
    }

    public ProxyMessageLogEntity delete(ProxyMessageLogEntity entity) {
        return exdbJdbcTemplate.delete(entity);
    }

    public ProxyMessageLogEntity getOne(UUID id, UUID etag) {
        try {
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("id", id);
            queryMap.put("etag", etag);
            return exdbJdbcTemplate.findEntity(PROXY_MESSAGE_LOG_GET_ONE_SQL, queryMap,
                    ProxyMessageLogEntity.class);
        } catch (Exception e) {
            logger.error(e.toString());
            return null;
        }
    }

    public List<ProxyMessageLogEntity> getByCondition(Map<String, Object> params) {
        try {
            return exdbJdbcTemplate.findEntityList(PROXY_MESSAGE_LOG_GET_SQL +
                    ParamsToCondition.convertParamToCondition(params, ProxyMessageLogEntity.class, true),
                    params, ProxyMessageLogEntity.class);
        } catch (Exception e) {
            logger.error(e.toString());
            return null;
        }
    }
}
