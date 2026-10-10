package tr.com.tatmetal.proxy.dao;

import static tr.com.tatmetal.proxy.dao.sql.SwaggerEndpointSql.SWAGGER_ENDPOINT_CANCEL_MISSING_SQL;
import static tr.com.tatmetal.proxy.dao.sql.SwaggerEndpointSql.SWAGGER_ENDPOINT_GET_ONE_SQL;
import static tr.com.tatmetal.proxy.dao.sql.SwaggerEndpointSql.SWAGGER_ENDPOINT_GET_SQL;
import static tr.com.tatmetal.proxy.dao.sql.SwaggerEndpointSql.SWAGGER_ENDPOINT_SYNC_TIME_SQL;
import static tr.com.tatmetal.proxy.dao.sql.SwaggerEndpointSql.SWAGGER_ENDPOINT_TRY_LOCK_SQL;
import static tr.com.tatmetal.proxy.dao.sql.SwaggerEndpointSql.SWAGGER_ENDPOINT_UPSERT_SQL;

import java.sql.Timestamp;
import java.sql.Types;
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
import tr.com.tatmetal.proxy.entity.SwaggerEndpointEntity;
import tr.com.tatmetal.proxy.enums.YesNoEnum;

@Repository
public class SwaggerEndpointDaoImpl {

    /** Upsert sonucu. */
    public enum UpsertOutcome {
        INSERTED, UPDATED, UNCHANGED
    }

    private final ZenergyNamedJdbcTemplate<SwaggerEndpointEntity> exdbJdbcTemplate;
    /** Upsert / toplu CANCELLED / advisory lock gibi entity'ye bağlı olmayan SQL'ler için. Aynı exDb DataSource'u kullanır. */
    private final NamedParameterJdbcTemplate exdbSqlTemplate;
    private Logger logger = LoggerFactory.getLogger(SwaggerEndpointDaoImpl.class);

    public SwaggerEndpointDaoImpl(@Qualifier("exdbJdbcTemplate")
            ZenergyNamedJdbcTemplate<SwaggerEndpointEntity> exdbJdbcTemplate,
            @Qualifier("exDb") DataSource exdbDataSource) {
        this.exdbJdbcTemplate = exdbJdbcTemplate;
        this.exdbSqlTemplate = new NamedParameterJdbcTemplate(exdbDataSource);
    }

    @ExtendsBaseEntity
    public List<SwaggerEndpointEntity> saveAll(List<SwaggerEndpointEntity> entity) {
        return exdbJdbcTemplate.saveAll(entity);
    }

    @ExtendsBaseEntity
    public SwaggerEndpointEntity save(SwaggerEndpointEntity entity) {
        return exdbJdbcTemplate.save(entity);
    }

    @ExtendsBaseEntity
    public SwaggerEndpointEntity update(SwaggerEndpointEntity entity) {
        return exdbJdbcTemplate.update(entity);
    }

    public SwaggerEndpointEntity delete(SwaggerEndpointEntity entity) {
        return exdbJdbcTemplate.delete(entity);
    }

    public SwaggerEndpointEntity getOne(UUID id, UUID etag) {
        try {
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("id", id);
            queryMap.put("etag", etag);
            return exdbJdbcTemplate.findEntity(SWAGGER_ENDPOINT_GET_ONE_SQL, queryMap,
                    SwaggerEndpointEntity.class);
        } catch (Exception e) {
            logger.error(e.toString());
            return null;
        }
    }

    public List<SwaggerEndpointEntity> getByCondition(Map<String, Object> params) {
        try {
            return exdbJdbcTemplate.findEntityList(SWAGGER_ENDPOINT_GET_SQL +
                    ParamsToCondition.convertParamToCondition(params, SwaggerEndpointEntity.class, true),
                    params, SwaggerEndpointEntity.class);
        } catch (Exception e) {
            logger.error(e.toString());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Senkronizasyon (transaction içinde çağrılmalıdır)
    // ------------------------------------------------------------------

    /** Veritabanı saatine göre senkronizasyon zamanı (transaction içinde sabittir). */
    public Timestamp currentSyncTime() {
        return exdbSqlTemplate.queryForObject(SWAGGER_ENDPOINT_SYNC_TIME_SQL, new MapSqlParameterSource(),
                Timestamp.class);
    }

    /** Kaynak için transaction'a bağlı kilit almayı dener; başka bir senkronizasyon sürüyorsa false. */
    public boolean tryLockSource(UUID swaggerSourceId) {
        Boolean locked = exdbSqlTemplate.queryForObject(SWAGGER_ENDPOINT_TRY_LOCK_SQL,
                new MapSqlParameterSource("lockKey", "swagger_sync:" + swaggerSourceId), Boolean.class);
        return Boolean.TRUE.equals(locked);
    }

    public UpsertOutcome upsert(SwaggerEndpointEntity e, Timestamp syncTime, String user) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("swaggerSourceId", e.getSwaggerSourceId())
                .addValue("apiGroup", e.getApiGroup())
                .addValue("httpMethod", e.getHttpMethod())
                .addValue("path", e.getPath())
                .addValue("fullUrl", e.getFullUrl())
                .addValue("operationId", e.getOperationId())
                .addValue("summary", e.getSummary())
                .addValue("description", e.getDescription(), Types.VARCHAR)
                .addValue("tags", e.getTags())
                .addValue("deprecated", e.getDeprecated() == null ? YesNoEnum.NO.getId() : e.getDeprecated().getId())
                .addValue("requestContentType", e.getRequestContentType())
                .addValue("parametersJson", e.getParametersJson(), Types.VARCHAR)
                .addValue("requestBodyJson", e.getRequestBodyJson(), Types.VARCHAR)
                .addValue("responsesJson", e.getResponsesJson(), Types.VARCHAR)
                .addValue("syncTime", syncTime)
                .addValue("user", user);
        return exdbSqlTemplate.queryForObject(SWAGGER_ENDPOINT_UPSERT_SQL, params, (rs, rowNum) -> {
            if (rs.getBoolean("inserted")) {
                return UpsertOutcome.INSERTED;
            }
            return rs.getBoolean("changed") ? UpsertOutcome.UPDATED : UpsertOutcome.UNCHANGED;
        });
    }

    /** Bu senkronizasyonda görülmeyen endpoint'leri CANCELLED yapar; etkilenen satır sayısını döner. */
    public int cancelMissing(UUID swaggerSourceId, Timestamp syncTime, String user) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("swaggerSourceId", swaggerSourceId)
                .addValue("syncTime", syncTime)
                .addValue("user", user);
        return exdbSqlTemplate.update(SWAGGER_ENDPOINT_CANCEL_MISSING_SQL, params);
    }
}
