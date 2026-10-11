package tr.com.tatmetal.proxy.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tr.com.tatmetal.proxy.app.AppIntegration;
import tr.com.tatmetal.proxy.app.AppOverview;
import tr.com.tatmetal.proxy.app.AppRouteLink;
import tr.com.tatmetal.proxy.dao.AppRegistryDaoImpl;
import tr.com.tatmetal.proxy.dao.AuthProfileDaoImpl;
import tr.com.tatmetal.proxy.dao.ProxyRouteDaoImpl;
import tr.com.tatmetal.proxy.entity.AppRegistryEntity;
import tr.com.tatmetal.proxy.entity.ProxyRouteEntity;
import tr.com.tatmetal.proxy.entity.SwaggerSourceEntity;
import tr.com.tatmetal.proxy.enums.HttpMethodEnum;
import tr.com.tatmetal.proxy.enums.YesNoEnum;
import tr.com.tatmetal.proxy.swagger.SwaggerSyncResult;
import tr.com.zenergy.mini_jpa.util.StatusEnum;

/**
 * app_registry CRUD ve uygulamaya bağlı kayıtların yönetimi.
 * <ul>
 *   <li><b>Swagger:</b> uygulama asıl kaynaktır. has_swagger = 1 ise swagger_source otomatik oluşturulur /
 *       güncellenir (kod, adres, taban adres, token tanımı uygulamadan) ve hemen senkronize edilir; has_swagger = 0
 *       ise kaynak PASSIVE yapılır (endpoint'ler silinmez). Aynı kodlu, uygulamaya bağlı olmayan bir kaynak varsa
 *       yeni kaynak açılmaz, o kaynak uygulamaya bağlanır.</li>
 *   <li><b>Route'lar:</b> uygulama adresi değişince Redis yenilenir; göreli target_url'ler yeni adresle çözülür.</li>
 *   <li><b>Silme:</b> kayıt silinmez, CANCELLED yapılır. Uygulamaya bağlı iptal edilmemiş route varsa silinemez.</li>
 * </ul>
 */
@Service
public class AppRegistryService {

    public static final String DEFAULT_SWAGGER_PATH = "/swagger-ui/index.html";
    private static final String NONE = "-";

    private final AppRegistryDaoImpl appRegistryDao;
    private final AuthProfileDaoImpl authProfileDao;
    private final ProxyRouteDaoImpl proxyRouteDao;
    private final SwaggerSourceService swaggerSourceService;
    private final SwaggerSyncService swaggerSyncService;
    private final ProxyRouteCacheService proxyRouteCacheService;
    private final Logger logger = LoggerFactory.getLogger(AppRegistryService.class);

    public AppRegistryService(AppRegistryDaoImpl appRegistryDao, AuthProfileDaoImpl authProfileDao,
                              ProxyRouteDaoImpl proxyRouteDao, SwaggerSourceService swaggerSourceService,
                              SwaggerSyncService swaggerSyncService, ProxyRouteCacheService proxyRouteCacheService) {
        this.appRegistryDao = appRegistryDao;
        this.authProfileDao = authProfileDao;
        this.proxyRouteDao = proxyRouteDao;
        this.swaggerSourceService = swaggerSourceService;
        this.swaggerSyncService = swaggerSyncService;
        this.proxyRouteCacheService = proxyRouteCacheService;
    }

    // ------------------------------------------------------------------
    // CRUD
    // ------------------------------------------------------------------

    /** Kaydeder; has_swagger = 1 ise swagger kaynağı oluşturulup hemen senkronize edilir. */
    public AppRegistryEntity save(AppRegistryEntity entity) {
        prepare(entity);
        AppRegistryEntity saved = appRegistryDao.save(entity);
        manageSwaggerSource(saved, true);
        return saved;
    }

    public List<AppRegistryEntity> saveAll(List<AppRegistryEntity> entities) {
        List<AppRegistryEntity> result = new ArrayList<>();
        for (AppRegistryEntity entity : entities) {
            result.add(save(entity));
        }
        return result;
    }

    /**
     * Günceller; swagger kaynağı uygulamaya göre güncellenir (adres değiştiyse hemen senkronize edilir) ve
     * route'ların hedef adresleri yeniden çözülsün diye Redis yenilenir.
     */
    public AppRegistryEntity update(AppRegistryEntity entity) {
        AppRegistryEntity existing = requireExisting(entity);
        if (entity.getStatus() == StatusEnum.CANCELLED) {
            throw new IllegalArgumentException("Uygulamayı iptal etmek için DELETE /api/v1/app-registry kullanın");
        }
        prepare(entity);
        boolean addressChanged = !Objects.equals(existing.getSwaggerUrl(), entity.getSwaggerUrl())
                || !Objects.equals(existing.getAuthProfileId(), entity.getAuthProfileId());
        AppRegistryEntity updated = appRegistryDao.update(entity);
        manageSwaggerSource(updated, addressChanged);
        refreshRouteCache();
        return updated;
    }

    /**
     * Uygulamayı iptal eder (status = CANCELLED; kayıt silinmez). Uygulamaya bağlı iptal edilmemiş route
     * varsa izin verilmez. Swagger kaynağı PASSIVE yapılır, endpoint'leri kalır.
     */
    public AppRegistryEntity delete(AppRegistryEntity entity) {
        AppRegistryEntity existing = requireExisting(entity);
        List<ProxyRouteEntity> routes = proxyRouteDao.getByAppRegistryId(existing.getId());
        if (!routes.isEmpty()) {
            throw new IllegalArgumentException("Uygulamaya bağlı route'lar var, önce bunları kaldırın ya da başka "
                    + "uygulamaya bağlayın: " + routes.stream().map(ProxyRouteEntity::getRouteCode)
                    .collect(Collectors.joining(", ")));
        }
        SwaggerSourceEntity source = swaggerSourceService.getByAppRegistryId(existing.getId());
        if (source != null && source.getStatus() == StatusEnum.ACTIVE) {
            source.setStatus(StatusEnum.PASSIVE);
            swaggerSourceService.updateForApp(source, false);
        }
        existing.setStatus(StatusEnum.CANCELLED);
        AppRegistryEntity cancelled = appRegistryDao.update(existing);
        refreshRouteCache();
        return cancelled;
    }

    public AppRegistryEntity getOne(UUID id, UUID etag) {
        return appRegistryDao.getOne(id, etag);
    }

    public AppRegistryEntity getById(UUID id) {
        return appRegistryDao.getById(id);
    }

    public List<AppRegistryEntity> getByCondition(Map<String, Object> params) {
        return appRegistryDao.getByCondition(params == null ? new HashMap<>() : params);
    }

    /** Uygulamanın swagger'ını hemen senkronize eder; uygulama yoksa null. */
    public SwaggerSyncResult sync(UUID id) {
        AppRegistryEntity app = appRegistryDao.getById(id);
        if (app == null) {
            return null;
        }
        SwaggerSourceEntity source = swaggerSourceService.getByAppRegistryId(id);
        if (app.getHasSwagger() != YesNoEnum.YES || source == null) {
            throw new IllegalArgumentException("Uygulamada swagger tanımlı değil (hasSwagger = 0): " + app.getAppCode());
        }
        return swaggerSyncService.sync(source);
    }

    // ------------------------------------------------------------------
    // Genel bakış (entegrasyon haritası)
    // ------------------------------------------------------------------

    /** Tek uygulamanın genel bakışı; uygulama yoksa null. */
    public AppOverview overview(UUID id) {
        AppRegistryEntity app = appRegistryDao.getById(id);
        if (app == null) {
            return null;
        }
        Map<UUID, AppRegistryEntity> apps = appMap();
        apps.put(app.getId(), app);
        return buildOverview(app, proxyRouteDao.getByAppRegistryId(id), apps);
    }

    /** İptal edilmemiş tüm uygulamaların genel bakışı. */
    public List<AppOverview> overviewAll() {
        Map<UUID, AppRegistryEntity> apps = appMap();
        List<ProxyRouteEntity> linkedRoutes = proxyRouteDao.getAppLinkedList();
        List<AppOverview> result = new ArrayList<>();
        for (AppRegistryEntity app : apps.values()) {
            List<ProxyRouteEntity> routes = linkedRoutes.stream()
                    .filter(r -> app.getId().equals(r.getSourceAppRegistryId())
                            || app.getId().equals(r.getTargetAppRegistryId()))
                    .collect(Collectors.toList());
            result.add(buildOverview(app, routes, apps));
        }
        return result;
    }

    private AppOverview buildOverview(AppRegistryEntity app, List<ProxyRouteEntity> routes,
                                      Map<UUID, AppRegistryEntity> apps) {
        AppOverview overview = new AppOverview();
        overview.setApp(app);
        overview.setSwaggerSource(swaggerSourceService.getByAppRegistryId(app.getId()));
        Map<String, AppIntegration> outgoing = new LinkedHashMap<>();
        Map<String, AppIntegration> incoming = new LinkedHashMap<>();
        for (ProxyRouteEntity route : routes) {
            AppRouteLink link = toLink(route, apps.get(route.getTargetAppRegistryId()));
            if (app.getId().equals(route.getSourceAppRegistryId())) {
                add(outgoing, route.getTargetAppRegistryId(), apps, link);
            }
            if (app.getId().equals(route.getTargetAppRegistryId())) {
                add(incoming, route.getSourceAppRegistryId(), apps, link);
            }
        }
        overview.setOutgoing(new ArrayList<>(outgoing.values()));
        overview.setIncoming(new ArrayList<>(incoming.values()));
        return overview;
    }

    private void add(Map<String, AppIntegration> target, UUID otherAppId, Map<UUID, AppRegistryEntity> apps,
                     AppRouteLink link) {
        String key = otherAppId == null ? NONE : otherAppId.toString();
        AppIntegration integration = target.computeIfAbsent(key, k -> {
            AppIntegration i = new AppIntegration();
            AppRegistryEntity other = otherAppId == null ? null : apps.get(otherAppId);
            i.setAppRegistryId(otherAppId);
            i.setAppCode(other == null ? NONE : other.getAppCode());
            i.setAppName(other == null
                    ? (otherAppId == null ? "Tanımsız (route'ta uygulama seçilmemiş)" : "Bulunamadı / iptal")
                    : other.getAppName());
            return i;
        });
        integration.getRoutes().add(link);
        integration.setRouteCount(integration.getRoutes().size());
    }

    private AppRouteLink toLink(ProxyRouteEntity route, AppRegistryEntity targetApp) {
        AppRouteLink link = new AppRouteLink();
        link.setProxyRouteId(route.getId());
        link.setRouteCode(route.getRouteCode());
        link.setSourceMethod(route.getSourceMethod() == null || route.getSourceMethod() == HttpMethodEnum.PASS_THROUGH
                ? "ANY" : route.getSourceMethod().name());
        link.setSourcePath(route.getSourcePath());
        link.setTargetUrl(route.getTargetUrl());
        link.setResolvedTargetUrl(ProxyRouteCacheService.resolveTargetUrl(route.getTargetUrl(), targetApp));
        link.setStatus(route.getStatus() == null ? null : route.getStatus().name());
        return link;
    }

    private Map<UUID, AppRegistryEntity> appMap() {
        Map<UUID, AppRegistryEntity> apps = new LinkedHashMap<>();
        for (AppRegistryEntity app : appRegistryDao.getNotCancelledList()) {
            apps.put(app.getId(), app);
        }
        return apps;
    }

    // ------------------------------------------------------------------
    // Swagger kaynağı yönetimi
    // ------------------------------------------------------------------

    /**
     * Uygulamaya göre swagger_source'u oluşturur / günceller / PASSIVE yapar.
     *
     * @param sync oluşturulan / adresi değişen kaynak hemen senkronize edilsin mi
     */
    private void manageSwaggerSource(AppRegistryEntity app, boolean sync) {
        if (app == null || app.getId() == null) {
            return;
        }
        SwaggerSourceEntity source = swaggerSourceService.getByAppRegistryId(app.getId());
        if (app.getHasSwagger() != YesNoEnum.YES || app.getStatus() == StatusEnum.CANCELLED) {
            if (source != null && source.getStatus() == StatusEnum.ACTIVE) {
                source.setStatus(StatusEnum.PASSIVE);
                swaggerSourceService.updateForApp(source, false);
                logger.info("Uygulamanın swagger'ı kapatıldı, kaynak PASSIVE yapıldı. app={}", app.getAppCode());
            }
            return;
        }
        if (source == null) {
            source = adoptableSourceByCode(app);
        }
        boolean isNew = source == null;
        if (isNew) {
            source = new SwaggerSourceEntity();
            source.setAutoSync(YesNoEnum.YES);
        }
        boolean reactivated = !isNew && source.getStatus() != StatusEnum.ACTIVE;
        source.setSourceCode(app.getAppCode());
        source.setSourceDescription(limit(app.getAppName(), 150));
        source.setSwaggerUrl(app.getSwaggerUrl());
        source.setTargetBaseUrl(app.getFullBaseUrl());
        source.setAuthProfileId(app.getAuthProfileId());
        source.setAppRegistryId(app.getId());
        source.setStatus(StatusEnum.ACTIVE);
        if (isNew) {
            swaggerSourceService.saveForApp(source);
            logger.info("Uygulama için swagger kaynağı oluşturuldu. app={}, url={}", app.getAppCode(), app.getSwaggerUrl());
        } else {
            swaggerSourceService.updateForApp(source, sync || reactivated);
        }
    }

    /**
     * Aynı kodlu, iptal edilmemiş bir kaynak varsa ve başka (aktif) bir uygulamaya bağlı değilse onu döner
     * (uygulama o kaynağı sahiplenir). Başka uygulamaya bağlıysa hata.
     */
    private SwaggerSourceEntity adoptableSourceByCode(AppRegistryEntity app) {
        SwaggerSourceEntity byCode = swaggerSourceService.getBySourceCode(app.getAppCode());
        if (byCode == null) {
            return null;
        }
        if (byCode.getAppRegistryId() == null || byCode.getAppRegistryId().equals(app.getId())
                || appRegistryDao.getById(byCode.getAppRegistryId()) == null) {
            return byCode;
        }
        throw new IllegalArgumentException("'" + app.getAppCode() + "' kodlu swagger kaynağı başka bir uygulamaya bağlı");
    }

    // ------------------------------------------------------------------
    // Doğrulama
    // ------------------------------------------------------------------

    private AppRegistryEntity requireExisting(AppRegistryEntity entity) {
        if (entity.getId() == null || entity.getEtag() == null) {
            throw new IllegalArgumentException("id ve etag zorunludur");
        }
        AppRegistryEntity existing = appRegistryDao.getOne(entity.getId(), entity.getEtag());
        if (existing == null || existing.getStatus() == StatusEnum.CANCELLED) {
            throw new IllegalArgumentException("Uygulama bulunamadı ya da etag güncel değil: " + entity.getId());
        }
        return existing;
    }

    private void prepare(AppRegistryEntity entity) {
        if (isBlank(entity.getAppCode())) {
            throw new IllegalArgumentException("appCode zorunludur");
        }
        if (isBlank(entity.getAppName())) {
            throw new IllegalArgumentException("appName zorunludur");
        }
        entity.setAppCode(entity.getAppCode().trim());
        entity.setAppName(entity.getAppName().trim());
        if (isBlank(entity.getBaseUrl())) {
            throw new IllegalArgumentException("baseUrl zorunludur (ör. http://localhost:7261)");
        }
        String baseUrl = trimTrailingSlash(entity.getBaseUrl().trim());
        if (!isHttpUrl(baseUrl)) {
            throw new IllegalArgumentException("baseUrl http:// veya https:// ile başlamalıdır");
        }
        entity.setBaseUrl(baseUrl);
        entity.setBasePath(normalizePath(entity.getBasePath()));
        if (entity.getHasSwagger() == null) {
            entity.setHasSwagger(YesNoEnum.NO);
        }
        if (isBlank(entity.getSwaggerPath()) || NONE.equals(entity.getSwaggerPath().trim())) {
            entity.setSwaggerPath(DEFAULT_SWAGGER_PATH);
        } else {
            String swaggerPath = entity.getSwaggerPath().trim();
            if (isHttpUrl(swaggerPath)) {
                throw new IllegalArgumentException("swaggerPath tam adres değil, base path'e göre yol olmalıdır "
                        + "(ör. /swagger-ui/index.html veya /v3/api-docs)");
            }
            entity.setSwaggerPath(swaggerPath.startsWith("/") ? swaggerPath : "/" + swaggerPath);
        }
        if (isBlank(entity.getAppOwner())) {
            entity.setAppOwner(NONE);
        }
        if (entity.getAuthProfileId() != null && authProfileDao.getById(entity.getAuthProfileId()) == null) {
            throw new IllegalArgumentException("authProfileId ile token tanımı bulunamadı ya da iptal edilmiş: "
                    + entity.getAuthProfileId());
        }
        if (entity.getHasSwagger() == YesNoEnum.YES) {
            String swaggerUrl = entity.getSwaggerUrl();
            if (swaggerUrl.length() > 500) {
                throw new IllegalArgumentException("Swagger adresi 500 karakteri geçemez: " + swaggerUrl);
            }
            // Kod çakışmasını uygulama kaydedilmeden önce yakala (sonradan yarım kayıt kalmasın)
            if (entity.getId() == null || swaggerSourceService.getByAppRegistryId(entity.getId()) == null) {
                adoptableSourceByCode(entity);
            }
        }
    }

    /** "/erp/" → "/erp", "erp" → "/erp", boş → "-". */
    static String normalizePath(String path) {
        if (path == null || path.isBlank() || NONE.equals(path.trim())) {
            return NONE;
        }
        String result = trimTrailingSlash(path.trim());
        if (result.isEmpty()) {
            return NONE;
        }
        return result.startsWith("/") ? result : "/" + result;
    }

    private void refreshRouteCache() {
        try {
            proxyRouteCacheService.reload();
        } catch (Exception e) {
            logger.error("Uygulama değişikliği sonrası Redis yenilenemedi. error={}", e.toString());
        }
    }

    private static boolean isHttpUrl(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
