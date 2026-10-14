package tr.com.tatmetal.proxy.dao;

import static tr.com.tatmetal.proxy.dao.sql.AppRegistrySql.APP_REGISTRY_GET_BY_ID_SQL;
import static tr.com.tatmetal.proxy.dao.sql.AppRegistrySql.APP_REGISTRY_GET_NOT_CANCELLED_SQL;
import static tr.com.tatmetal.proxy.dao.sql.AppRegistrySql.APP_REGISTRY_GET_ONE_SQL;
import static tr.com.tatmetal.proxy.dao.sql.AppRegistrySql.APP_REGISTRY_GET_SQL;

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
import tr.com.tatmetal.proxy.entity.AppRegistryEntity;

@Repository
public class AppRegistryDaoImpl {

    private final ZenergyNamedJdbcTemplate<AppRegistryEntity> exdbJdbcTemplate;
    private Logger logger = LoggerFactory.getLogger(AppRegistryDaoImpl.class);

    public AppRegistryDaoImpl(@Qualifier("exdbJdbcTemplate")
            ZenergyNamedJdbcTemplate<AppRegistryEntity> exdbJdbcTemplate) {
        this.exdbJdbcTemplate = exdbJdbcTemplate;
    }

    @ExtendsBaseEntity
    public List<AppRegistryEntity> saveAll(List<AppRegistryEntity> entity) {
        return exdbJdbcTemplate.saveAll(entity);
    }

    @ExtendsBaseEntity
    public AppRegistryEntity save(AppRegistryEntity entity) {
        return exdbJdbcTemplate.save(entity);
    }

    @ExtendsBaseEntity
    public AppRegistryEntity update(AppRegistryEntity entity) {
        return exdbJdbcTemplate.update(entity);
    }

    public AppRegistryEntity delete(AppRegistryEntity entity) {
        return exdbJdbcTemplate.delete(entity);
    }

    public AppRegistryEntity getOne(UUID id, UUID etag) {
        try {
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("id", id);
            queryMap.put("etag", etag);
            return exdbJdbcTemplate.findEntity(APP_REGISTRY_GET_ONE_SQL, queryMap,
                    AppRegistryEntity.class);
        } catch (Exception e) {
            logger.error(e.toString());
            return null;
        }
    }

    public List<AppRegistryEntity> getByCondition(Map<String, Object> params) {
        try {
            return exdbJdbcTemplate.findEntityList(APP_REGISTRY_GET_SQL +
                    ParamsToCondition.convertParamToCondition(params, AppRegistryEntity.class, true),
                    params, AppRegistryEntity.class);
        } catch (Exception e) {
            logger.error(e.toString());
            return null;
        }
    }

    public AppRegistryEntity getById(UUID id) {
        try {
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("id", id);
            return exdbJdbcTemplate.findEntity(APP_REGISTRY_GET_BY_ID_SQL, queryMap,
                    AppRegistryEntity.class);
        } catch (Exception e) {
            logger.error(e.toString());
            return null;
        }
    }

    /** İptal edilmemiş tüm uygulamalar. Hata durumunda exception fırlatır (Redis'e eksik adres yazılmasın). */
    public List<AppRegistryEntity> getNotCancelledList() {
        try {
            return exdbJdbcTemplate.findEntityList(APP_REGISTRY_GET_NOT_CANCELLED_SQL, new HashMap<>(),
                    AppRegistryEntity.class);
        } catch (Exception e) {
            logger.error(e.toString());
            throw new IllegalStateException("Uygulama kayıtları okunamadı: " + e.getMessage(), e);
        }
    }
}
