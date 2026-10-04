package tr.com.tatmetal.proxy.dao;

import static tr.com.tatmetal.proxy.dao.sql.ProxyFieldMapSql.PROXY_FIELD_MAP_GET_ACTIVE_SQL;
import static tr.com.tatmetal.proxy.dao.sql.ProxyFieldMapSql.PROXY_FIELD_MAP_GET_ONE_SQL;
import static tr.com.tatmetal.proxy.dao.sql.ProxyFieldMapSql.PROXY_FIELD_MAP_GET_SQL;

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

import tr.com.tatmetal.proxy.entity.ProxyFieldMapEntity;

@Repository
public class ProxyFieldMapDaoImpl {

    private final ZenergyNamedJdbcTemplate<ProxyFieldMapEntity> exdbJdbcTemplate;
    private Logger logger = LoggerFactory.getLogger(ProxyFieldMapDaoImpl.class);

    public ProxyFieldMapDaoImpl(@Qualifier("exdbJdbcTemplate")
            ZenergyNamedJdbcTemplate<ProxyFieldMapEntity> exdbJdbcTemplate) {
        this.exdbJdbcTemplate = exdbJdbcTemplate;
    }

    @ExtendsBaseEntity
    public List<ProxyFieldMapEntity> saveAll(List<ProxyFieldMapEntity> entity) {
        return exdbJdbcTemplate.saveAll(entity);
    }

    @ExtendsBaseEntity
    public ProxyFieldMapEntity save(ProxyFieldMapEntity entity) {
        return exdbJdbcTemplate.save(entity);
    }

    @ExtendsBaseEntity
    public ProxyFieldMapEntity update(ProxyFieldMapEntity entity) {
        return exdbJdbcTemplate.update(entity);
    }

    public ProxyFieldMapEntity delete(ProxyFieldMapEntity entity) {
        return exdbJdbcTemplate.delete(entity);
    }

    public ProxyFieldMapEntity getOne(UUID id, UUID etag) {
        try {
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("id", id);
            queryMap.put("etag", etag);
            return exdbJdbcTemplate.findEntity(PROXY_FIELD_MAP_GET_ONE_SQL, queryMap,
                    ProxyFieldMapEntity.class);
        } catch (Exception e) {
            logger.error(e.toString());
            return null;
        }
    }

    public List<ProxyFieldMapEntity> getByCondition(Map<String, Object> params) {
        try {
            return exdbJdbcTemplate.findEntityList(PROXY_FIELD_MAP_GET_SQL +
                    ParamsToCondition.convertParamToCondition(params, ProxyFieldMapEntity.class, true),
                    params, ProxyFieldMapEntity.class);
        } catch (Exception e) {
            logger.error(e.toString());
            return null;
        }
    }

    /** Aktif route'lara ait aktif eşleştirmeler (Redis yüklemesi için). Hata durumunda exception fırlatır. */
    public List<ProxyFieldMapEntity> getActiveList() {
        try {
            return exdbJdbcTemplate.findEntityList(PROXY_FIELD_MAP_GET_ACTIVE_SQL, new HashMap<>(), ProxyFieldMapEntity.class);
        } catch (Exception e) {
            logger.error(e.toString());
            throw new IllegalStateException("Aktif kayıtlar okunamadı: " + e.getMessage(), e);
        }
    }
}
