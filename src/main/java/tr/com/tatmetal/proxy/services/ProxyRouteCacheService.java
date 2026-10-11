package tr.com.tatmetal.proxy.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import tr.com.tatmetal.proxy.dao.AppRegistryDaoImpl;
import tr.com.tatmetal.proxy.dao.ProxyFieldMapDaoImpl;
import tr.com.tatmetal.proxy.dao.ProxyRouteDaoImpl;
import tr.com.tatmetal.proxy.entity.AppRegistryEntity;
import tr.com.tatmetal.proxy.entity.ProxyFieldMapEntity;
import tr.com.tatmetal.proxy.entity.ProxyRouteEntity;
import tr.com.tatmetal.proxy.enums.HttpMethodEnum;
import tr.com.tatmetal.proxy.enums.MapDirectionEnum;
import tr.com.tatmetal.proxy.enums.YesNoEnum;
import tr.com.tatmetal.proxy.model.ProxyFieldMapDefinition;
import tr.com.tatmetal.proxy.model.ProxyRouteDefinition;
import tr.com.tatmetal.proxy.model.ProxyRouteMatch;

/**
 * Route + alan eşleştirme tanımlarını veritabanından okuyup Redis'e yazar; çalışma anında Redis'ten okur.
 * <p>
 * Redis anahtarları (prefix = proxy.cache.key-prefix):
 * <ul>
 *   <li>{prefix}:routes          HASH  "METOT /path" → route JSON (sabit path'ler, O(1) okuma)</li>
 *   <li>{prefix}:route-patterns  HASH  "METOT /path/{id}" → route JSON (değişken / joker içeren path'ler)</li>
 *   <li>{prefix}:loaded-at       STRING son yükleme zamanı; yoksa (Redis temizlendiyse) ilk istekte yeniden yüklenir</li>
 * </ul>
 * Yükleme geçici anahtarlara yazılıp RENAME ile değiştirilir; okuyan istekler yarım veri görmez.
 */
@Service
public class ProxyRouteCacheService {

    private static final String ANY = ProxyRouteDefinition.ANY_METHOD;

    private final ProxyRouteDaoImpl proxyRouteDao;
    private final ProxyFieldMapDaoImpl proxyFieldMapDao;
    private final AppRegistryDaoImpl appRegistryDao;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final Logger logger = LoggerFactory.getLogger(ProxyRouteCacheService.class);

    private final String routesKey;
    private final String patternsKey;
    private final String loadedAtKey;

    public ProxyRouteCacheService(ProxyRouteDaoImpl proxyRouteDao,
                                  ProxyFieldMapDaoImpl proxyFieldMapDao,
                                  AppRegistryDaoImpl appRegistryDao,
                                  StringRedisTemplate redisTemplate,
                                  ObjectMapper objectMapper,
                                  @Value("${proxy.cache.key-prefix:message-proxy}") String keyPrefix) {
        this.proxyRouteDao = proxyRouteDao;
        this.proxyFieldMapDao = proxyFieldMapDao;
        this.appRegistryDao = appRegistryDao;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.routesKey = keyPrefix + ":routes";
        this.patternsKey = keyPrefix + ":route-patterns";
        this.loadedAtKey = keyPrefix + ":loaded-at";
    }

    // ------------------------------------------------------------------
    // Yükleme
    // ------------------------------------------------------------------

    /** Uygulama ayağa kalktığında (schema.sql çalıştıktan sonra) tanımlar Redis'e yazılır. */
    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        try {
            int count = reload();
            logger.info("Proxy route tanımları Redis'e yüklendi. routeSayisi={}", count);
        } catch (Exception e) {
            // Redis/DB o an erişilemiyorsa uygulama yine açılır; ilk istekte tekrar denenir.
            logger.error("Proxy route tanımları Redis'e yüklenemedi; ilk istekte tekrar denenecek. error={}",
                    e.toString());
        }
    }

    /**
     * Aktif route ve eşleştirmeleri veritabanından okuyup Redis'teki tanımları tamamen yeniler.
     *
     * @return yüklenen route sayısı
     */
    public synchronized int reload() {
        List<ProxyRouteEntity> routes = proxyRouteDao.getActiveList();
        List<ProxyFieldMapEntity> fieldMaps = proxyFieldMapDao.getActiveList();
        Map<UUID, AppRegistryEntity> apps = new HashMap<>();
        for (AppRegistryEntity app : appRegistryDao.getNotCancelledList()) {
            apps.put(app.getId(), app);
        }

        Map<UUID, List<ProxyFieldMapEntity>> mapsByRoute = new HashMap<>();
        for (ProxyFieldMapEntity fieldMap : fieldMaps) {
            mapsByRoute.computeIfAbsent(fieldMap.getProxyRouteId(), k -> new ArrayList<>()).add(fieldMap);
        }

        Map<String, String> exact = new LinkedHashMap<>();
        Map<String, String> patterns = new LinkedHashMap<>();
        for (ProxyRouteEntity route : routes) {
            AppRegistryEntity targetApp = route.getTargetAppRegistryId() == null
                    ? null : apps.get(route.getTargetAppRegistryId());
            String targetUrl = resolveTargetUrl(route.getTargetUrl(), targetApp);
            if (targetUrl == null) {
                logger.warn("Route'un hedef adresi çözülemedi (göreli target_url, hedef uygulama yok ya da iptal), "
                        + "route yüklenmedi. routeCode={}, targetUrl={}, targetAppRegistryId={}",
                        route.getRouteCode(), route.getTargetUrl(), route.getTargetAppRegistryId());
                continue;
            }
            ProxyRouteDefinition definition = toDefinition(route, mapsByRoute.get(route.getId()));
            definition.setTargetUrl(targetUrl);
            String key = cacheKey(definition.getSourceMethod(), definition.getSourcePath());
            Map<String, String> target = isPattern(definition.getSourcePath()) ? patterns : exact;
            if (target.containsKey(key)) {
                logger.warn("Aynı path + metot için birden fazla aktif route var, ilki kullanılacak. key={}, routeCode={}",
                        key, definition.getRouteCode());
                continue;
            }
            target.put(key, toJson(definition));
        }

        replaceHash(routesKey, exact);
        replaceHash(patternsKey, patterns);
        redisTemplate.opsForValue().set(loadedAtKey, Instant.now().toString());
        return exact.size() + patterns.size();
    }

    private void replaceHash(String key, Map<String, String> values) {
        if (values.isEmpty()) {
            redisTemplate.delete(key);
            return;
        }
        String tmpKey = key + ":tmp:" + UUID.randomUUID();
        redisTemplate.opsForHash().putAll(tmpKey, values);
        redisTemplate.rename(tmpKey, key);
    }

    // ------------------------------------------------------------------
    // Okuma
    // ------------------------------------------------------------------

    /**
     * Gelen isteğin metot + path bilgisine göre route'u Redis'ten bulur.
     * Öncelik: sabit path + metot → sabit path + ANY → pattern path + metot → pattern path + ANY.
     */
    public Optional<ProxyRouteMatch> find(String method, String path) {
        ensureLoaded();
        String normalizedPath = normalizePath(path);
        String upperMethod = method == null ? ANY : method.toUpperCase();

        Object json = redisTemplate.opsForHash().get(routesKey, cacheKey(upperMethod, normalizedPath));
        if (json == null) {
            json = redisTemplate.opsForHash().get(routesKey, cacheKey(ANY, normalizedPath));
        }
        if (json != null) {
            return Optional.of(new ProxyRouteMatch(fromJson(json.toString()), null));
        }
        return findByPattern(upperMethod, normalizedPath);
    }

    private Optional<ProxyRouteMatch> findByPattern(String method, String path) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(patternsKey);
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        Comparator<String> specificity = pathMatcher.getPatternComparator(path);
        String bestPattern = null;
        String bestJson = null;
        boolean bestIsExactMethod = false;
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            String key = entry.getKey().toString();
            int space = key.indexOf(' ');
            String keyMethod = key.substring(0, space);
            String pattern = key.substring(space + 1);
            boolean exactMethod = keyMethod.equals(method);
            if (!(exactMethod || ANY.equals(keyMethod)) || !pathMatcher.match(pattern, path)) {
                continue;
            }
            boolean better = bestPattern == null
                    || (exactMethod && !bestIsExactMethod)
                    || (exactMethod == bestIsExactMethod && specificity.compare(pattern, bestPattern) < 0);
            if (better) {
                bestPattern = pattern;
                bestJson = entry.getValue().toString();
                bestIsExactMethod = exactMethod;
            }
        }
        if (bestPattern == null) {
            return Optional.empty();
        }
        return Optional.of(new ProxyRouteMatch(fromJson(bestJson),
                pathMatcher.extractUriTemplateVariables(bestPattern, path)));
    }

    /** Redis'teki tüm tanımlar (izleme / kontrol amaçlı). */
    public List<ProxyRouteDefinition> getAll() {
        ensureLoaded();
        List<ProxyRouteDefinition> result = new ArrayList<>();
        for (String key : List.of(routesKey, patternsKey)) {
            for (Object json : redisTemplate.opsForHash().values(key)) {
                result.add(fromJson(json.toString()));
            }
        }
        return result;
    }

    /** Redis yeniden başlatıldı / temizlendiyse tanımlar veritabanından tekrar yüklenir. */
    private void ensureLoaded() {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(loadedAtKey))) {
            return;
        }
        synchronized (this) {
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(loadedAtKey))) {
                logger.warn("Redis'te proxy route tanımları bulunamadı, veritabanından yükleniyor.");
                reload();
            }
        }
    }

    // ------------------------------------------------------------------
    // Dönüşümler
    // ------------------------------------------------------------------

    private ProxyRouteDefinition toDefinition(ProxyRouteEntity route, List<ProxyFieldMapEntity> fieldMaps) {
        ProxyRouteDefinition definition = new ProxyRouteDefinition();
        definition.setId(route.getId());
        definition.setRouteCode(route.getRouteCode());
        definition.setSourcePath(normalizePath(route.getSourcePath()));
        definition.setSourceMethod(route.getSourceMethod() == null
                || route.getSourceMethod() == HttpMethodEnum.PASS_THROUGH ? ANY : route.getSourceMethod().name());
        definition.setTargetUrl(route.getTargetUrl());
        definition.setTargetMethod(route.getTargetMethod() == null
                ? HttpMethodEnum.PASS_THROUGH.name() : route.getTargetMethod().name());
        definition.setConnectTimeoutMs(route.getConnectTimeoutMs() > 0 ? route.getConnectTimeoutMs() : 5000);
        definition.setReadTimeoutMs(route.getReadTimeoutMs() > 0 ? route.getReadTimeoutMs() : 30000);
        definition.setLogBody(route.getLogBody() == null || route.getLogBody() == YesNoEnum.YES);
        definition.setPassUnmappedFields(route.getPassUnmappedFields() == null
                || route.getPassUnmappedFields() == YesNoEnum.YES);

        if (fieldMaps != null) {
            for (ProxyFieldMapEntity fieldMap : fieldMaps) {
                ProxyFieldMapDefinition mapDefinition = new ProxyFieldMapDefinition(
                        fieldMap.getSourceField().trim(),
                        fieldMap.getTargetField().trim(),
                        fieldMap.getIsRequired() == YesNoEnum.YES,
                        fieldMap.getDefaultValue(),
                        fieldMap.getFieldOrder());
                if (fieldMap.getMapDirection() == MapDirectionEnum.RESPONSE) {
                    definition.getResponseFieldMaps().add(mapDefinition);
                } else {
                    definition.getRequestFieldMaps().add(mapDefinition);
                }
            }
        }
        Comparator<ProxyFieldMapDefinition> byOrder = Comparator.comparingInt(ProxyFieldMapDefinition::getFieldOrder);
        definition.getRequestFieldMaps().sort(byOrder);
        definition.getResponseFieldMaps().sort(byOrder);
        return definition;
    }

    private String toJson(ProxyRouteDefinition definition) {
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Route JSON'a çevrilemedi: " + definition.getRouteCode(), e);
        }
    }

    private ProxyRouteDefinition fromJson(String json) {
        try {
            return objectMapper.readValue(json, ProxyRouteDefinition.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Redis'teki route tanımı okunamadı", e);
        }
    }

    private String cacheKey(String method, String path) {
        return method + " " + path;
    }

    private boolean isPattern(String path) {
        return pathMatcher.isPattern(path);
    }

    /**
     * Route'un gerçek hedef adresi.
     * <ul>
     *   <li>{@code http://} veya {@code https://} ile başlıyorsa olduğu gibi kullanılır.</li>
     *   <li>Göreli ise ({@code /api/siparis/{id}}) hedef uygulamanın tam adresinin (base_url + base_path) sonuna eklenir.</li>
     *   <li>Göreli olup hedef uygulama yoksa {@code null} (route kullanılamaz).</li>
     * </ul>
     */
    public static String resolveTargetUrl(String targetUrl, AppRegistryEntity targetApp) {
        if (targetUrl == null || targetUrl.isBlank()) {
            return null;
        }
        String url = targetUrl.trim();
        if (isAbsoluteUrl(url)) {
            return url;
        }
        if (targetApp == null || targetApp.getFullBaseUrl() == null) {
            return null;
        }
        return targetApp.getFullBaseUrl() + (url.startsWith("/") ? url : "/" + url);
    }

    public static boolean isAbsoluteUrl(String url) {
        String lower = url.trim().toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /** Baştaki "/" garanti edilir, sondaki "/" kaldırılır (kök hariç). */
    public static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String result = path.trim();
        if (!result.startsWith("/")) {
            result = "/" + result;
        }
        while (result.length() > 1 && result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
