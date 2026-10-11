package tr.com.tatmetal.proxy.services;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import tr.com.tatmetal.proxy.auth.AuthTokenException;
import tr.com.tatmetal.proxy.dao.AuthProfileDaoImpl;
import tr.com.tatmetal.proxy.dao.SwaggerEndpointDaoImpl;
import tr.com.tatmetal.proxy.dao.SwaggerEndpointDaoImpl.UpsertOutcome;
import tr.com.tatmetal.proxy.dao.SwaggerSourceDaoImpl;
import tr.com.tatmetal.proxy.entity.AuthProfileEntity;
import tr.com.tatmetal.proxy.entity.SwaggerEndpointEntity;
import tr.com.tatmetal.proxy.entity.SwaggerSourceEntity;
import tr.com.tatmetal.proxy.enums.SyncResultEnum;
import tr.com.tatmetal.proxy.swagger.SwaggerDocLoader;
import tr.com.tatmetal.proxy.swagger.SwaggerDocument;
import tr.com.tatmetal.proxy.swagger.SwaggerSpecParser;
import tr.com.tatmetal.proxy.swagger.SwaggerSyncResult;
import tr.com.tatmetal.proxy.swagger.SwaggerUnauthorizedException;

/**
 * Tablo bağımsız servis: swagger_source kaydındaki adresten swagger/OpenAPI dokümanını okur ve
 * endpoint'leri swagger_endpoint tablosuna yazar.
 * <ul>
 *   <li>Yeni endpoint → eklenir; değişen endpoint → güncellenir (etag yenilenir); aynısı → dokunulmaz.</li>
 *   <li>Dokümandan kalkan endpoint → silinmez, CANCELLED yapılır (counter_of_unique_data artırılır).</li>
 *   <li>Doküman okunamaz ya da boş gelirse mevcut kayıtlara dokunulmaz; hata swagger_source'a yazılır.</li>
 *   <li>Kaynağa auth_profile bağlıysa doküman okunmadan önce token alınır ve tüm doküman isteklerine
 *       {@code Authorization} header'ı eklenir; doküman 401/403 dönerse token yenilenip bir kez daha denenir.</li>
 *   <li>Tüm yazma işlemi tek transaction'dadır ve kaynak bazında kilitlidir (aynı anda iki senkronizasyon çalışmaz).</li>
 * </ul>
 */
@Service
public class SwaggerSyncService {

    public static final String SYNC_USER = "swagger-sync";
    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final SwaggerSourceDaoImpl swaggerSourceDao;
    private final SwaggerEndpointDaoImpl swaggerEndpointDao;
    private final SwaggerDocLoader swaggerDocLoader;
    private final SwaggerSpecParser swaggerSpecParser;
    private final AuthProfileDaoImpl authProfileDao;
    private final AuthTokenService authTokenService;
    private final TransactionTemplate transactionTemplate;
    private final Logger logger = LoggerFactory.getLogger(SwaggerSyncService.class);

    public SwaggerSyncService(SwaggerSourceDaoImpl swaggerSourceDao,
                              SwaggerEndpointDaoImpl swaggerEndpointDao,
                              SwaggerDocLoader swaggerDocLoader,
                              SwaggerSpecParser swaggerSpecParser,
                              AuthProfileDaoImpl authProfileDao,
                              AuthTokenService authTokenService,
                              @Qualifier("exDb") DataSource exdbDataSource) {
        this.authProfileDao = authProfileDao;
        this.authTokenService = authTokenService;
        this.swaggerSourceDao = swaggerSourceDao;
        this.swaggerEndpointDao = swaggerEndpointDao;
        this.swaggerDocLoader = swaggerDocLoader;
        this.swaggerSpecParser = swaggerSpecParser;
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(exdbDataSource));
    }

    /** id ile senkronizasyon; kaynak yoksa (ya da CANCELLED ise) null döner. */
    public SwaggerSyncResult sync(UUID swaggerSourceId) {
        SwaggerSourceEntity source = swaggerSourceDao.getById(swaggerSourceId);
        return source == null ? null : sync(source);
    }

    /** ACTIVE ve auto_sync = 1 olan tüm kaynakları sırayla senkronize eder. Bir kaynağın hatası diğerlerini durdurmaz. */
    public List<SwaggerSyncResult> syncAllAuto() {
        List<SwaggerSyncResult> results = new ArrayList<>();
        List<SwaggerSourceEntity> sources = swaggerSourceDao.getAutoSyncSources();
        if (sources == null) {
            return results;
        }
        for (SwaggerSourceEntity source : sources) {
            results.add(sync(source));
        }
        return results;
    }

    public SwaggerSyncResult sync(SwaggerSourceEntity source) {
        long start = System.nanoTime();
        SwaggerSyncResult result = new SwaggerSyncResult();
        result.setSwaggerSourceId(source.getId());
        result.setSourceCode(source.getSourceCode());

        Timestamp syncTime = null;
        try {
            // 1) Dokümanları oku ve endpoint'lere dönüştür (HTTP; transaction dışında)
            List<SwaggerDocument> documents = loadDocuments(source);
            Map<String, SwaggerEndpointEntity> endpoints = new LinkedHashMap<>();
            for (SwaggerDocument document : documents) {
                result.getDocumentUrls().add(document.getDocumentUri().toString());
                for (SwaggerEndpointEntity endpoint : swaggerSpecParser.parse(document, source.getTargetBaseUrl())) {
                    endpoint.setSwaggerSourceId(source.getId());
                    endpoints.putIfAbsent(endpoint.getApiGroup() + " " + endpoint.getHttpMethod() + " "
                            + endpoint.getPath(), endpoint);
                }
            }
            if (endpoints.isEmpty()) {
                throw new IllegalStateException("Dokümanda hiç endpoint bulunamadı; mevcut kayıtlara dokunulmadı. "
                        + "Okunan doküman(lar): " + result.getDocumentUrls());
            }
            result.setEndpointCount(endpoints.size());

            // 2) Tek transaction'da yaz
            syncTime = transactionTemplate.execute(status -> write(source, endpoints.values(), result));

            result.setResult(SyncResultEnum.SUCCESS);
            result.setMessage(String.format("%d endpoint: %d yeni, %d güncellenen, %d değişmeyen, %d kaldırılan (CANCELLED)",
                    result.getEndpointCount(), result.getInsertedCount(), result.getUpdatedCount(),
                    result.getUnchangedCount(), result.getCancelledCount()));
        } catch (SyncInProgressException e) {
            result.setSkipped(true);
            result.setResult(source.getLastSyncResult());
            result.setMessage(e.getMessage());
            result.setDurationMs(elapsedMs(start));
            logger.info("Swagger senkronizasyonu atlandı. source={}, reason={}", source.getSourceCode(), e.getMessage());
            return result;
        } catch (Exception e) {
            result.setResult(SyncResultEnum.ERROR);
            result.setMessage(truncate(e.getMessage() == null ? e.toString() : e.getMessage()));
            result.setInsertedCount(0);
            result.setUpdatedCount(0);
            result.setUnchangedCount(0);
            result.setCancelledCount(0);
            logger.error("Swagger senkronizasyonu başarısız. source={}, url={}, error={}",
                    source.getSourceCode(), source.getSwaggerUrl(), e.toString());
        }
        result.setDurationMs(elapsedMs(start));
        saveResult(source, syncTime, result);
        if (result.getResult() == SyncResultEnum.SUCCESS) {
            logger.info("Swagger senkronizasyonu tamamlandı. {}", result);
        }
        return result;
    }

    /**
     * Dokümanları okur. Kaynağa token tanımı bağlıysa Authorization header'ı eklenir; doküman token'ı kabul
     * etmezse (401/403) bellekteki token silinir, yeni token alınır ve bir kez daha denenir.
     */
    private List<SwaggerDocument> loadDocuments(SwaggerSourceEntity source) {
        if (source.getAuthProfileId() == null) {
            return swaggerDocLoader.load(source.getSwaggerUrl());
        }
        AuthProfileEntity profile = authProfileDao.getById(source.getAuthProfileId());
        if (profile == null) {
            throw new IllegalStateException("Kaynağa bağlı token tanımı bulunamadı ya da iptal edilmiş. authProfileId="
                    + source.getAuthProfileId());
        }
        try {
            return swaggerDocLoader.load(source.getSwaggerUrl(), authorization(profile));
        } catch (SwaggerUnauthorizedException e) {
            logger.warn("Swagger dokümanı token'ı kabul etmedi, token yenilenip tekrar denenecek. source={}, profile={}",
                    source.getSourceCode(), profile.getProfileCode());
            authTokenService.invalidate(profile.getId());
            return swaggerDocLoader.load(source.getSwaggerUrl(), authorization(profile));
        }
    }

    private Map<String, String> authorization(AuthProfileEntity profile) {
        try {
            return Map.of("Authorization", authTokenService.getAuthorizationHeader(profile));
        } catch (AuthTokenException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /** Transaction içinde çalışır; senkronizasyon zamanını döner. */
    private Timestamp write(SwaggerSourceEntity source, Iterable<SwaggerEndpointEntity> endpoints,
                            SwaggerSyncResult result) {
        if (!swaggerEndpointDao.tryLockSource(source.getId())) {
            throw new SyncInProgressException("Bu kaynak için başka bir senkronizasyon sürüyor");
        }
        Timestamp syncTime = swaggerEndpointDao.currentSyncTime();
        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        for (SwaggerEndpointEntity endpoint : endpoints) {
            UpsertOutcome outcome = swaggerEndpointDao.upsert(endpoint, syncTime, SYNC_USER);
            if (outcome == UpsertOutcome.INSERTED) {
                inserted++;
            } else if (outcome == UpsertOutcome.UPDATED) {
                updated++;
            } else {
                unchanged++;
            }
        }
        int cancelled = swaggerEndpointDao.cancelMissing(source.getId(), syncTime, SYNC_USER);
        result.setInsertedCount(inserted);
        result.setUpdatedCount(updated);
        result.setUnchangedCount(unchanged);
        result.setCancelledCount(cancelled);
        return syncTime;
    }

    /** Sonucu swagger_source'a yazar; buradaki hata senkronizasyon sonucunu değiştirmez. */
    private void saveResult(SwaggerSourceEntity source, Timestamp syncTime, SwaggerSyncResult result) {
        try {
            Timestamp time = syncTime != null ? syncTime : swaggerEndpointDao.currentSyncTime();
            Integer endpointCount = result.getResult() == SyncResultEnum.SUCCESS ? result.getEndpointCount() : null;
            swaggerSourceDao.updateSyncResult(source.getId(), time, result.getResult(), result.getMessage(),
                    endpointCount);
        } catch (Exception e) {
            logger.error("Senkronizasyon sonucu swagger_source'a yazılamadı. source={}, error={}",
                    source.getSourceCode(), e.toString());
        }
    }

    private static String truncate(String value) {
        return value.length() <= MAX_MESSAGE_LENGTH ? value : value.substring(0, MAX_MESSAGE_LENGTH);
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private static final class SyncInProgressException extends RuntimeException {
        SyncInProgressException(String message) {
            super(message);
        }
    }
}
