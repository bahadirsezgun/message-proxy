package tr.com.tatmetal.proxy.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import tr.com.tatmetal.proxy.enums.SyncResultEnum;
import tr.com.tatmetal.proxy.swagger.SwaggerSyncResult;

/**
 * Endpoint'leri otomatik senkronize eder:
 * <ul>
 *   <li>Uygulama ayağa kalktığında (swagger.sync.on-startup=true)</li>
 *   <li>swagger.sync.cron zamanlamasıyla (varsayılan: her saat başı)</li>
 * </ul>
 * swagger.sync.enabled=false ile tamamen kapatılır (elle tetikleme API'si çalışmaya devam eder).
 */
@Component
@ConditionalOnProperty(name = "swagger.sync.enabled", havingValue = "true", matchIfMissing = true)
public class SwaggerSyncScheduler {

    private final SwaggerSyncService swaggerSyncService;
    private final boolean syncOnStartup;
    private final Logger logger = LoggerFactory.getLogger(SwaggerSyncScheduler.class);

    public SwaggerSyncScheduler(SwaggerSyncService swaggerSyncService,
                                @Value("${swagger.sync.on-startup:true}") boolean syncOnStartup) {
        this.swaggerSyncService = swaggerSyncService;
        this.syncOnStartup = syncOnStartup;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (syncOnStartup) {
            run("açılış");
        }
    }

    @Scheduled(cron = "${swagger.sync.cron:0 0 * * * *}")
    public void onSchedule() {
        run("zamanlanmış");
    }

    private void run(String trigger) {
        try {
            List<SwaggerSyncResult> results = swaggerSyncService.syncAllAuto();
            long failed = results.stream().filter(r -> r.getResult() == SyncResultEnum.ERROR && !r.isSkipped()).count();
            logger.info("Swagger senkronizasyonu ({}) bitti. kaynak={}, hatalı={}", trigger, results.size(), failed);
        } catch (Exception e) {
            logger.error("Swagger senkronizasyonu ({}) çalıştırılamadı. error={}", trigger, e.toString());
        }
    }
}
