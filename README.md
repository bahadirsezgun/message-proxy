# Message Proxy (TatMetal)

Gelen REST mesajını, veritabanında tanımlı route'a göre hedef sisteme REST (OpenFeign) ile ileten proxy servis.
Gelen isteğin **path** bilgisi route'u belirler; gövdedeki alan isimleri hedef sistemin beklediği isimlere **eşleştirilir** (mapping).

## Akış

```
İstemci ──POST /proxy/erp/order──▶ ProxyGatewayResources
                                    │  prefix (/proxy) çıkarılır → path = /erp/order
                                    ▼
                          ProxyRouteCacheService.find(POST, /erp/order)   ◀── Redis
                                    │  bulunamazsa 404
                                    ▼
                          ProxyFieldMappingService (REQUEST eşleştirmeleri) → hata: 400
                                    ▼
                          ProxyForwardService.forward(...)  ──▶ Hedef sistem
                                    ▼
                          ProxyFieldMappingService (RESPONSE eşleştirmeleri, 2xx) → hata: 502
                                    ▼
İstemci ◀──────── status + header + gövde aynen döner
```

- **Açılışta** (`ApplicationReadyEvent`, schema.sql'den sonra) aktif route'lar ve eşleştirmeler DB'den okunup Redis'e yazılır.
- **İsteklerde** sadece Redis okunur. Redis temizlenmiş/yeniden başlamışsa (`{prefix}:loaded-at` yoksa) ilk istekte DB'den otomatik tekrar yüklenir.
- Route / eşleştirme API'leri ile yapılan her kayıt değişikliğinden sonra Redis yenilenir.
  DB'ye SQL ile doğrudan yapılan değişikliklerde `POST /api/v1/proxy-route/cache/refresh` çağrılır.

### Redis anahtarları (`proxy.cache.key-prefix=message-proxy`)

| Anahtar | Tip | İçerik |
|---|---|---|
| `message-proxy:routes` | HASH | `"POST /erp/order"` → route JSON (sabit path'ler) |
| `message-proxy:route-patterns` | HASH | `"GET /erp/order/{id}"` → route JSON (değişkenli path'ler) |
| `message-proxy:loaded-at` | STRING | Son yükleme zamanı |

Yükleme geçici anahtara yazılıp `RENAME` ile değiştirilir; istekler yarım veri görmez.

### Route eşleşme sırası

1. Sabit path + gelen metot → 2. Sabit path + `source_method = 0` (tümü) → 3. Değişkenli path + metot → 4. Değişkenli path + tümü.
Değişkenli path'lerde `{id}` değeri `target_url` içindeki `{id}` yerine yazılır. Gelen query string hedefe eklenir.

### Alan eşleştirme söz dizimi (`proxy_field_map`)

| source_field | target_field | Anlamı |
|---|---|---|
| `customerName` | `musteri.ad` | Üst seviye alan → iç içe alan (ara nesne oluşturulur) |
| `customer.tax` | `musteri.vergiNo` | İç içe → iç içe |
| `items[].code` | `kalemler[].urunKodu` | Dizinin her elemanında alan adı değişir |
| `tags[]` | `etiketler[]` | Dizinin sadece adı değişir |

- Kaynak ve hedefte `[]` sayısı eşit olmalıdır. Gövde en üstte dizi ise eşleştirme her elemana uygulanır.
- `proxy_route.pass_unmapped_fields = 1`: eşleştirilmeyen alanlar aynen aktarılır; `0`: sadece eşleştirilen alanlar gönderilir.
- `is_required = 1` ve alan yoksa (default da yoksa) istek hedefe gönderilmez, **400** döner, log'a `MAPPING_ERROR` yazılır.
- `default_value` JSON olarak yorumlanır: `10`, `true`, `"WEB"`, `{}`; geçersiz JSON metin kabul edilir.
- Eşleştirme sadece JSON gövdeye uygulanır (Content-Type `json` içeriyorsa veya gövde `{`/`[` ile başlıyorsa); diğer içerikler aynen iletilir.
- `map_direction = 2 (RESPONSE)` eşleştirmeleri sadece 2xx JSON cevaplara uygulanır; hata cevapları aynen döner.

### Örnek tanım

```sql
INSERT INTO message_proxy.proxy_route (route_code, route_description, source_path, source_method,
       target_url, target_method, pass_unmapped_fields)
VALUES ('ERP_ORDER_CREATE', 'Sipariş oluşturma', '/erp/order', 2,
        'http://erp.local/api/siparis', 0, 0)
RETURNING id;

INSERT INTO message_proxy.proxy_field_map (proxy_route_id, map_direction, source_field, target_field, is_required, field_order) VALUES
('<route id>', 1, 'customerName',  'musteri.ad',          1, 1),
('<route id>', 1, 'items[].code',  'kalemler[].urunKodu', 1, 2),
('<route id>', 1, 'items[].qty',   'kalemler[].miktar',   0, 3),
('<route id>', 2, 'siparisNo',     'orderNo',             0, 1);
```

Sonra `POST /api/v1/proxy-route/cache/refresh`. İstek:

```bash
curl -X POST http://localhost:8080/proxy/erp/order -H 'Content-Type: application/json' \
  -d '{"customerName":"Ali","items":[{"code":"A1","qty":2}]}'
# ERP'ye giden gövde: {"musteri":{"ad":"Ali"},"kalemler":[{"urunKodu":"A1","miktar":2}]}
```

### Hata cevapları (proxy'nin kendi ürettiği)

| Status | `error` | Durum |
|---|---|---|
| 404 | `ROUTE_NOT_FOUND` | Path + metot için aktif route yok |
| 400 | `REQUEST_MAPPING_ERROR` | Zorunlu alan yok / gövde geçersiz JSON |
| 502 | `RESPONSE_MAPPING_ERROR` | Hedef cevabı eşleştirilemedi |
| 502 / 504 | `TARGET_UNREACHABLE` / `TARGET_TIMEOUT` | Hedefe ulaşılamadı / zaman aşımı |
| 503 | `ROUTE_CACHE_UNAVAILABLE` | Redis ve DB'den tanım okunamadı |

## Teknoloji

| | |
|---|---|
| Java | 17 |
| Spring Boot | 2.6.4 |
| Spring Cloud | 2021.0.1 (OpenFeign + Apache HttpClient 5) |
| Veritabanı | PostgreSQL 13+ (JDBC, `mini_jpa`) |
| Önbellek | Redis (route + alan eşleştirme tanımları) |
| Lombok | Kullanılmaz |

## Proje parametreleri (standart dokümanına göre başta belirlenenler)

| Parametre | Değer |
|---|---|
| Kök paket | `tr.com.tatmetal.proxy` |
| Şema | `message_proxy` |
| JdbcTemplate qualifier | `exdbJdbcTemplate` |

## Paket yapısı

```
tr.com.tatmetal.proxy
├── MessageProxyApplication          
├── api.v1.resources                 REST API katmanı      (*Resources)
├── services                         Service katmanı       (*Service)
├── entity                           Entity katmanı        (*Entity, BaseEntity'den extend)
├── dao                              Dao katmanı           (*DaoImpl)
│   └── sql                          SQL sabitleri         (*Sql)
├── enums                            IEnumNumber enum'ları (standart dışı ek paket)
├── model                            Redis'te tutulan route/eşleştirme tanımları (standart dışı ek paket)
├── exception                        Uygulama exception'ları (standart dışı ek paket)
├── client                           OpenFeign client'ları (standart dışı ek paket)
└── config                           Spring konfigürasyonları (standart dışı ek paket)
```

Tablo bazlı sınıf isimlendirmesi (örnek tablo `proxy_route`):

| Katman | Sınıf |
|---|---|
| Entity | `ProxyRouteEntity` |
| Sql | `ProxyRouteSql` |
| Dao | `ProxyRouteDaoImpl` |
| Service | `ProxyRouteService` |
| Rest Api | `ProxyRouteResources` |

## Tablolar

| Tablo | Amaç | API |
|---|---|---|
| `proxy_route` | Gelen path/metot → hedef tanımı (URL, metot, timeout, gövde loglansın mı, eşleşmeyen alanlar aktarılsın mı) | `/api/v1/proxy-route` (CRUD), `/cache`, `/cache/refresh` |
| `proxy_field_map` | Route bazlı alan eşleştirmeleri (`fk1` → `proxy_route`) | `/api/v1/proxy-field-map` (CRUD) |
| `proxy_message_log` | Her iletimin istek/cevap kaydı (`fk1` → `proxy_route`) | `/api/v1/proxy-message-log` (salt okunur) |

`proxy_message_log` notları:
- Her iletim `ProxyForwardService` tarafından otomatik yazılır; log yazımındaki hata iletimi bozmaz.
- `X-Correlation-Id` header'ı yoksa üretilir ve hedefe de gönderilir.
- `result_type`: 1 SUCCESS (2xx/3xx), 2 TARGET_ERROR (4xx/5xx), 3 CONNECTION_ERROR (timeout/ulaşılamadı, `response_status = 0`).
- `source_request_body` gelen (eşleştirme öncesi), `request_body` hedefe giden (eşleştirilmiş) gövdedir.
- `result_type = 4 MAPPING_ERROR`: istek/cevap eşleştirilemedi.
- Gövdeler `proxy_route.log_body = 1` ise yazılır ve `proxy.log.max-body-length` ile kırpılır.
- `Authorization`, `Cookie`, `Set-Cookie`, `X-Api-Key` header değerleri maskelenir.

## Veritabanı standardı

`src/main/resources/schema.sql` uygulama açılışında çalışır, şema/tablo yoksa oluşturur (Paketleme Veritabanı standardıyla aynı):

- Her tabloda `<BASE_COLUMNS>`: `id`, `id_serial`, `etag`, `status`, `date_of_recorded`, `user_who_recorded`, `date_of_last_updated`, `user_who_last_updated`, `counter_of_unique_data`
- `PRIMARY KEY (id)`, `uk0_<tablo> UNIQUE (id_serial)`
- İş anahtarı: `ukN_<tablo> UNIQUE (<alanlar>, counter_of_unique_data)`
- Yabancı anahtar: `fkN_<tablo> FOREIGN KEY (...) REFERENCES message_proxy.<tablo>(id)`
- Silme yok; `status = 1 (CANCELLED)` yapılır ve `counter_of_unique_data` `message_proxy.counter_of_unique_data_seq` ile artırılır.

Yeni tablo eklerken: `schema.sql`'e `CREATE TABLE IF NOT EXISTS` bloğu → Entity → Sql → DaoImpl → Service → Resources.

## OpenFeign ile iletim

`client.ProxyForwardClient` tek bir genel client'tır:
- `URI` parametresi hedef adresi çağrı bazında belirler (`proxy_route.target_url`).
- `Request.Options` parametresi route bazlı timeout'ları uygular.
- Gövde `byte[]` olarak dönüştürülmeden taşınır.

`services.ProxyForwardService.forward(...)` hedefin 4xx/5xx cevaplarını exception'a çevirmeden çağırana aynen döner; Retry kapalıdır.

Sabit adresli başka bir sisteme çağrı gerekiyorsa `client` paketine ayrı bir `@FeignClient(name = "...", url = "${...}")` eklenir.

## Çalıştırma

```bash
export DB_URL=jdbc:postgresql://localhost:5432/tatmetal
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export REDIS_HOST=localhost REDIS_PORT=6379
mvn spring-boot:run
```

`mini_jpa` kurum içi repodan çekilir; `pom.xml` içindeki `mini-jpa.version` değerini ve Maven `settings.xml` repo ayarını kontrol edin.
