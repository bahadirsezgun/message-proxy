package tr.com.tatmetal.proxy.dao;

import static tr.com.tatmetal.proxy.dao.sql.SwaggerSourceSql.SWAGGER_SOURCE_GET_AUTO_SYNC_SQL;
import static tr.com.tatmetal.proxy.dao.sql.SwaggerSourceSql.SWAGGER_SOURCE_GET_BY_ID_SQL;
import static tr.com.tatmetal.proxy.dao.sql.SwaggerSourceSql.SWAGGER_SOURCE_GET_ONE_SQL;
import static tr.com.tatmetal.proxy.dao.sql.SwaggerSourceSql.SWAGGER_SOURCE_GET_SQL;
import static tr.com.tatmetal.proxy.dao.sql.SwaggerSourceSql.SWAGGER_SOURCE_UPDATE_SYNC_RESULT_SQL;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import tr.com.zenergy.mini_jpa.annotation.aop.ExtendsBaseEntity;
import tr.com.zenergy.mini_jpa.jdbc.params_to_condition.ParamsToCondition;
import tr.com.zenergy.mini_jpa.jdbc.template.ZenergyNamedJdbcTemplate;
import tr.com.tatmetal.proxy.entity.SwaggerSourceEntity;
import tr.com.tatmetal.proxy.enums.SyncResultEnum;

@Repository
public class SwaggerSourceDaoImpl {

    private final ZenergyNamedJdbcTemplate<SwaggerSourceEntity> exdbJdbcTemplate;
    /** Entity'ye bağlı olmayan UPDATE'ler için (senkronizasyon sonucu). Aynı exDb DataSource'u kullanır. */
    private final NamedParameterJdbcTemplate exdbSqlTemplate;
    private Logger logger = LoggerFactory.getLogger(SwaggerSourceDaoImpl.class);

    public SwaggerSourceDaoImpl(@Qualifier("exdbJdbcTemplate")
            ZenergyNamedJdbcTemplate<SwaggerSourceEntity> exdbJdbcTemplate,
            @Qualifier("exDb") DataSource exdbDataSource) {
        this.exdbJdbcTemplate = exdbJdbcTemplate;
        this.exdbSqlTemplate = new NamedParameterJdbcTemplate(exdbDataSource);
    }

    @ExtendsBaseEntity
    public List<SwaggerSourceEntity> saveAll(List<SwaggerSourceEntity> entity) {
        return exdbJdbcTemplate.saveAll(entity);
    }

    @ExtendsBaseEntity
    public SwaggerSourceEntity save(SwaggerSourceEntity entity) {
        return exdbJdbcTemplate.save(entity);
    }

    @ExtendsBaseEntity
    public SwaggerSourceEntity update(SwaggerSourceEntity entity) {
        return exdbJdbcTemplate.update(entity);
    }

    public SwaggerSourceEntity delete(SwaggerSourceEntity entity) {
        return exdbJdbcTemplate.delete(entity);
    }

    public SwaggerSourceEntity getOne(UUID id, UUID etag) {
        try {
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("id", id);
            queryMap.put("etag", etag);
            return exdbJdbcTemplate.findEntity(SWAGGER_SOURCE_GET_ONE_SQL, queryMap,
                    SwaggerSourceEntity.class);
        } catch (Exception e) {
            logger.error(e.toString());
            return null;
        }
    }

    public List<SwaggerSourceEntity> getByCondition(Map<String, Object> params) {
        try {
            return exdbJdbcTemplate.findEntityList(SWAGGER_SOURCE_GET_SQL +
                    ParamsToCondition.convertParamToCondition(params, SwaggerSourceEntity.class, true),
                    params, SwaggerSourceEntity.class);
        } catch (Exception e) {
            logger.error(e.toString());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Senkronizasyon
    // ------------------------------------------------------------------

    public SwaggerSourceEntity getById(UUID id) {
        try {
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("id", id);
            return exdbJdbcTemplate.findEntity(SWAGGER_SOURCE_GET_BY_ID_SQL, queryMap,
                    SwaggerSourceEntity.class);
        } catch (Exception e) {
            logger.error(e.toString());
            return null;
        }
    }

    public List<SwaggerSourceEntity> getAutoSyncSources() {
        try {
            return exdbJdbcTemplate.findEntityList(SWAGGER_SOURCE_GET_AUTO_SYNC_SQL, new HashMap<>(),
                    SwaggerSourceEntity.class);
        } catch (Exception e) {
            logger.error(e.toString());
            return null;
        }
    }

    /**
     * @param endpointCount null ise mevcut değer korunur (hata durumunda eski sayı kalsın diye)
     */
    public int updateSyncResult(UUID id, Timestamp syncDate, SyncResultEnum result, String message,
                                Integer endpointCount) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("lastSyncDate", syncDate)
                .addValue("lastSyncResult", result.getId())
                .addValue("lastSyncMessage", message)
                .addValue("endpointCount", endpointCount, java.sql.Types.INTEGER);
        return exdbSqlTemplate.update(SWAGGER_SOURCE_UPDATE_SYNC_RESULT_SQL, params);
    }
}
