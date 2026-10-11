# Message Proxy (TatMetal)

Gelen REST isteklerini, veritabanında tanımlı kurallara göre başka bir sisteme REST ile ileten proxy servis.

- Gelen isteğin **path + HTTP metodu** hangi hedefe gidileceğini (route) belirler.
- Gelen gövdedeki alan isimleri hedef sistemin beklediği isimlere **eşleştirilir** (mapping). Gerekirse hedefin cevabı da geri dönerken eşleştirilir.
- Route ve eşleştirme tanımları PostgreSQL'de tutulur. Uygulama açılışta bunları **Redis**'e yükler, isteklerde sadece Redis'ten okur.
- Hedefin cevabı (status, header, gövde) isteği gönderen sisteme aynen döner.
- Her iletim `proxy_message_log` tablosuna kaydedilir.
- Ayrıca entegre olunan servislerin **swagger dokümanları okunup** tüm endpoint'leri `swagger_endpoint` tablosuna yazılır ve güncel tutulur (bkz. [Swagger'dan endpoint senkronizasyonu](#swaggerdan-endpoint-senkronizasyonu)).

---

## İçindekiler

1. [Teknoloji](#teknoloji)
2. [Hızlı başlangıç](#hızlı-başlangıç)
3. [Çalışma mantığı](#çalışma-mantığı)
4. [Paket yapısı](#paket-yapısı)
5. [Sınıf referansı](#sınıf-referansı)
6. [Veritabanı tabloları](#veritabanı-tabloları)
7. [Redis önbellek](#redis-önbellek)
8. [Route eşleşme kuralları](#route-eşleşme-kuralları)
9. [Alan eşleştirme (mapping)](#alan-eşleştirme-mapping)
10. [Swagger'dan endpoint senkronizasyonu](#swaggerdan-endpoint-senkronizasyonu)
11. [REST API referansı](#rest-api-referansı)
12. [Hata cevapları](#hata-cevapları)
13. [Loglama](#loglama)
14. [Konfigürasyon](#konfigürasyon)
15. [Yeni bir entegrasyon tanımlama (adım adım)](#yeni-bir-entegrasyon-tanımlama-adım-adım)
16. [Geliştirici notları](#geliştirici-notları)
17. [Sorun giderme](#sorun-giderme)

---

## Teknoloji

| Bileşen | Sürüm / Not |
|---|---|
| Java | 17 |
| Spring Boot | 2.6.4 |
| Spring Cloud | 2021.0.1 (OpenFeign + Apache HttpClient 5) |
| Veritabanı | PostgreSQL 13+ (JDBC, kurum içi `mini_jpa` 0.0.341) |
| Önbellek | Redis (`spring-boot-starter-data-redis` / Lettuce, sürüm Boot BOM'undan; `StringRedisTemplate` ile kullanılır) |
| Güvenlik | Kurum içi `exss-service` 0.0.67 |
| Lombok | Kullanılmaz |

Proje parametreleri (standart dokümanına göre):

| Parametre | Değer |
|---|---|
| Kök paket | `tr.com.tatmetal.proxy` |
| Şema | `message_proxy` |
| JdbcTemplate qualifier | `exdbJdbcTemplate` |

---

## Hızlı başlangıç

**Gerekenler:** PostgreSQL 13+, Redis, Maven, kurum içi Maven reposuna erişim (`mini_jpa`, `exss-service`).

```bash
# 1) PostgreSQL ve Redis ayarlarını application.properties içinde kontrol edin
# 2) Uygulamayı başlatın
mvn spring-boot:run
```

Açılışta sırasıyla şunlar olur:

1. `schema.sql` çalışır; şema, tablolar ve yeni kolonlar yoksa oluşturulur.
2. `ProxyRouteCacheService` aktif route ve eşleştirmeleri veritabanından okuyup Redis'e yazar.
3. Uygulama `/proxy/**` altındaki istekleri karşılamaya başlar.

Hızlı deneme için [Yeni bir entegrasyon tanımlama](#yeni-bir-entegrasyon-tanımlama-adım-adım) bölümüne bakın.

---

## Çalışma mantığı

```mermaid
sequenceDiagram
    participant C as İstemci sistem
    participant G as ProxyGatewayResources
    participant S as ProxyGatewayService
    participant R as ProxyRouteCacheService
    participant M as ProxyFieldMappingService
    participant F as ProxyForwardService
    participant T as Hedef sistem
    participant L as ProxyMessageLogService

    C->>G: POST /proxy/erp/order
    G->>S: handle(POST, "/erp/order", ...)
    S->>R: find(POST, "/erp/order")
    R-->>S: route + eşleştirmeler (Redis)
    S->>M: map(gövde, REQUEST eşleştirmeleri)
    M-->>S: hedef formatında gövde
    S->>F: forward(route, hedef URI, gövde)
    F->>T: HTTP isteği (OpenFeign)
    T-->>F: cevap
    F->>L: record(...) → proxy_message_log
    F-->>S: cevap
    S->>M: map(cevap, RESPONSE eşleştirmeleri) [sadece 2xx]
    S-->>C: status + header + gövde
```

Adım adım:

| # | Adım | Sorumlu sınıf | Hata durumunda |
|---|---|---|---|
| 1 | `/proxy` prefix'i çıkarılır, kalan path bulunur | `ProxyGatewayResources` | — |
| 2 | Path + metot ile route Redis'ten bulunur | `ProxyRouteCacheService` | 404 `ROUTE_NOT_FOUND` |
| 3 | Hedef URL hazırlanır (`{id}` değişkenleri + query string) | `ProxyGatewayService` | 500 `INVALID_TARGET_URL` |
| 4 | REQUEST eşleştirmeleri gövdeye uygulanır | `ProxyFieldMappingService` | 400 `REQUEST_MAPPING_ERROR` |
| 5 | İstek hedefe iletilir | `ProxyForwardService` + `ProxyForwardClient` | 502 / 504 |
| 6 | İletim loglanır | `ProxyMessageLogService` | Log hatası iletimi bozmaz |
| 7 | 2xx JSON cevaba RESPONSE eşleştirmeleri uygulanır | `ProxyFieldMappingService` | 502 `RESPONSE_MAPPING_ERROR` |
| 8 | Cevap çağırana döner | `ProxyGatewayService` | — |

Hedefin 4xx/5xx cevapları hata sayılıp değiştirilmez; status, header ve gövde aynen döner.

---

## Paket yapısı

```
tr.com.tatmetal.proxy
├── MessageProxyApplication          Uygulama giriş noktası
├── api.v1.resources                 REST API katmanı        (*Resources)
├── services                         Service katmanı         (*Service)
├── entity                           Entity katmanı          (*Entity, BaseEntity'den extend)
├── dao                              Dao katmanı             (*DaoImpl)
│   └── sql                          SQL sabitleri           (*Sql)
├── enums                            IEnumNumber enum'ları   (standart dışı ek paket)
├── model                            Redis'te tutulan tanım nesneleri (standart dışı ek paket)
├── exception                        Uygulama exception'ları (standart dışı ek paket)
├── client                           OpenFeign client'ları   (standart dışı ek paket)
├── swagger                          Swagger dokümanı okuma/parse (standart dışı ek paket)
└── config                           Spring konfigürasyonları (standart dışı ek paket)
```

Tablo bazlı sınıf isimlendirmesi (ör. `proxy_field_map` tablosu):

| Katman | Sınıf |
|---|---|
| Entity | `ProxyFieldMapEntity` |
| Sql | `ProxyFieldMapSql` |
| Dao | `ProxyFieldMapDaoImpl` |
| Service | `ProxyFieldMapService` |
| Rest Api | `ProxyFieldMapResources` |

---

## Sınıf referansı

### Uygulama

#### `MessageProxyApplication`
Spring Boot giriş noktası.
- `tr.com.tatmetal.proxy` ile birlikte `tr.com.zenergy.mini_jpa` paketini de tarar. Böylece `@ExtendsBaseEntity` aspect'i gibi `mini_jpa` bileşenleri bean olarak yüklenir.
- `@EnableFeignClients` ile `client` paketindeki Feign client'larını etkinleştirir.
- `@EnableScheduling` ile zamanlanmış görevleri açar (`SwaggerSyncScheduler`).

---

### `api.v1.resources` — REST API katmanı

#### `ProxyGatewayResources`
Proxy'nin **giriş kapısı**. `${proxy.gateway.prefix}/**` (varsayılan `/proxy/**`) altındaki GET, POST, PUT, PATCH ve DELETE isteklerini karşılar.
- Prefix'i path'ten çıkarır. Örnek: `/proxy/erp/order` → `/erp/order`.
- Header'ları, query string'i, ham gövdeyi (`byte[]`) ve istemci IP'sini toplar. IP için önce `X-Forwarded-For`, yoksa `remoteAddr` kullanılır.
- İşin geri kalanını `ProxyGatewayService.handle(...)`'a devreder.

#### `ProxyRouteResources`
`/api/v1/proxy-route`: route tanımlarının CRUD işlemleri ve Redis önbellek yönetimi. Her kayıt değişikliğinden sonra Redis otomatik yenilenir. Uç noktalar için [REST API referansı](#rest-api-referansı)'na bakın.

#### `ProxyFieldMapResources`
`/api/v1/proxy-field-map`: alan eşleştirme tanımlarının CRUD işlemleri.
- Hatalı alan yolu (örneğin `[]` sayısı tutmuyor) `ProxyMappingException` olarak yakalanır ve **400 `VALIDATION_ERROR`** döner.
- Eksik `proxyRouteId` `IllegalArgumentException` olarak yakalanır, yine 400 döner.

#### `ProxyMessageLogResources`
`/api/v1/proxy-message-log`: iletim loglarını **salt okunur** sorgular. Kayıtları `ProxyForwardService` ve `ProxyGatewayService` yazar.

#### `SwaggerSourceResources`
`/api/v1/swagger-source`: swagger kaynaklarının CRUD işlemleri ve elle senkronizasyon (`/{id}/sync`, `/sync`).
- `IllegalArgumentException` (eksik `sourceCode`, geçersiz `swaggerUrl`) yakalanır ve **400** `{"message": "..."}` döner.
- `/{id}/sync` kaynak yoksa veya CANCELLED ise 404 döner.

#### `SwaggerEndpointResources`
`/api/v1/swagger-endpoint`: okunan endpoint'leri sorgular. Ekleme ve silme yoktur; `PUT` sadece `status`'u (ör. PASSIVE) değiştirmek için kullanılır.

---

### `services` — Service katmanı

#### `ProxyGatewayService`
Proxy akışının **orkestratörü**. `handle(method, path, queryString, clientIp, headers, body)` şunları yapar:
1. `ProxyRouteCacheService.find` ile route'u bulur. Yoksa 404, Redis/DB okunamazsa 503 döner.
2. `X-Correlation-Id` header'ı yoksa üretir. Bu değer hedefe de, cevapta çağırana da gönderilir.
3. `target_url` içindeki `{değişken}`'leri gelen path'ten doldurur (URL-encode ederek) ve gelen query string'i hedefe ekler.
4. REQUEST eşleştirmelerini uygular. Hata olursa 400 döner ve `MAPPING_ERROR` logu yazar; istek hedefe **gönderilmez**.
5. `ProxyForwardService.forward(...)`'u çağırır. Bağlantı hatasında 502, zaman aşımında 504 döner.
6. Cevap 2xx ise ve RESPONSE eşleştirmesi tanımlıysa cevabı dönüştürür. Hata olursa 502 döner.
7. Cevabı döndürür. Gövde değişmiş olabileceği için `Content-Length` ve hop-by-hop header'lar taşınmaz.

Eşleştirme sadece JSON gövdelere uygulanır: Content-Type `json` içeriyorsa ya da Content-Type yokken gövde `{` veya `[` ile başlıyorsa. Diğer içerikler (XML, form, binary) dokunulmadan iletilir.

#### `ProxyRouteCacheService`
Route + eşleştirme tanımlarının **Redis önbelleğini** yönetir.
- **`loadOnStartup()`**: `ApplicationReadyEvent` ile, yani `schema.sql` çalıştıktan sonra tetiklenir ve `reload()` çağırır. Redis veya DB o an erişilemezse uygulama yine açılır, hata loglanır; tanımlar ilk istekte tekrar yüklenmeye çalışılır.
- **`reload()`**: aktif route'ları ve eşleştirmeleri DB'den okur, `ProxyRouteDefinition` nesnelerine çevirip JSON olarak Redis'e yazar.
  - Veri önce geçici bir anahtara yazılır, sonra `RENAME` ile asıl anahtarın yerine geçer. Böylece okuyan istekler yarım veri görmez.
  - Aynı path + metot için birden fazla aktif route varsa ilki kullanılır ve uyarı loglanır.
- **`find(method, path)`**: route'u Redis'ten bulur. Sıralama için [Route eşleşme kuralları](#route-eşleşme-kuralları)'na bakın. Sonuç `ProxyRouteMatch` olarak döner: route ve path değişkenleri.
- **`ensureLoaded()`**: Redis'te `{prefix}:loaded-at` anahtarı yoksa (Redis yeniden başladı ya da temizlendi) tanımları DB'den otomatik yeniden yükler.
- **`getAll()`**: Redis'teki tüm tanımları döner. İzleme amaçlı kullanılır.
- **`normalizePath(path)`** (static): path'in başına `/` ekler, sonundaki `/`'ı kaldırır. Kayıt sırasında da kullanılır, böylece `erp/order/` ve `/erp/order` aynı route'a gider.

#### `ProxyFieldMappingService`
JSON **alan eşleştirme motoru**. Jackson `JsonNode` üzerinde çalışır.
- **`map(source, fieldMaps, passUnmappedFields)`**: kaynağı eşleştirme listesine göre dönüştürür.
  - Gövde en üstte bir diziyse (`[{...},{...}]`) her elemana ayrı uygulanır.
  - Tüm değerler önce değiştirilmemiş kaynaktan okunur, sonra yazılır. Bu sayede `a→b`, `b→a` gibi takaslar doğru çalışır.
  - `passUnmappedFields = true`: kaynak kopyalanır; eşleştirilen alanlar eski yerinden silinir, yeni yerine yazılır. Silme sonrası boş kalan ara nesneler de temizlenir.
  - `passUnmappedFields = false`: hedef boş başlar, sadece eşleştirilen alanlar yazılır.
  - İç içe hedef yolundaki ara nesneler yoksa oluşturulur.
- **`validate(sourceField, targetField)`** (static): alan yolu söz dizimini kontrol eder. Hem kayıt sırasında (`ProxyFieldMapService`) hem çalışma anında kullanılır.

Söz dizimi için [Alan eşleştirme](#alan-eşleştirme-mapping) bölümüne bakın.

#### `ProxyForwardService`
Eşleştirmesi yapılmış isteği **hedefe iletir** ve iletimi loglar. Tablodan bağımsızdır.
- **`forward(route, target, incomingMethod, requestPath, clientIp, headers, sourceBody, body)`**:
  - Hedef metodu belirler. Route'ta `PASS_THROUGH` ise gelen metot kullanılır.
  - Hop-by-hop header'ları (`Host`, `Content-Length`, `Connection`, ...) hedefe iletmez.
  - Route bazlı connect/read timeout'ları `Request.Options` ile çağrı anında uygular.
  - Hedef 4xx/5xx dönerse `FeignException`'ı exception olarak bırakmaz, `ResponseEntity`'e çevirir. Böylece cevap çağırana aynen döner.
  - Bağlantı hatası veya timeout'ta (`status <= 0`) log yazar ve exception'ı fırlatır. Gateway bunu 502 ya da 504'e çevirir.
  - Retry yoktur; idempotent olmayan çağrılar (POST vb.) çift gitmez.
- `CORRELATION_ID_HEADER = "X-Correlation-Id"` sabiti diğer sınıflarca da kullanılır.

#### `ProxyMessageLogService`
`proxy_message_log` tablosuna **iletim kaydı** yazar.
- **`record(...)`**: normal iletim kaydı. `result_type` HTTP status'tan hesaplanır.
- **`recordMappingError(...)`**: eşleştirme hatası kaydı (`result_type = MAPPING_ERROR`).
- `Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie` ve `X-Api-Key` header değerleri `******` olarak maskelenir.
- Gövdeler sadece `proxy_route.log_body = 1` ise yazılır ve `proxy.log.max-body-length` karakterde kesilir.
- Log yazımındaki hata **asla iletimi bozmaz**; sadece uygulama loguna düşer.
- Standart `save`, `getOne` ve `getByCondition` metotları da bulunur.

#### `ProxyRouteService`
`proxy_route` için CRUD.
- Kayıt öncesi `source_path`'i normalize eder.
- Her değişiklikten sonra `ProxyRouteCacheService.reload()` çağırır. Redis hatası kaydı geri almaz, sadece loglanır.

#### `ProxyFieldMapService`
`proxy_field_map` için CRUD.
- Kayıt öncesi `proxyRouteId` zorunluluğunu ve alan yolu söz dizimini doğrular, alan yollarındaki boşlukları kırpar.
- Her değişiklikten sonra Redis'i yeniler.

#### `SwaggerSyncService`
Swagger senkronizasyonunun **orkestratörü**. Tablodan bağımsızdır.
- **`sync(source)`**: dokümanı `SwaggerDocLoader` ile okur, `SwaggerSpecParser` ile endpoint'lere çevirir, tek transaction'da upsert + CANCELLED işlemini yapar, sonucu `swagger_source`'a yazar ve `SwaggerSyncResult` döner. Exception fırlatmaz; her hata sonuca `ERROR` olarak yazılır.
- **`sync(id)`**: kaynağı `id` ile bulur (CANCELLED hariç). Yoksa `null`.
- **`syncAllAuto()`**: `status = 3` ve `auto_sync = 1` olan kaynakları sırayla senkronize eder.
- Transaction `exDb` üzerinde `TransactionTemplate` ile açılır. HTTP okuma transaction dışındadır.
- Kilit alınamazsa (başka senkronizasyon sürüyor) `skipped = true` döner ve `swagger_source`'a yazmaz.
- Yazan kullanıcı olarak `user_who_recorded` / `user_who_last_updated` alanlarına `swagger-sync` yazılır.

#### `SwaggerSyncScheduler`
Otomatik senkronizasyon. `swagger.sync.enabled=false` ise bean hiç oluşmaz.
- **`onStartup()`**: `ApplicationReadyEvent` ile (yani `schema.sql` çalıştıktan sonra) `swagger.sync.on-startup=true` ise `syncAllAuto()` çağırır.
- **`onSchedule()`**: `swagger.sync.cron` zamanlamasıyla `syncAllAuto()` çağırır.
- Hata uygulamayı durdurmaz, sadece loglanır.

#### `SwaggerSourceService`
`swagger_source` için CRUD.
- Kayıt öncesi `sourceCode` ve `swaggerUrl` zorunluluğunu, `swaggerUrl`'in `http(s)://` ile başladığını kontrol eder; boş alanlara tablo varsayılanlarını (`-`, `autoSync = 1`, `lastSyncResult = NEVER`) yazar.
- **`save`**: kayıttan sonra `autoSync` 0 değilse hemen senkronize eder ve kaydı son hâliyle (`lastSync*` dolu) döner. Okuma hatası kaydı engellemez.
- `saveAll` ve `update` senkronizasyon başlatmaz.

#### `SwaggerEndpointService`
`swagger_endpoint` için okuma (`getOne`, `getByCondition`) ve `update`. Ekleme/silme bilerek yoktur; kayıtları `SwaggerSyncService` yönetir.

---

### `client` — OpenFeign

#### `ProxyForwardClient`
Dinamik hedeflere istek atan **genel Feign client**.
- Her metotta bir `URI` parametresi vardır. Bu parametre client'ın varsayılan url'ini (`proxy.forward.default-url`) çağrı bazında ezer, böylece tek client ile her hedefe gidilir.
- `Request.Options` parametresi timeout'u çağrı bazında belirler.
- Gövde `byte[]` olarak taşınır; içerik dönüştürülmez.
- Metotlar: `get`, `post`, `put`, `patch`, `delete`. PATCH desteği Apache HttpClient 5 (`feign-hc5`) ile gelir.

#### `SwaggerDocClient`
Swagger dokümanlarını okumak için genel Feign client (`get(URI, Request.Options, headers)`). `ProxyForwardClient` gibi hedef adresi `URI` parametresiyle çağrı bazında alır. Timeout'lar `swagger.sync.connect-timeout-ms` / `read-timeout-ms` ile belirlenir. `ProxyFeignConfig`'i kullanır (retry yok).

Sabit adresli başka bir sisteme çağrı gerekirse bu paketa ayrı bir `@FeignClient(name = "...", url = "${...}")` eklenmelidir.

---

### `config` — Konfigürasyon

#### `JdbcConfig`
Veri kaynaklarını ve `mini_jpa` template'lerini tanımlar.

| Bean | Açıklama |
|---|---|
| `exDb` (`@Primary`) | `datasource.exdb.*` ayarlarından oluşan ana DataSource. `schema.sql` bunun üzerinde çalışır. |
| `exdbJdbcTemplate` (`@Primary`) | Dao'ların `@Qualifier("exdbJdbcTemplate")` ile kullandığı `ZenergyNamedJdbcTemplate` |
| `pgDb` | `datasource.pgdb.*` ayarlarından ikinci DataSource |
| `pgJdbcTemplate` | İkinci DataSource için template |

> İkinci DataSource **`@Primary` olmamalıdır.** İki `@Primary` DataSource olursa Spring `schema.sql`'i çalıştırmaz.

#### `ProxyFeignConfig`
Sadece `ProxyForwardClient`'a özel Feign ayarları: Retry kapalı (`Retryer.NEVER_RETRY`) ve log seviyesi `BASIC`.
Bilerek `@Configuration` **değildir**; aksi halde ayarlar tüm Feign client'larına uygulanırdı.

---

### `entity` — Entity katmanı

Tüm entity'ler `mini_jpa` `BaseEntity`'den extend eder. Standart alanlar (`id`, `id_serial`, `etag`, `status`, `date_of_recorded`, ...) oradan gelir.

| Sınıf | Tablo | Açıklama |
|---|---|---|
| `ProxyRouteEntity` | `proxy_route` | Gelen path/metot → hedef tanımı |
| `ProxyFieldMapEntity` | `proxy_field_map` | Route'a bağlı kaynak alan → hedef alan eşleştirmesi |
| `ProxyMessageLogEntity` | `proxy_message_log` | İletim kaydı. `toString()` gövde ve header'ları bilerek yazmaz. |
| `SwaggerSourceEntity` | `swagger_source` | Swagger adresi okunacak servis ve son senkronizasyon sonucu |
| `SwaggerEndpointEntity` | `swagger_endpoint` | Swagger'dan okunan endpoint. `toString()` JSON kolonlarını yazmaz. |

Kolon detayları için [Veritabanı tabloları](#veritabanı-tabloları) bölümüne bakın.

---

### `dao` ve `dao.sql` — Veri erişim katmanı

Her Dao `@Repository`'dir ve `exdbJdbcTemplate` kullanır. Standart metotlar:

| Metot | Açıklama |
|---|---|
| `save`, `saveAll`, `update` | `@ExtendsBaseEntity` ile standart alanlar otomatik doldurulur |
| `delete` | `mini_jpa` delete; standarda göre kayıt silinmez, `status = 1 (CANCELLED)` yapılır |
| `getOne(id, etag)` | Tek kayıt |
| `getByCondition(params)` | `ParamsToCondition` ile dinamik WHERE. Örnek: `{"routeCode": "ERP_ORDER"}` |

Ek metotlar:

| Sınıf | Metot | SQL sabiti | Açıklama |
|---|---|---|---|
| `ProxyRouteDaoImpl` | `getActiveList()` | `ProxyRouteSql.PROXY_ROUTE_GET_ACTIVE_SQL` | `status = 3` ve `source_path <> '-'` olan route'lar |
| `ProxyFieldMapDaoImpl` | `getActiveList()` | `ProxyFieldMapSql.PROXY_FIELD_MAP_GET_ACTIVE_SQL` | Aktif route'lara ait aktif eşleştirmeler, `field_order` sırasıyla |
| `ProxyMessageLogDaoImpl` | — | — | Sadece standart metotlar |
| `SwaggerSourceDaoImpl` | `getById(id)` | `SwaggerSourceSql.SWAGGER_SOURCE_GET_BY_ID_SQL` | etag'siz tekil okuma, CANCELLED hariç |
| `SwaggerSourceDaoImpl` | `getAutoSyncSources()` | `SwaggerSourceSql.SWAGGER_SOURCE_GET_AUTO_SYNC_SQL` | `status = 3` ve `auto_sync = 1` |
| `SwaggerSourceDaoImpl` | `updateSyncResult(...)` | `SwaggerSourceSql.SWAGGER_SOURCE_UPDATE_SYNC_RESULT_SQL` | `last_sync_*` ve `endpoint_count` yazar; etag'e dokunmaz, `endpoint_count` null gelirse eskisini korur |
| `SwaggerEndpointDaoImpl` | `currentSyncTime()` | `SwaggerEndpointSql.SWAGGER_ENDPOINT_SYNC_TIME_SQL` | Veritabanı saati (`LOCALTIMESTAMP`) |
| `SwaggerEndpointDaoImpl` | `tryLockSource(id)` | `SwaggerEndpointSql.SWAGGER_ENDPOINT_TRY_LOCK_SQL` | Kaynak bazlı transaction kilidi (`pg_try_advisory_xact_lock`) |
| `SwaggerEndpointDaoImpl` | `upsert(...)` | `SwaggerEndpointSql.SWAGGER_ENDPOINT_UPSERT_SQL` | `INSERT ... ON CONFLICT uk1 DO UPDATE`; `INSERTED` / `UPDATED` / `UNCHANGED` döner |
| `SwaggerEndpointDaoImpl` | `cancelMissing(...)` | `SwaggerEndpointSql.SWAGGER_ENDPOINT_CANCEL_MISSING_SQL` | Bu çalışmada görülmeyenleri CANCELLED yapar, sayısını döner |

`getActiveList()` diğer okuma metotlarından farklı olarak hata durumunda `null` dönmez, exception fırlatır. Böylece Redis'e eksik veri yazılmaz.

Swagger Dao'larındaki `upsert`, `cancelMissing`, `tryLockSource` ve `updateSyncResult` entity'ye bağlı olmayan SQL'ler olduğu için `exDb` üzerinde Spring `NamedParameterJdbcTemplate` ile çalışır; `upsert`, `cancelMissing` ve `tryLockSource` transaction içinde çağrılmalıdır.

`*Sql` sınıfları (`ProxyRouteSql`, `ProxyFieldMapSql`, `ProxyMessageLogSql`, `SwaggerSourceSql`, `SwaggerEndpointSql`) sadece `public static final String` SQL sabitleri içerir.

---

### `swagger` — Swagger dokümanı okuma

Veritabanına dokunmayan, swagger dokümanını bulma ve çözümleme sınıfları.

| Sınıf | Açıklama |
|---|---|
| `SwaggerDocLoader` | **`load(swaggerUrl)`**: verilen adresten doküman(lar)ı bulur ve okur; en az bir `SwaggerDocument` döner, bulamazsa denenen adreslerle birlikte `IllegalStateException` fırlatır. Sıra için [Swagger adresinin çözülmesi](#swagger-adresinin-çözülmesi)'ne bakın. JSON ve YAML okur. |
| `SwaggerSpecParser` | **`parse(document, baseUrlOverride)`**: OpenAPI 3.x / Swagger 2.0 dokümanını `SwaggerEndpointEntity` listesine çevirir (`swaggerSourceId` hariç). Kurallar için [Endpoint'e dönüştürme](#endpointe-dönüştürme)'ye bakın. |
| `SwaggerDocument` | Okunmuş doküman: grup adı, okunduğu adres (göreli `servers` adreslerini çözmek için) ve JSON kökü. |
| `SwaggerSyncResult` | Bir kaynağın senkronizasyon özeti; API cevabı olarak döner. Alanlar için [Senkronizasyon sonucu](#senkronizasyon-sonucu)'na bakın. |

---

### `model` — Redis tanım nesneleri

Redis'te JSON olarak tutulan, entity'den bağımsız sade nesneler. Böylece önbellek içeriği `mini_jpa`/`BaseEntity` yapısından etkilenmez.

| Sınıf | Açıklama |
|---|---|
| `ProxyRouteDefinition` | Bir route'un çalışma anında gereken tüm bilgisi, REQUEST ve RESPONSE eşleştirme listeleriyle birlikte. Metotlar metin olarak tutulur: kaynakta `ANY`/`GET`/`POST`..., hedefte `PASS_THROUGH`/`GET`/... |
| `ProxyFieldMapDefinition` | Tek eşleştirme: `sourceField`, `targetField`, `required`, `defaultValue`, `fieldOrder` |
| `ProxyRouteMatch` | `find` sonucu: bulunan route ve path'ten çıkarılan değişkenler. Örnek: `/erp/order/{id}` + `/erp/order/42` → `{id=42}` |

---

### `enums`

Hepsi `IEnumNumber` uygular ve DB'de `smallint` olarak saklanır. JSON'da hem `id` (`2`) hem isim (`"POST"`) ile gönderilebilirler.

| Enum | Değerler | Kullanıldığı yer |
|---|---|---|
| `HttpMethodEnum` | 0 `PASS_THROUGH`, 1 `GET`, 2 `POST`, 3 `PUT`, 4 `PATCH`, 5 `DELETE` | `proxy_route.source_method` (0 = tüm metotlar), `proxy_route.target_method` (0 = gelen metot) |
| `MapDirectionEnum` | 1 `REQUEST`, 2 `RESPONSE` | `proxy_field_map.map_direction` |
| `YesNoEnum` | 0 `NO`, 1 `YES` (JSON'da `true`/`false` da kabul edilir) | `log_body`, `pass_unmapped_fields`, `is_required`, `auto_sync`, `deprecated` |
| `ProxyResultEnum` | 1 `SUCCESS`, 2 `TARGET_ERROR`, 3 `CONNECTION_ERROR`, 4 `MAPPING_ERROR` | `proxy_message_log.result_type` |
| `SyncResultEnum` | 0 `NEVER`, 1 `SUCCESS`, 2 `ERROR` | `swagger_source.last_sync_result`, `SwaggerSyncResult.result` |

---

### `exception`

| Sınıf | Açıklama |
|---|---|
| `ProxyMappingException` | Eşleştirme yapılamadığında (zorunlu alan yok, dizi beklenen yerde dizi yok, hatalı tanım) fırlatılır. Gateway'de 400/502'ye, field-map API'sinde 400'e çevrilir. |

---

## Veritabanı tabloları

`src/main/resources/schema.sql` uygulama açılışında çalışır ve idempotent'tir: var olan tabloya dokunmaz, eksik kolonları `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` ile ekler.

**Standart (Paketleme Veritabanı ile aynı):**
- Her tabloda `<BASE_COLUMNS>` bulunur: `id`, `id_serial`, `etag`, `status`, `date_of_recorded`, `user_who_recorded`, `date_of_last_updated`, `user_who_last_updated`, `counter_of_unique_data`.
- Anahtarlar:
  - `PRIMARY KEY (id)`
  - `uk0_<tablo> UNIQUE (id_serial)`
  - İş anahtarı: `ukN_<tablo> UNIQUE (<alanlar>, counter_of_unique_data)`
  - Yabancı anahtar: `fkN_<tablo> FOREIGN KEY (...) REFERENCES message_proxy.<tablo>(id)`
- `status` değerleri: 1 CANCELLED, 2 PASSIVE, 3 ACTIVE, 4 CLOSED. Kayıt silinmez; `status = 1` yapılır ve `counter_of_unique_data` `message_proxy.counter_of_unique_data_seq` ile artırılır.

Aşağıdaki tablolarda `<BASE_COLUMNS>` dışındaki tüm kolonlar anlatılır. Çalışma anında kullanılmayan, sadece bilgi amaçlı kolonlar ayrıca belirtilmiştir.

### `proxy_route` — Route tanımları

Her satır bir entegrasyon uç noktasıdır: "şu path'e şu metotla gelen istek şu adrese iletilsin".

| Kolon | Tip | Zorunlu / Varsayılan | Açıklama | Örnek / geçerli değerler |
|---|---|---|---|---|
| `route_code` | varchar(50) | Zorunlu | Route'un benzersiz iş kodu (`uk1`). Uygulama loglarında ve `proxy_message_log.route_code`'da görünür; bir isteği hangi kuralın karşıladığını bulmak için kullanılır. | `ERP_ORDER_CREATE` |
| `route_description` | varchar(150) | `-` | Route'un ne iş yaptığını anlatan serbest metin. **Sadece bilgi amaçlıdır**, çalışma anında kullanılmaz. | `ERP sipariş oluşturma` |
| `source_path` | varchar(500) | `-` | İstemcinin çağırdığı path, **`/proxy` ön eki olmadan**. `/` ile başlar, sonda `/` olmaz (API ile kaydederken düzeltilir). Büyük/küçük harfe duyarlıdır. `{ad}` tek bir path parçasını değişken olarak yakalar; `*` ve `**` joker olarak kullanılabilir. **`-` bırakılırsa route Redis'e yüklenmez**, yani hiçbir istekle eşleşmez. | `/erp/order`, `/erp/order/{id}`, `/ifs/{firma}/part/{kod}` |
| `source_method` | smallint | 0 | Route'un hangi HTTP metoduyla gelen isteği karşıladığı. Aynı path için hem 0 hem belirli metot tanımlıysa belirli metot önceliklidir. | 0 tüm metotlar, 1 GET, 2 POST, 3 PUT, 4 PATCH, 5 DELETE |
| `target_url` | varchar(500) | Zorunlu | Hedef sistemin tam adresi (şema, host, port, path). `source_path`'teki `{ad}` değişkenleri aynı adla kullanılabilir, değerleri URL-encode edilerek yerine yazılır. Gelen query string sona eklenir (`target_url`'de `?` varsa `&` ile). `source_path`'te olmayan bir değişken kullanılırsa adres geçersiz olur ve istek 500 `INVALID_TARGET_URL` ile döner. | `http://erp.local:8081/api/siparis/{id}` |
| `target_method` | smallint | 0 | Hedefe hangi metotla gidileceği. 0 ise gelen metot aynen kullanılır. **1 (GET) seçilirse gövde gönderilmez.** | 0 gelen metot, 1 GET, 2 POST, 3 PUT, 4 PATCH, 5 DELETE |
| `connect_timeout_ms` | integer | 5000 | Hedefe TCP bağlantısı kurmak için beklenecek en uzun süre (ms). Aşılırsa istemciye 502/504 döner, log'a `CONNECTION_ERROR` yazılır. 0 veya negatif ise 5000 kullanılır. | `3000` |
| `read_timeout_ms` | integer | 30000 | Bağlantı kurulduktan sonra hedefin cevabı için beklenecek en uzun süre (ms). Aşılırsa istemciye 504 `TARGET_TIMEOUT` döner. 0 veya negatif ise 30000 kullanılır. Uzun süren işlemler için artırın. | `60000` |
| `log_body` | smallint | 1 | Bu route'un istek/cevap gövdeleri `proxy_message_log`'a yazılsın mı. Büyük veya hassas veri taşıyan route'larda 0 yapın; header, status, süre gibi diğer bilgiler yine loglanır. | 0 Hayır, 1 Evet |
| `pass_unmapped_fields` | smallint | 1 | `proxy_field_map`'te tanımı olmayan alanlar ne olsun. 1: aynen hedefe aktarılır, sadece eşleştirilen alanların adı/yeri değişir. 0: sadece eşleştirilen alanlar gönderilir, diğerleri atılır. Hem REQUEST hem RESPONSE eşleştirmesine uygulanır. Route'un hiç eşleştirmesi yoksa etkisi yoktur, gövde aynen gider. | 0 Hayır, 1 Evet |

Anahtarlar:
- `uk1_proxy_route (route_code, counter_of_unique_data)`: aynı kodla iki aktif route olamaz.
- `uk2_proxy_route (source_path, source_method, counter_of_unique_data)`: aynı path + metot için iki aktif route olamaz (`source_path = '-'` olanlar hariç).

### `proxy_field_map` — Alan eşleştirmeleri

Her satır bir route için tek bir alan dönüşümüdür: "kaynaktaki şu alan, hedefte şu ad/yerde olsun".

| Kolon | Tip | Zorunlu / Varsayılan | Açıklama | Örnek / geçerli değerler |
|---|---|---|---|---|
| `proxy_route_id` | uuid | Zorunlu | Eşleştirmenin ait olduğu route (`fk1` → `proxy_route.id`). Route aktif değilse (status ≠ 3) eşleştirmeleri de yüklenmez. | `3f1c2b7e-...` |
| `map_direction` | smallint | 1 | Eşleştirmenin hangi yönde uygulanacağı. REQUEST: istemcinin gövdesi hedefe gitmeden önce. RESPONSE: hedefin cevabı istemciye dönmeden önce; sadece 2xx JSON cevaplarda çalışır. | 1 REQUEST, 2 RESPONSE |
| `source_field` | varchar(250) | Zorunlu | Değerin okunacağı alan yolu. REQUEST'te istemcinin gövdesindeki, RESPONSE'ta hedefin cevabındaki alandır. Nokta iç içe alanı, `[]` dizinin her elemanını ifade eder. İndeks (`[0]`) desteklenmez. Büyük/küçük harfe duyarlıdır. | `customerName`, `customer.tax`, `items[].code`, `tags[]` |
| `target_field` | varchar(250) | Zorunlu | Değerin yazılacağı alan yolu. `source_field` ile **aynı sayıda `[]`** içermelidir. Ara nesneler yoksa oluşturulur. Aynı route ve yönde iki satır aynı hedef alana yazamaz (`uk1`). | `musteri.ad`, `musteri.vergiNo`, `kalemler[].urunKodu`, `etiketler[]` |
| `is_required` | smallint | 0 | Kaynakta alan yoksa ne olacağı. 1 ve `default_value` boşsa: REQUEST'te istek hedefe **gönderilmez**, istemciye 400 döner; RESPONSE'ta istemciye 502 döner. 0 ise alan hedefte oluşturulmaz. Dizi alanlarında (`items[].code`) kontrol her eleman için ayrı yapılır; bir eşleştirme zorunluysa dizinin kendisi de zorunlu sayılır. | 0 Hayır, 1 Evet |
| `default_value` | varchar(500) | null | Kaynakta alan yoksa hedefe yazılacak değer. Dolu olduğunda `is_required` kontrolü yapılmaz. JSON olarak yorumlanır: `10` sayı, `true` boolean, `"WEB"` metin, `{}` nesne. Geçerli JSON değilse (`WEB`) metin kabul edilir. Dizi elemanı alanlarında her eleman için ayrı uygulanır. Sadece dizi adı değiştiren satırlarda (`tags[]`) kullanılmaz. | `1`, `"WEB"`, `true`, `null` |
| `field_order` | integer | 0 | Eşleştirmelerin işlenme ve hedefe yazılma sırası (küçükten büyüğe). Değerler önce okunup sonra yazıldığı için çoğu durumda sonucu değiştirmez; hedef JSON'daki alan sırasını etkiler. İki eşleştirme iç içe aynı yere yazıyorsa (`a → x`, `b → x.y`) sonradan yazan kazanır. | `1`, `2`, `3` |
| `map_description` | varchar(150) | `-` | Eşleştirmenin açıklaması. **Sadece bilgi amaçlıdır**, çalışma anında kullanılmaz. | `Müşteri adı ERP formatına` |

Anahtar: `uk1_proxy_field_map (proxy_route_id, map_direction, target_field, counter_of_unique_data)`.

### `proxy_message_log` — İletim kayıtları

Bu tablo elle doldurulmaz. Her iletimde `ProxyForwardService`, eşleştirme hatalarında `ProxyGatewayService` tarafından yazılır. Route bulunamayan (404) istekler bu tabloya yazılmaz.

| Kolon | Tip | Açıklama | Örnek |
|---|---|---|---|
| `proxy_route_id` | uuid | İsteği karşılayan route (`fk1` → `proxy_route.id`). | `3f1c2b7e-...` |
| `route_code` | varchar(50) | Route kodu. Route sonradan değişse de log anlaşılır kalsın diye kopyalanır. | `ERP_ORDER_CREATE` |
| `correlation_id` | varchar(100) | İsteğin izleme numarası. İstemci `X-Correlation-Id` header'ı gönderdiyse o, göndermediyse üretilen UUID. Aynı değer hedefe gönderilir ve cevapta istemciye döner; bir isteği uçtan uca izlemek için bu kolonla aranır. | `5b0c9e1a-...` |
| `client_ip` | varchar(50) | İsteği gönderen IP. Önce `X-Forwarded-For`'daki ilk adres, yoksa bağlantı adresi. | `10.0.4.21` |
| `request_method` | varchar(10) | Normal iletimde **hedefe gönderilen** metot (`target_method` uygulanmış hali). Eşleştirme hatasında istemcinin **gelen** metodu. | `PUT` |
| `request_path` | varchar(1000) | İstemcinin çağırdığı path ve query string, `/proxy` ön eki olmadan. | `/erp/order/42?detay=1` |
| `target_url` | varchar(1000) | Gerçekten çağrılan (veya çağrılacak) hedef adres; değişkenler doldurulmuş, query string eklenmiş hali. | `http://erp.local/api/siparis/42?detay=1` |
| `request_headers` | text | Hedefe gönderilen header'lar, satır başına `Ad: değer`. `Host`, `Content-Length` gibi bağlantı header'ları iletilmediği için burada da yoktur. Eşleştirme hatası kaydında ise istemcinin gönderdiği header'ların tamamı yazılır. `Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`, `X-Api-Key` değerleri `******` olarak maskelenir. | `Content-Type: application/json` |
| `source_request_body` | text | İstemciden gelen, **eşleştirme öncesi** gövde. | `{"customerName":"Ali"}` |
| `request_body` | text | Hedefe gönderilen, **eşleştirme sonrası** gövde. Eşleştirme yoksa `source_request_body` ile aynıdır. `MAPPING_ERROR` kayıtlarında boştur. | `{"musteri":{"ad":"Ali"}}` |
| `response_status` | integer | Hedefin döndüğü HTTP status. Hedefe ulaşılamadıysa 0. İstek eşleştirmesi hatasında istemciye dönen 400; cevap eşleştirmesi hatasında hedefin orijinal (2xx) status'u. | `201`, `500`, `0` |
| `response_headers` | text | Hedeften dönen header'lar (hassas olanlar maskeli). Hedefe ulaşılamadıysa veya eşleştirme hatasında boş. | `Content-Type: application/json` |
| `response_body` | text | Hedeften dönen gövde, **cevap eşleştirmesi öncesi** hali. İstek eşleştirmesi hatasında istemciye dönen hata JSON'u. | `{"siparisNo":"S-1001"}` |
| `duration_ms` | bigint | Hedef çağrısının süresi (ms); sadece HTTP çağrısını ölçer. Eşleştirme hatalarında 0. | `184` |
| `result_type` | smallint | İletimin sonucu. Hangi durumda hangi değer yazıldığı aşağıdaki tabloda. | 1, 2, 3, 4 |
| `error_message` | varchar(2000) | Hata açıklaması: eşleştirme hatasının sebebi, bağlantı hatası mesajı veya hedefin hata cevabından üretilen mesaj. Başarılı iletimlerde `-`. 2000 karakterde kesilir. | `Zorunlu alan bulunamadı: customerName` |

`result_type` değerleri:

| Değer | Ad | Ne zaman |
|---|---|---|
| 1 | SUCCESS | Hedef 2xx veya 3xx döndü |
| 2 | TARGET_ERROR | Hedef 4xx veya 5xx döndü (cevap istemciye aynen iletilir) |
| 3 | CONNECTION_ERROR | Hedefe bağlanılamadı veya zaman aşımı (`response_status = 0`) |
| 4 | MAPPING_ERROR | İstek veya cevap eşleştirilemedi |

> **Cevap eşleştirmesi hatasında iki satır oluşur.** İstek hedefe gönderilip 2xx cevap alındığı için önce normal iletim kaydı (`SUCCESS`, hedefe giden gövde ve süre ile) yazılır. Cevap eşleştirilemeyince aynı `correlation_id` ile ikinci bir `MAPPING_ERROR` kaydı eklenir ve istemciye 502 döner. İstek eşleştirmesi hatasında istek gönderilmediği için tek satır (`MAPPING_ERROR`) vardır.

Gövde kolonları (`source_request_body`, `request_body`, `response_body`) için ortak kurallar:
- Sadece route'un `log_body = 1` olduğu durumda yazılır; aksi halde boştur.
- UTF-8 metin olarak saklanır, bu yüzden ikili (binary) içerik okunamaz görünür.
- `proxy.log.max-body-length` (varsayılan 100000) karakterde kesilir.

İndeksler: `(proxy_route_id, date_of_recorded)`, `(correlation_id)`, `(date_of_recorded)`.

### `swagger_source` — Swagger kaynakları

Her satır, endpoint'leri okunacak bir servistir: "şu servisin swagger dokümanını oku". Kullanıcı API ile girer; `last_sync_*` ve `endpoint_count` alanlarını senkron servisi yazar. Süreç için [Swagger'dan endpoint senkronizasyonu](#swaggerdan-endpoint-senkronizasyonu) bölümüne bakın.

| Kolon | Tip | Zorunlu / Varsayılan | Açıklama | Örnek / geçerli değerler |
|---|---|---|---|---|
| `source_code` | varchar(50) | Zorunlu | Servisin benzersiz iş kodu (`uk1`). Loglarda ve senkronizasyon sonucunda görünür. | `ERP`, `IFS_PART` |
| `source_description` | varchar(150) | `-` | Servisin açıklaması. **Sadece bilgi amaçlıdır**, çalışma anında kullanılmaz. | `ERP sipariş servisi` |
| `swagger_url` | varchar(500) | Zorunlu | Dokümanın bulunacağı adres. swagger-ui sayfası, swagger-config, doğrudan doküman (JSON/YAML) ya da servis kökü olabilir. `http://` veya `https://` ile başlamalıdır (kayıtta kontrol edilir). Çözülme sırası için [Swagger adresinin çözülmesi](#swagger-adresinin-çözülmesi)'ne bakın. | `http://erp:8081/swagger-ui/index.html`, `http://erp:8081/v3/api-docs`, `http://erp:8081` |
| `target_base_url` | varchar(500) | `-` | `swagger_endpoint.full_url` için taban adres. `-` ise dokümandaki `servers` (OAS3) ya da `host + basePath` (Swagger 2) kullanılır. Servis gateway / load balancer arkasındaysa ve dokümandaki adres dışarıdan geçerli değilse doldurun. Sondaki `/` önemsizdir. | `-`, `https://gw.tatmetal.com.tr/erp` |
| `auto_sync` | smallint | 1 | Açılış ve zamanlanmış senkronizasyona dahil mi; 1 ise kayıt sırasında da hemen senkronize edilir. 0 olsa da `/{id}/sync` ile elle çalıştırılabilir. | 0 Hayır, 1 Evet |
| `last_sync_date` | timestamp | null | Son senkronizasyonun (başarılı veya hatalı) veritabanı saatine göre zamanı. Hiç çalışmadıysa boş. | `2026-10-11 10:00:00.123` |
| `last_sync_result` | smallint | 0 | Son senkronizasyonun sonucu. Kilit nedeniyle atlanan çalışmalar bu alanı değiştirmez. | 0 NEVER, 1 SUCCESS, 2 ERROR |
| `last_sync_message` | varchar(2000) | `-` | Başarılıysa özet, hatalıysa hata mesajı. Doküman bulunamadığında denenen tüm adresleri ve sonuçlarını içerir. 2000 karakterde kesilir. | `12 endpoint: 2 yeni, 1 güncellenen, 9 değişmeyen, 0 kaldırılan (CANCELLED)` |
| `endpoint_count` | integer | 0 | Son **başarılı** senkronizasyonda dokümanda bulunan endpoint sayısı. Hatalı çalışmada eski değer korunur. | `12` |

Anahtar: `uk1_swagger_source (source_code, counter_of_unique_data)`.

> `last_sync_*` ve `endpoint_count` güncellenirken `etag` **değiştirilmez**. Bunlar sistemin yazdığı durum bilgileridir; değişseydi kaynağı ekranda açık tutan kullanıcının `PUT` isteği her senkronizasyondan sonra etag hatası alırdı.

### `swagger_endpoint` — Okunan endpoint'ler

Bu tablo elle doldurulmaz; satırları sadece `SwaggerSyncService` ekler ve günceller. Her satır, bir kaynağın dokümanındaki tek bir `metot + path`'tir. Elle yapılabilecek tek değişiklik `status`'u PASSIVE yapmaktır.

| Kolon | Tip | Açıklama | Örnek |
|---|---|---|---|
| `swagger_source_id` | uuid | Endpoint'in okunduğu kaynak (`fk1` → `swagger_source.id`). | `8d0f5a3e-...` |
| `api_group` | varchar(100) | springdoc grup adı (swagger-config'deki `urls[].name`). Grup yoksa `-`. Aynı path farklı gruplarda varsa her grup için ayrı satır olur. | `-`, `public`, `admin` |
| `http_method` | varchar(10) | Büyük harfle HTTP metodu. | `GET`, `POST`, `DELETE` |
| `path` | varchar(1000) | Dokümandaki path, olduğu gibi (değişkenler `{...}` ile). Swagger 2'de `basePath` **dahil değildir**; o `full_url`'de bulunur. | `/api/v1/orders/{id}` |
| `full_url` | varchar(1500) | Taban adres + path. Taban adres `target_base_url`'den, o yoksa dokümandan gelir (bkz. [Endpoint'e dönüştürme](#endpointe-dönüştürme)). Değişkenler doldurulmaz. | `http://erp.local:8081/api/v1/orders/{id}` |
| `operation_id` | varchar(200) | OpenAPI `operationId`. Yoksa `-`. springdoc'ta genellikle Java metot adıdır. | `getOrder`, `-` |
| `summary` | varchar(500) | Operasyon özeti; operasyonda yoksa path seviyesindeki. Yoksa `-`. | `Siparişi getir` |
| `description` | text | Operasyon açıklaması; operasyonda yoksa path seviyesindeki. Yoksa boş. | `Sipariş başlığı ve kalemleri döner.` |
| `tags` | varchar(500) | Virgülle ayrılmış tag listesi (tekrarlar ayıklanır). springdoc'ta genellikle controller adıdır. Yoksa `-`. | `order,write` |
| `deprecated` | smallint | Dokümanda `deprecated: true` mu. | 0 Hayır, 1 Evet |
| `request_content_type` | varchar(500) | İstek gövdesinin kabul ettiği içerik tipleri, virgülle ayrılmış. Gövdesiz endpoint'lerde `-`. | `application/json`, `multipart/form-data`, `-` |
| `parameters_json` | text | Path, query, header ve cookie parametreleri; dokümandaki JSON dizisi olduğu gibi (Swagger 2'de `in: body` hariç). Path ve operasyon seviyesi birleştirilmiştir. Parametre yoksa boş. | `[{"name":"id","in":"path","required":true,"schema":{"type":"string"}}]` |
| `request_body_json` | text | İstek gövdesi tanımı: OAS3'te `requestBody`, Swagger 2'de `in: body` parametresi. `$ref`'ler çözülmez. Gövde yoksa boş. | `{"content":{"application/json":{"schema":{"$ref":"#/components/schemas/Order"}}}}` |
| `responses_json` | text | Cevap tanımları (`responses` nesnesi), status kodu → tanım. `$ref`'ler çözülmez. | `{"200":{"description":"OK"},"404":{"description":"Not Found"}}` |
| `last_seen_date` | timestamp | Endpoint'in dokümanda en son görüldüğü senkronizasyonun zamanı. İçerik değişmese de her başarılı senkronizasyonda güncellenir. CANCELLED satırlarda dokümandan kalkmadan önceki son görülme zamanı kalır. | `2026-10-11 10:00:00.123` |

`status` değerlerinin bu tablodaki anlamı:

| Değer | Anlam | Nasıl olur |
|---|---|---|
| 3 ACTIVE | Dokümanda var | Senkronizasyon ekledi |
| 2 PASSIVE | Dokümanda var ama gizlenmiş | Kullanıcı `PUT /api/v1/swagger-endpoint` ile yaptı; senkronizasyon değiştirmez |
| 1 CANCELLED | Dokümandan kalkmış (geçmiş kaydı) | Senkronizasyon yaptı; `counter_of_unique_data > 0` |

Anahtar ve indeks:
- `uk1_swagger_endpoint (swagger_source_id, api_group, http_method, path, counter_of_unique_data)`: upsert bu anahtarla yapılır (`ON CONFLICT`). Aktif satırlarda `counter_of_unique_data = 0`'dır.
- `ix1_swagger_endpoint (swagger_source_id, status)`.

> JSON kolonları dokümandaki sırayla, boşluksuz yazılır. Kaynak servis aynı dokümanı farklı alan sırasıyla üretirse endpoint "güncellenen" sayılır (etag yenilenir); veri kaybı olmaz.

---

## Redis önbellek

Anahtarlar `proxy.cache.key-prefix` (varsayılan `message-proxy`) ile başlar:

| Anahtar | Tip | İçerik |
|---|---|---|
| `message-proxy:routes` | HASH | `"POST /erp/order"` → route JSON (sabit path'ler, O(1) okuma) |
| `message-proxy:route-patterns` | HASH | `"GET /erp/order/{id}"` → route JSON (değişken veya joker içeren path'ler) |
| `message-proxy:loaded-at` | STRING | Son yükleme zamanı (ISO-8601) |

Kaynak metodu 0 (tümü) olan route'larda anahtar metodu `ANY` olur: `"ANY /erp/order"`.

**Önbellek ne zaman yenilenir?**

| Durum | Yenileme |
|---|---|
| Uygulama açılışı | Otomatik (`ApplicationReadyEvent`) |
| `/api/v1/proxy-route` veya `/api/v1/proxy-field-map` ile kayıt ekleme/güncelleme/silme | Otomatik |
| Redis yeniden başladı veya `loaded-at` silindi | İlk istekte otomatik |
| DB'ye SQL ile doğrudan değişiklik | **Elle:** `POST /api/v1/proxy-route/cache/refresh` |

Redis içeriğini görmek için `GET /api/v1/proxy-route/cache` kullanılabilir. Komut satırından:

```bash
redis-cli HKEYS message-proxy:routes
redis-cli HGET  message-proxy:routes "POST /erp/order"
```

---

## Route eşleşme kuralları

Gelen istek için route şu sırayla aranır; ilk bulunan kullanılır:

1. Sabit path + gelen metot (`POST /erp/order`)
2. Sabit path + tüm metotlar (`ANY /erp/order`)
3. Değişkenli path + gelen metot (`GET /erp/order/{id}`)
4. Değişkenli path + tüm metotlar (`ANY /erp/order/{id}`)

Birden fazla değişkenli path eşleşirse daha spesifik olan seçilir (Spring `AntPathMatcher` kuralları). Path'lerde `{değişken}`, `*` ve `**` kullanılabilir.

**Path değişkenleri ve query string:**

| `source_path` | `target_url` | Gelen istek | Hedefe giden |
|---|---|---|---|
| `/erp/order` | `http://erp/api/siparis` | `POST /proxy/erp/order` | `POST http://erp/api/siparis` |
| `/erp/order/{id}` | `http://erp/api/siparis/{id}` | `GET /proxy/erp/order/42?detay=1` | `GET http://erp/api/siparis/42?detay=1` |

Path karşılaştırması büyük/küçük harfe duyarlıdır. Sondaki `/` önemsizdir.

---

## Alan eşleştirme (mapping)

### Söz dizimi

| source_field | target_field | Anlamı |
|---|---|---|
| `customerName` | `musteriAdi` | Üst seviye alanın adı değişir |
| `customerName` | `musteri.ad` | Üst seviye alan iç içe alana taşınır (ara nesne oluşturulur) |
| `customer.tax` | `musteri.vergiNo` | İç içe alandan iç içe alana |
| `items[].code` | `kalemler[].urunKodu` | Dizinin her elemanında alan adı değişir |
| `tags[]` | `etiketler[]` | Dizinin sadece adı değişir, elemanlar aynen kalır |
| `orders[].lines[].c` | `siparisler[].satirlar[].kod` | İç içe diziler |

**Kurallar:**
- Kaynak ve hedef yolda `[]` sayısı eşit olmalıdır.
- `[0]` gibi indeks desteklenmez, sadece `[]` kullanılır.
- Yol `.` ile başlayamaz veya bitemez.
- Aynı dizi için tanımlı tüm alanlar tek dizide toplanır. Örneğin `items[].code` ve `items[].qty` birlikte `kalemler[]` dizisini oluşturur.
- Alan yolları büyük/küçük harfe duyarlıdır.

### `pass_unmapped_fields` etkisi

Gelen gövde:
```json
{ "customerName": "Ali", "note": "acil", "items": [ { "code": "A1", "qty": 2, "color": "red" } ] }
```
Eşleştirmeler: `customerName → musteri.ad`, `items[].code → kalemler[].urunKodu`, `items[].qty → kalemler[].miktar`

| `pass_unmapped_fields = 1` (aktar) | `pass_unmapped_fields = 0` (sadece eşleşenler) |
|---|---|
| `{"note":"acil","musteri":{"ad":"Ali"},"kalemler":[{"color":"red","urunKodu":"A1","miktar":2}]}` | `{"musteri":{"ad":"Ali"},"kalemler":[{"urunKodu":"A1","miktar":2}]}` |

### Zorunlu alan ve varsayılan değer

| Kaynakta alan | `default_value` | `is_required` | Sonuç |
|---|---|---|---|
| Var | — | — | Değer taşınır |
| Yok | dolu | — | Varsayılan değer yazılır |
| Yok | boş | 1 | **REQUEST:** 400, istek hedefe gitmez. **RESPONSE:** 502. |
| Yok | boş | 0 | Alan hedefte oluşturulmaz |

`default_value` JSON olarak yorumlanır:

| Değer | Hedefe yazılan |
|---|---|
| `10` | sayı 10 |
| `true` | boolean |
| `"WEB"` | metin WEB |
| `{}` / `[]` | boş nesne / dizi |
| `WEB` (tırnaksız, geçersiz JSON) | metin WEB |

### Nerede uygulanır, nerede uygulanmaz

- **REQUEST** eşleştirmeleri gelen JSON gövdeye, hedefe gönderilmeden önce uygulanır.
- **RESPONSE** eşleştirmeleri sadece hedefin **2xx** dönen JSON cevabına uygulanır. Hata cevapları aynen döner.
- Gövde boşsa ya da JSON değilse eşleştirme atlanır ve gövde aynen iletilir.
- Header'lar ve query parametreleri eşleştirilmez.

---

## Swagger'dan endpoint senkronizasyonu

Bu modül, başka servislerin **swagger-ui / OpenAPI dokümanını okuyup** içindeki tüm endpoint'leri (`metot + path`) veritabanına yazar ve güncel tutar. Amaç, entegre olunan sistemlerin hangi uç noktaları sunduğunu elle girmeden tek yerde görmektir.

Proxy iletim akışından **bağımsızdır**: `swagger_endpoint` kayıtları route oluşturmaz, Redis'e yüklenmez ve `/proxy/**` isteklerini etkilemez. Route tanımları yine `proxy_route`'a ayrıca girilir.

İki tablo kullanılır:

| Tablo | Kim yazar | İçerik |
|---|---|---|
| [`swagger_source`](#swagger_source--swagger-kaynakları) | Kullanıcı (API ile); `last_sync_*` alanlarını senkron servisi | Okunacak servisin swagger adresi ve son senkronizasyonun sonucu |
| [`swagger_endpoint`](#swagger_endpoint--okunan-endpointler) | Sadece senkron servisi | Dokümandan okunan her `metot + path` için bir satır |

### Akış

```mermaid
sequenceDiagram
    participant U as Kullanıcı / Zamanlayıcı
    participant A as SwaggerSourceResources / SwaggerSyncScheduler
    participant S as SwaggerSyncService
    participant L as SwaggerDocLoader
    participant T as Hedef servis (swagger)
    participant P as SwaggerSpecParser
    participant D as PostgreSQL

    U->>A: POST /api/v1/swagger-source/{id}/sync (veya cron / açılış)
    A->>S: sync(kaynak)
    S->>L: load(swagger_url)
    L->>T: GET swagger-ui / swagger-config / api-docs
    T-->>L: OpenAPI 3 / Swagger 2 dokümanı (JSON veya YAML)
    L-->>S: doküman(lar) (springdoc grupları dahil)
    S->>P: parse(doküman, target_base_url)
    P-->>S: endpoint listesi
    S->>D: BEGIN + advisory lock (kaynak bazlı)
    S->>D: her endpoint için UPSERT (swagger_endpoint)
    S->>D: dokümanda görülmeyenleri CANCELLED yap
    S->>D: COMMIT
    S->>D: sonucu swagger_source.last_sync_* alanlarına yaz
    S-->>A: SwaggerSyncResult (yeni / güncellenen / değişmeyen / kaldırılan)
```

Adım adım:

| # | Adım | Sorumlu sınıf | Hata durumunda |
|---|---|---|---|
| 1 | Senkronizasyon tetiklenir (kayıt, elle, açılış veya cron) | `SwaggerSourceService`, `SwaggerSourceResources`, `SwaggerSyncScheduler` | — |
| 2 | `swagger_url`'den doküman adresi bulunur ve doküman(lar) indirilir | `SwaggerDocLoader` + `SwaggerDocClient` | `ERROR`; mevcut endpoint'lere dokunulmaz |
| 3 | Her dokümandaki `paths` → `metot + path` listesine çevrilir, tam adres hesaplanır | `SwaggerSpecParser` | `ERROR` |
| 4 | Hiç endpoint çıkmadıysa işlem durdurulur | `SwaggerSyncService` | `ERROR` ("hiç endpoint bulunamadı"); mevcut kayıtlar korunur |
| 5 | Transaction açılır, kaynak için kilit alınır | `SwaggerEndpointDaoImpl.tryLockSource` | Kilit alınamazsa çalışma **atlanır** (`skipped = true`) |
| 6 | Her endpoint upsert edilir | `SwaggerEndpointDaoImpl.upsert` | Transaction geri alınır, `ERROR` |
| 7 | Bu çalışmada görülmeyen endpoint'ler CANCELLED yapılır | `SwaggerEndpointDaoImpl.cancelMissing` | Transaction geri alınır, `ERROR` |
| 8 | Sonuç `swagger_source`'a yazılır, `SwaggerSyncResult` döner | `SwaggerSourceDaoImpl.updateSyncResult` | Yazılamazsa sadece loglanır; sonuç yine döner |

Adım 2–4 HTTP çağrısıdır ve **transaction dışında** yapılır; yavaş bir swagger sunucusu veritabanı bağlantısını meşgul etmez. Adım 5–7 tek transaction'dır: ya hepsi yazılır ya hiçbiri.

### Ne zaman çalışır

| Tetikleyici | Hangi kaynaklar | Ayar / uç nokta |
|---|---|---|
| Kaynak kaydedildiğinde | Kaydedilen kaynak (`autoSync` 0 değilse) | `POST /api/v1/swagger-source` |
| Elle, tek kaynak | `id` ile verilen kaynak (CANCELLED hariç; PASSIVE ve `autoSync = 0` olanlar da çalışır) | `POST /api/v1/swagger-source/{id}/sync` |
| Elle, tüm kaynaklar | `status = 3` ve `auto_sync = 1` | `POST /api/v1/swagger-source/sync` |
| Uygulama açılışında | `status = 3` ve `auto_sync = 1` | `swagger.sync.on-startup=true` |
| Zamanlanmış | `status = 3` ve `auto_sync = 1` | `swagger.sync.cron=0 0 * * * *` (her saat başı) |

- `swagger.sync.enabled=false` açılış ve zamanlanmış senkronizasyonu kapatır; elle tetikleme çalışmaya devam eder.
- `POST /api/v1/swagger-source/all` (toplu kayıt) ve `PUT` (güncelleme) senkronizasyon **başlatmaz**. Adres değiştirdiyseniz `/{id}/sync` çağırın.
- Kaynaklar sırayla işlenir; bir kaynağın hatası diğerlerini durdurmaz.

### Swagger adresinin çözülmesi

`swagger_url` alanına swagger-ui sayfası, doküman adresi ya da servisin kök adresi yazılabilir. `SwaggerDocLoader` gerçek dokümanı şu sırayla arar ve ilk bulduğunda durur:

| Sıra | Ne denenir | Örnek |
|---|---|---|
| 1 | Adreste `?configUrl=` varsa o swagger-config | `.../swagger-ui/index.html?configUrl=/v3/api-docs/swagger-config` |
| 2 | Adreste `?url=` varsa o doküman | `.../swagger-ui/index.html?url=/openapi.yaml` |
| 3 | Adresin kendisi (query olmadan); doküman ya da swagger-config ise kullanılır | `http://erp:8081/v3/api-docs` |
| 4 | Servis kökü bulunur ve standart adresler sırayla denenir: `/v3/api-docs/swagger-config`, `/v3/api-docs`, `/v2/api-docs`, `/swagger.json`, `/openapi.json` | `http://erp:8081/erp/swagger-ui/index.html` → kök `http://erp:8081/erp` |

- **Servis kökü**: adreste `/swagger-ui` geçiyorsa ondan önceki kısım, yoksa `/v3/api-docs`, `/v2/api-docs`, `/swagger.json`, `/openapi.json`, `/swagger-resources`'tan önceki kısım. Böylece context path (`/erp`) korunur.
- **springdoc grupları**: swagger-config içinde `urls` listesi varsa her grup dokümanı ayrı okunur; grup adı `swagger_endpoint.api_group`'a yazılır. Gruplardan biri okunamazsa senkronizasyonun tamamı `ERROR` olur (eksik veriyle kalkan endpoint'ler yanlışlıkla CANCELLED olmasın diye).
- **Doküman tanıma**: kökte `openapi` veya `swagger` alanı ve nesne olan `paths` varsa dokümandır. HTML cevaplar (`<` ile başlayan) atlanır. JSON değilse YAML olarak okunur.
- **Sunucuya hiç ulaşılamazsa** (bağlantı reddi, DNS, timeout) 4. adımdaki adresler denenmez; her biri ayrı timeout bekletmesin diye hemen hata verilir.
- Hata mesajında denenen her adres ve sonucu yazar: `http://erp:8081/v3/api-docs → HTTP 404; ...`.
- İstek `Accept: application/json, application/yaml` ile atılır. Kimlik doğrulama header'ı gönderilmez (bkz. [Sınırlamalar](#sınırlamalar)).

### Endpoint'e dönüştürme

`SwaggerSpecParser` dokümandaki her `paths.<path>.<metot>` için bir `swagger_endpoint` satırı üretir. Desteklenen metotlar: GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS, TRACE.

| Alan | OpenAPI 3.x | Swagger 2.0 |
|---|---|---|
| `full_url` | `servers[0].url` + path. `{değişken}`'ler `default` değeriyle doldurulur; göreli adresler doküman adresine göre çözülür. `servers` yoksa doküman adresinin kökü. | `schemes[0]://host + basePath` + path. Eksik olanlar doküman adresinden tamamlanır. |
| `parameters_json` | Path seviyesi + operasyon seviyesi parametreler birleşir; aynı `name + in` varsa operasyondaki geçerlidir. | Aynı; `in: body` parametresi buradan çıkarılır. |
| `request_body_json` | `requestBody` | `in: body` parametresi |
| `request_content_type` | `requestBody.content` anahtarları | Gövde veya `formData` parametresi varsa `consumes` (operasyonda yoksa kökteki) |
| `summary`, `description` | Operasyonda yoksa path seviyesinden | Aynı |

- `swagger_source.target_base_url` doluysa (`-` değilse) `full_url` için dokümandaki adres yerine o kullanılır. Servis bir API gateway arkasındaysa gateway adresini buraya yazın.
- `$ref` referansları **çözülmez**; JSON alanlarında olduğu gibi (`"#/components/schemas/Order"`) saklanır.
- Uzun metinler kolon boyuna göre kesilir (ör. `summary` 500, `operation_id` 200 karakter).
- Aynı çalışmada aynı `api_group + metot + path` iki kez gelirse ilki kullanılır.

### Yazma kuralları

İş anahtarı `swagger_source_id + api_group + http_method + path` (+ `counter_of_unique_data`) dır. Her endpoint için:

| Durum | Ne yapılır | `etag` / `date_of_last_updated` | `last_seen_date` |
|---|---|---|---|
| Yeni endpoint | Eklenir, `status = 3` | Yeni | Senkron zamanı |
| İçeriği değişmiş | Güncellenir | Yenilenir | Senkron zamanı |
| Aynı kalmış | İçeriğe dokunulmaz | **Değişmez** | Senkron zamanı |
| Dokümandan kalkmış | `status = 1 (CANCELLED)`, `counter_of_unique_data` sequence'tan artırılır | Yenilenir | Eski değer kalır |
| Kalkmıştı, geri geldi | **Yeni satır** olarak eklenir; eski CANCELLED satır geçmiş olarak kalır | Yeni | Senkron zamanı |

- "İçerik değişti" kontrolü şu kolonlarla yapılır: `full_url`, `operation_id`, `summary`, `description`, `tags`, `deprecated`, `request_content_type`, `parameters_json`, `request_body_json`, `responses_json`. Sadece `last_seen_date`'in değişmesi güncelleme sayılmaz.
- **`status`'a senkronizasyon dokunmaz.** Elle `PASSIVE (2)` yapılan endpoint dokümanda durdukça PASSIVE kalır; dokümandan kalkarsa CANCELLED olur.
- Bir endpoint'i listeden gizlemek için **PASSIVE** yapın. Silerseniz (`counter_of_unique_data` artar) bir sonraki senkronizasyonda yeni satır olarak geri gelir.
- "Görülmedi" kararı `last_seen_date < senkron zamanı` ile verilir. Senkron zamanı **veritabanı saatinden** (`LOCALTIMESTAMP`, transaction başlangıcı) alınır; birden fazla uygulama sunucusu arasındaki saat farkı sonucu bozmaz.
- Kaynak bazlı kilit `pg_try_advisory_xact_lock(hashtext('swagger_sync:<id>'))` ile alınır. Aynı kaynak için ikinci bir senkronizasyon (cron + elle, ya da iki instance) beklemez, atlanır. Kilit transaction bitince kendiliğinden bırakılır.
- Doküman okunamaz, parse edilemez ya da hiç endpoint içermezse `swagger_endpoint`'e **hiç yazılmaz**. Böylece geçici bir kesinti tüm endpoint'leri CANCELLED yapmaz.

### Senkronizasyon sonucu

Her çalışma `swagger_source`'a ve API cevabına (`SwaggerSyncResult`) yazılır:

| Durum | `last_sync_result` | `last_sync_message` | `endpoint_count` | `last_sync_date` |
|---|---|---|---|---|
| Başarılı | 1 `SUCCESS` | `12 endpoint: 2 yeni, 1 güncellenen, 9 değişmeyen, 0 kaldırılan (CANCELLED)` | Dokümandaki endpoint sayısı | Senkron zamanı |
| Hata | 2 `ERROR` | Hata mesajı (denenen adresler dahil, 2000 karakterde kesilir) | **Eski değer korunur** | Hata zamanı |
| Atlandı (kilit) | Değişmez | Değişmez | Değişmez | Değişmez |

`SwaggerSyncResult` örneği (`POST /api/v1/swagger-source/{id}/sync` cevabı):

```json
{
  "swaggerSourceId": "8d0f5a3e-1c2b-4e7a-9f10-2a3b4c5d6e7f",
  "sourceCode": "ERP",
  "result": { "id": 1, "value": "Başarılı", "engValue": "Success" },
  "skipped": false,
  "message": "12 endpoint: 2 yeni, 1 güncellenen, 9 değişmeyen, 0 kaldırılan (CANCELLED)",
  "documentUrls": ["http://erp:8081/v3/api-docs/public", "http://erp:8081/v3/api-docs/admin"],
  "endpointCount": 12,
  "insertedCount": 2,
  "updatedCount": 1,
  "unchangedCount": 9,
  "cancelledCount": 0,
  "durationMs": 412
}
```

| Alan | Açıklama |
|---|---|
| `result` | `SUCCESS` / `ERROR`. Atlanan çalışmada kaynağın bir önceki sonucu. |
| `skipped` | `true`: aynı kaynak için başka bir senkronizasyon sürdüğü için bu çalışma bir şey yapmadı. |
| `documentUrls` | Gerçekten okunan doküman adresleri (grup başına bir tane). |
| `endpointCount` | Dokümanlardaki toplam endpoint (tekrarlar ayıklanmış). Doküman okunamadıysa 0. |
| `insertedCount` … `cancelledCount` | Yazma sonucu. Hata durumunda hepsi 0 (transaction geri alındığı için). |
| `durationMs` | İndirme + yazma toplam süresi. |

### Örnek: yeni bir servisi ekleme

1. Kaynağı kaydedin. Kayıt cevabında `lastSyncResult` ve `lastSyncMessage` dolu döner:
   ```bash
   curl -X POST http://localhost:8080/api/v1/swagger-source \
     -H 'Content-Type: application/json' \
     -d '{"sourceCode":"ERP","sourceDescription":"ERP servisi","swaggerUrl":"http://erp:8081/swagger-ui/index.html"}'
   ```
2. `lastSyncResult.id = 2` ise `lastSyncMessage`'daki denenen adreslere bakın, adresi düzeltip (`PUT`) `/{id}/sync` çağırın.
3. Endpoint'leri listeleyin:
   ```bash
   curl -X POST http://localhost:8080/api/v1/swagger-endpoint/search \
     -H 'Content-Type: application/json' -d '{"swaggerSourceId":"<id>"}'
   ```
4. Servis gateway arkasındaysa `targetBaseUrl`'i gateway adresiyle güncelleyip tekrar senkronize edin; `full_url`'ler değişir (güncellenen olarak sayılır).

### Sınırlamalar

- Kimlik doğrulama gerektiren swagger adresleri desteklenmez (header/token gönderilmez). Swagger dokümanı proxy sunucusundan erişilebilir ve açık olmalıdır.
- `$ref` şemaları çözülmez; `components` / `definitions` ayrıca saklanmaz.
- Endpoint'lerden otomatik `proxy_route` üretilmez.
- Swagger 1.x (`/swagger-resources` tabanlı eski springfox listeleri) desteklenmez; springfox'ta `/v2/api-docs` kullanılır.
- Kaynak CANCELLED yapılsa da ona ait `swagger_endpoint` satırları olduğu gibi kalır; artık senkronize edilmez.

---

## REST API referansı

### Proxy girişi

| Metot | Yol | Açıklama |
|---|---|---|
| GET, POST, PUT, PATCH, DELETE | `/proxy/**` | Route'a göre iletim. Örnek: `POST /proxy/erp/order` |

### `/api/v1/proxy-route`

| Metot | Yol | Gövde | Açıklama |
|---|---|---|---|
| GET | `/{id}/{etag}` | — | Tek route |
| POST | `/search` | `{"routeCode":"ERP_ORDER"}` (opsiyonel) | Koşula göre listeleme |
| POST | `/` | `ProxyRouteEntity` | Ekle (+ Redis yenile) |
| POST | `/all` | `[ProxyRouteEntity]` | Toplu ekle |
| PUT | `/` | `ProxyRouteEntity` | Güncelle |
| DELETE | `/` | `ProxyRouteEntity` | Sil (CANCELLED) |
| POST | `/cache/refresh` | — | Redis'i DB'den yenile → `{"loadedRouteCount": n}` |
| GET | `/cache` | — | Redis'teki tanımlar |

Örnek kayıt:
```json
{
  "routeCode": "ERP_ORDER_CREATE",
  "routeDescription": "Sipariş oluşturma",
  "sourcePath": "/erp/order",
  "sourceMethod": "POST",
  "targetUrl": "http://erp.local/api/siparis",
  "targetMethod": "PASS_THROUGH",
  "connectTimeoutMs": 5000,
  "readTimeoutMs": 30000,
  "logBody": true,
  "passUnmappedFields": false
}
```

### `/api/v1/proxy-field-map`

| Metot | Yol | Gövde | Açıklama |
|---|---|---|---|
| GET | `/{id}/{etag}` | — | Tek eşleştirme |
| POST | `/search` | `{"proxyRouteId":"..."}` | Koşula göre listeleme |
| POST | `/` | `ProxyFieldMapEntity` | Ekle (+ doğrulama + Redis yenile) |
| POST | `/all` | `[ProxyFieldMapEntity]` | Toplu ekle |
| PUT | `/` | `ProxyFieldMapEntity` | Güncelle |
| DELETE | `/` | `ProxyFieldMapEntity` | Sil (CANCELLED) |

Örnek kayıt:
```json
{
  "proxyRouteId": "3f1c2b7e-8a4d-4c9e-9b21-5d6e7f8a9b01",
  "mapDirection": "REQUEST",
  "sourceField": "items[].code",
  "targetField": "kalemler[].urunKodu",
  "isRequired": true,
  "fieldOrder": 2
}
```

### `/api/v1/proxy-message-log` (salt okunur)

| Metot | Yol | Gövde | Açıklama |
|---|---|---|---|
| GET | `/{id}/{etag}` | — | Tek log |
| POST | `/search` | `{"correlationId":"..."}` veya `{"routeCode":"..."}` | Koşula göre listeleme |

### `/api/v1/swagger-source`

| Metot | Yol | Gövde | Açıklama |
|---|---|---|---|
| GET | `/{id}/{etag}` | — | Tek kaynak |
| POST | `/search` | `{"sourceCode":"ERP"}` (opsiyonel) | Koşula göre listeleme |
| POST | `/` | `SwaggerSourceEntity` | Ekle (+ `autoSync` ise hemen senkronize et; cevapta `lastSync*` dolu) |
| POST | `/all` | `[SwaggerSourceEntity]` | Toplu ekle (senkronizasyon yok) |
| PUT | `/` | `SwaggerSourceEntity` | Güncelle (senkronizasyon yok) |
| DELETE | `/` | `SwaggerSourceEntity` | Sil (CANCELLED); endpoint satırları kalır |
| POST | `/{id}/sync` | — | Kaynağı hemen senkronize et → `SwaggerSyncResult`. Kaynak yoksa 404. |
| POST | `/sync` | — | `status = 3` ve `auto_sync = 1` olanların hepsi → `[SwaggerSyncResult]` |

Eksik `sourceCode` veya geçersiz `swaggerUrl` 400 `{"message":"..."}` döner.

Örnek kayıt:
```json
{
  "sourceCode": "ERP",
  "sourceDescription": "ERP servisi",
  "swaggerUrl": "http://erp:8081/swagger-ui/index.html",
  "targetBaseUrl": "-",
  "autoSync": true
}
```

### `/api/v1/swagger-endpoint`

| Metot | Yol | Gövde | Açıklama |
|---|---|---|---|
| GET | `/{id}/{etag}` | — | Tek endpoint |
| POST | `/search` | `{"swaggerSourceId":"...","httpMethod":"POST"}` | Koşula göre listeleme |
| PUT | `/` | `SwaggerEndpointEntity` | Güncelle; pratikte sadece `status` (ör. 2 PASSIVE) için. Diğer alanlar bir sonraki senkronizasyonda dokümandaki değerle ezilir. |

---

## Hata cevapları

Proxy'nin **kendi ürettiği** hatalar aşağıdaki formatta döner. Hedef sistemin hataları ise değiştirilmeden döner.

```json
{
  "status": 400,
  "error": "REQUEST_MAPPING_ERROR",
  "message": "Zorunlu alan bulunamadı: items[0].code",
  "path": "/erp/order",
  "correlationId": "5b0c..."
}
```

| Status | `error` | Ne zaman | Log'a yazılır mı |
|---|---|---|---|
| 404 | `ROUTE_NOT_FOUND` | Path + metot için aktif route yok | Hayır (uygulama loguna uyarı) |
| 400 | `REQUEST_MAPPING_ERROR` | Zorunlu alan yok / gövde geçersiz JSON / dizi beklenen yerde dizi yok | Evet, `MAPPING_ERROR` |
| 502 | `RESPONSE_MAPPING_ERROR` | Hedefin 2xx cevabı eşleştirilemedi | Evet, `MAPPING_ERROR` |
| 502 | `TARGET_UNREACHABLE` | Hedefe bağlanılamadı | Evet, `CONNECTION_ERROR` |
| 504 | `TARGET_TIMEOUT` | Hedef zamanında cevap vermedi | Evet, `CONNECTION_ERROR` |
| 503 | `ROUTE_CACHE_UNAVAILABLE` | Redis ve DB'den tanım okunamadı | Hayır |
| 500 | `INVALID_TARGET_URL` | `target_url` geçerli bir URI değil | Hayır |
| 400 | `VALIDATION_ERROR` | `/api/v1/proxy-field-map` kaydında hatalı alan yolu | — |

---

## Loglama

- **Correlation ID:** Her isteğin `X-Correlation-Id` değeri olur. İstemci gönderirse o kullanılır, göndermezse üretilir. Bu değer hedefe iletilir, cevapta çağırana döner ve `proxy_message_log.correlation_id`'ye yazılır. Bir isteği uçtan uca izlemek için:
  ```json
  POST /api/v1/proxy-message-log/search
  {"correlationId": "5b0c..."}
  ```
- **Gövdeler:** `log_body = 1` olan route'larda hem gelen hem giden gövde yazılır. Böylece eşleştirmenin ne yaptığı log'dan görülebilir.
- **Maskeleme:** `Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`, `X-Api-Key`.
- **Uygulama logu:** `logging.level.tr.com.tatmetal.proxy=INFO`. `client` paketi `DEBUG` seviyesindedir, Feign istek satırlarını gösterir.

---

## Konfigürasyon

`src/main/resources/application.properties`:

| Property | Varsayılan | Açıklama |
|---|---|---|
| `server.port` | `${SERVER_PORT:8080}` | HTTP portu |
| `datasource.exdb.*` | — | Ana PostgreSQL bağlantısı (`exDb`) |
| `datasource.pgdb.*` | — | İkinci PostgreSQL bağlantısı (`pgDb`) |
| `spring.sql.init.mode` | `always` | Açılışta `schema.sql` çalışsın |
| `feign.httpclient.hc5.enabled` | `true` | Apache HttpClient 5 (PATCH desteği) |
| `feign.client.config.*` | 5000 / 30000 ms | Varsayılan timeout'lar. Route bazlı değerler bunları ezer. |
| Redis bağlantısı | localhost:6379 | Bkz. [Sorun giderme](#sorun-giderme) — Redis property adları |
| `proxy.gateway.prefix` | `/proxy` | Proxy giriş prefix'i |
| `proxy.cache.key-prefix` | `message-proxy` | Redis anahtar ön eki. Aynı Redis'i paylaşan ortamlarda farklı verin. |
| `proxy.forward.default-url` | `http://localhost` | Feign'in zorunlu taban adresi. Gerçek hedef her çağrıda belirlenir. |
| `proxy.log.max-body-length` | `100000` | Log'a yazılacak gövdenin en fazla karakter sayısı |
| `swagger.sync.enabled` | `true` | Swagger zamanlayıcısı ve açılış senkronizasyonu açık mı |
| `swagger.sync.on-startup` | `true` | Açılışta ACTIVE + `auto_sync = 1` kaynakları senkronize et |
| `swagger.sync.cron` | `0 0 * * * *` | Zamanlanmış senkronizasyon (her saat başı) |
| `swagger.sync.connect-timeout-ms` / `read-timeout-ms` | 5000 / 30000 | Swagger dokümanı okuma timeout'ları |
| `exss.auth`, `exss.unauth.url` | `true`, `/ns/` | `exss-service` kimlik doğrulama ayarları |

> **Güvenlik:** Veritabanı ve Redis şifrelerini `application.properties`'e açık yazmak yerine ortam değişkeninden okuyun. Örnek: `spring.redis.password=${REDIS_PASSWORD:}`.

---

## Yeni bir entegrasyon tanımlama (adım adım)

Senaryo: İstemci `POST /proxy/erp/order` ile İngilizce alan adlarıyla sipariş gönderiyor; ERP Türkçe alan adları bekliyor ve `siparisNo` dönüyor.

**1. Route ekleyin**
```sql
INSERT INTO message_proxy.proxy_route
       (route_code, route_description, source_path, source_method, target_url, target_method, pass_unmapped_fields)
VALUES ('ERP_ORDER_CREATE', 'Sipariş oluşturma', '/erp/order', 2,
        'http://erp.local/api/siparis', 0, 0)
RETURNING id;
```

**2. Eşleştirmeleri ekleyin** (`<route id>` yerine 1. adımdaki id)
```sql
INSERT INTO message_proxy.proxy_field_map
       (proxy_route_id, map_direction, source_field, target_field, is_required, default_value, field_order) VALUES
('<route id>', 1, 'customerName', 'musteri.ad',          1, NULL,    1),
('<route id>', 1, 'items[].code', 'kalemler[].urunKodu', 1, NULL,    2),
('<route id>', 1, 'items[].qty',  'kalemler[].miktar',   0, '1',     3),
('<route id>', 1, 'channel',      'kanal',               0, '"WEB"', 4),
('<route id>', 2, 'siparisNo',    'orderNo',             0, NULL,    1);
```

**3. Redis'i yenileyin.** SQL ile eklediğiniz için bu adım gerekli; API ile ekleseydiniz otomatik olurdu.
```bash
curl -X POST http://localhost:8080/api/v1/proxy-route/cache/refresh
```

**4. Deneyin**
```bash
curl -i -X POST http://localhost:8080/proxy/erp/order \
  -H 'Content-Type: application/json' \
  -d '{"customerName":"Ali","items":[{"code":"A1","qty":2},{"code":"B2"}]}'
```

ERP'ye giden gövde:
```json
{"musteri":{"ad":"Ali"},"kanal":"WEB","kalemler":[{"urunKodu":"A1","miktar":2},{"urunKodu":"B2","miktar":1}]}
```
ERP `{"siparisNo":"S-1001"}` dönerse istemciye `{"orderNo":"S-1001"}` döner.

**5. Log'u kontrol edin**
```sql
SELECT result_type, response_status, source_request_body, request_body, response_body
FROM message_proxy.proxy_message_log
ORDER BY date_of_recorded DESC LIMIT 5;
```

---

## Geliştirici notları

### Yeni tablo eklerken

1. `schema.sql`'e `CREATE TABLE IF NOT EXISTS` bloğu ekleyin. `<BASE_COLUMNS>`, `uk0` ve `COMMENT` satırlarını unutmayın.
2. Var olan tabloya kolon ekliyorsanız `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` kullanın.
3. Sırasıyla Entity, Sql, DaoImpl, Service ve Resources sınıflarını yazın (isimlendirme tablosuna bakın).

### Route ya da eşleştirmeye yeni alan eklerken

Yeni alan çalışma anında kullanılacaksa sadece entity'ye eklemek yetmez:
1. `ProxyRouteDefinition` / `ProxyFieldMapDefinition`'a ekleyin.
2. `ProxyRouteCacheService.toDefinition(...)` içinde entity'den aktarın.
3. Redis'teki eski JSON'lar bir sonraki `reload()`'da yenilenir.

### Bilinçli tasarım kararları

| Karar | Neden |
|---|---|
| Feign retry kapalı | POST gibi idempotent olmayan çağrılar çift gitmesin |
| Hedefin 4xx/5xx'i exception yapılmaz | Proxy şeffaf olmalı, istemci hedefin hatasını görmeli |
| Log hatası iletimi bozmaz | Log tablosu sorunu iş akışını durdurmamalı |
| Redis'e entity değil `model` nesnesi yazılır | Önbellek `mini_jpa` sürümünden bağımsız kalsın |
| Yükleme geçici anahtar + `RENAME` ile | Yenileme sırasında istekler yarım veri görmesin |
| Redis yoksa açılış durmaz | Redis geç ayağa kalksa da uygulama açılsın, ilk istekte yüklensin |
| Swagger okunamazsa endpoint'lere dokunulmaz | Geçici bir kesinti tüm endpoint'leri CANCELLED yapmasın |
| Swagger grubu okunamazsa tüm senkronizasyon hata | Eksik dokümanla diğer grupların endpoint'leri kalkmış sayılmasın |
| Swagger HTTP okuması transaction dışında | Yavaş bir swagger sunucusu DB bağlantısını tutmasın |
| Kaynak bazlı advisory lock, bekleme yok | Cron + elle tetikleme ya da birden fazla instance aynı kaynağı aynı anda yazmasın |
| Senkron zamanı DB saatinden | Sunucular arası saat farkı "dokümandan kalktı" kararını bozmasın |
| Değişmeyen endpoint'in etag'i korunur | Endpoint'i ekranda açık tutan kullanıcı gereksiz etag hatası almasın |

---

## Sorun giderme

| Belirti | Olası neden / çözüm |
|---|---|
| Her istekte 404 `ROUTE_NOT_FOUND` | `source_path` `-` kalmış olabilir, `status` 3 olmayabilir, metot uyuşmuyor olabilir ya da `/proxy` prefix'i eksik olabilir. `GET /api/v1/proxy-route/cache` ile Redis'te ne olduğuna bakın. SQL ile eklediyseniz `cache/refresh` çağırın. |
| Tablolar oluşmuyor | İki DataSource da `@Primary` olabilir (bkz. `JdbcConfig`); `spring.sql.init.mode=always` olduğunu kontrol edin. |
| Açılışta "Redis'e yüklenemedi" logu | Redis erişilemiyor. Uygulama açılır, Redis gelince ilk istekte yüklenir. |
| Redis `NOAUTH` / bağlantı hatası, şifre tanımlı olduğu halde | **Redis property adları Spring Boot sürümüne bağlıdır.** Spring Boot 2.6'da adlar `spring.redis.host/port/password/username` şeklindedir. `spring.data.redis.*` Spring Boot 3'e aittir ve 2.6'da okunmaz; bu durumda bağlantı varsayılan localhost:6379 ve şifresiz yapılır. |
| Açılışta `NoSuchMethodError: ...DnsNameResolverBuilder.socketChannelType` (Redisson) | `redisson-spring-boot-starter` 3.27.0 Spring Boot 3 / Netty 4.1.10x ister; Boot 2.6 Netty 4.1.74 getirir. Proje bu yüzden `spring-boot-starter-data-redis` (Lettuce) kullanır. pom'a Redisson tekrar eklenmemeli; IDE eski sınıfları tutuyorsa Maven projesini yenileyip `mvn clean` yapın. |
| Gövde değişmeden gidiyor | Content-Type `json` içermiyor olabilir, eşleştirmenin `map_direction`'ı yanlış olabilir ya da cevap 2xx değildir (RESPONSE eşleştirmeleri sadece 2xx'te çalışır). |
| PATCH çalışmıyor | `feign.httpclient.hc5.enabled=true` olmalı (Spring Cloud 2021.0.x'te `spring.cloud.openfeign.*` adları geçersizdir). |
| 504 `TARGET_TIMEOUT` | Route'un `read_timeout_ms` değerini artırın. |
| Swagger: `last_sync_result = 2`, "dokümanı bulunamadı" | `last_sync_message`'daki denenen adreslere bakın. Doküman adresini (`.../v3/api-docs`) doğrudan `swagger_url`'e yazmayı deneyin. springdoc'ta `springdoc.api-docs.path` değiştirilmişse yeni yol yazılmalıdır. |
| Swagger: "adresine ulaşılamadı" | Proxy sunucusundan hedefe ağ erişimi yok, host/port yanlış ya da timeout kısa. `swagger.sync.connect-timeout-ms` / `read-timeout-ms`'i kontrol edin. |
| Swagger: HTTP 401 / 403 | Swagger dokümanı kimlik doğrulama istiyor; desteklenmez. Hedef serviste api-docs yolunu açık bırakın. |
| Swagger: "hiç endpoint bulunamadı" | Doküman boş `paths` içeriyor (ör. yanlış springdoc grubu ya da `packagesToScan`). Mevcut endpoint'ler korunmuştur. |
| Swagger: `full_url` yanlış host / iç adres | Dokümandaki `servers` adresi dışarıdan geçerli değil. `target_base_url`'i doldurup tekrar senkronize edin. |
| Swagger: sonuçta `skipped = true` | Aynı kaynak için başka bir senkronizasyon o an sürüyordu (cron, açılış ya da başka instance). Biraz sonra tekrar deneyin. |
| Swagger: zamanlanmış senkronizasyon çalışmıyor | `swagger.sync.enabled=true` ve kaynağın `status = 3`, `auto_sync = 1` olduğunu kontrol edin. |
| Swagger: sildiğim endpoint geri geldi | Silme `counter_of_unique_data`'yı artırdığı için bir sonraki senkronizasyon endpoint'i yeni satır olarak ekler. Gizlemek için `status = 2 (PASSIVE)` yapın. |
