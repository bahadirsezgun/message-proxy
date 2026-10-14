package tr.com.tatmetal.proxy.dao;

import static tr.com.tatmetal.proxy.dao.sql.AuthProfileSql.AUTH_PROFILE_GET_BY_ID_SQL;
import static tr.com.tatmetal.proxy.dao.sql.AuthProfileSql.AUTH_PROFILE_GET_ONE_SQL;
import static tr.com.tatmetal.proxy.dao.sql.AuthProfileSql.AUTH_PROFILE_GET_SQL;

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
import tr.com.tatmetal.proxy.entity.AuthProfileEntity;

@Repository
public class AuthProfileDaoImpl {

    private final ZenergyNamedJdbcTemplate<AuthProfileEntity> exdbJdbcTemplate;
    private Logger logger = LoggerFactory.getLogger(AuthProfileDaoImpl.class);

    public AuthProfileDaoImpl(@Qualifier("exdbJdbcTemplate")
            ZenergyNamedJdbcTemplate<AuthProfileEntity> exdbJdbcTemplate) {
        this.exdbJdbcTemplate = exdbJdbcTemplate;
    }

    @ExtendsBaseEntity
    public List<AuthProfileEntity> saveAll(List<AuthProfileEntity> entity) {
        return exdbJdbcTemplate.saveAll(entity);
    }

    @ExtendsBaseEntity
    public AuthProfileEntity save(AuthProfileEntity entity) {
        return exdbJdbcTemplate.save(entity);
    }

    @ExtendsBaseEntity
    public AuthProfileEntity update(AuthProfileEntity entity) {
        return exdbJdbcTemplate.update(entity);
    }

    public AuthProfileEntity delete(AuthProfileEntity entity) {
        return exdbJdbcTemplate.delete(entity);
    }

    public AuthProfileEntity getOne(UUID id, UUID etag) {
        try {
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("id", id);
            queryMap.put("etag", etag);
            return exdbJdbcTemplate.findEntity(AUTH_PROFILE_GET_ONE_SQL, queryMap,
                    AuthProfileEntity.class);
        } catch (Exception e) {
            logger.error(e.toString());
            return null;
        }
    }

    public List<AuthProfileEntity> getByCondition(Map<String, Object> params) {
        try {
            return exdbJdbcTemplate.findEntityList(AUTH_PROFILE_GET_SQL +
                    ParamsToCondition.convertParamToCondition(params, AuthProfileEntity.class, true),
                    params, AuthProfileEntity.class);
        } catch (Exception e) {
            logger.error(e.toString());
            return null;
        }
    }

    public AuthProfileEntity getById(UUID id) {
        try {
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("id", id);
            return exdbJdbcTemplate.findEntity(AUTH_PROFILE_GET_BY_ID_SQL, queryMap,
                    AuthProfileEntity.class);
        } catch (Exception e) {
            logger.error(e.toString());
            return null;
        }
    }
}
