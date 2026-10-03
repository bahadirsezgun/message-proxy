# Message Proxy (TatMetal)

Gelen REST mesajını, veritabanında tanımlı route'a göre hedef sisteme REST (OpenFeign) ile ileten proxy servis.

## Teknoloji

| | |
|---|---|
| Java | 25 |
| Spring Boot | 4.1.1 |
| Spring Cloud | 2025.1.3 (OpenFeign + Apache HttpClient 5) |
| Veritabanı | PostgreSQL 13+ (JDBC, `mini_jpa`) |
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
| `proxy_route` | Mesajın iletileceği hedef tanımları (URL, metot, timeout, gövde loglansın mı) | `/api/v1/proxy-route` (CRUD) |
| `proxy_message_log` | Her iletimin istek/cevap kaydı (`fk1` → `proxy_route`) | `/api/v1/proxy-message-log` (salt okunur) |

`proxy_message_log` notları:
- Her iletim `ProxyForwardService` tarafından otomatik yazılır; log yazımındaki hata iletimi bozmaz.
- `X-Correlation-Id` header'ı yoksa üretilir ve hedefe de gönderilir.
- `result_type`: 1 SUCCESS (2xx/3xx), 2 TARGET_ERROR (4xx/5xx), 3 CONNECTION_ERROR (timeout/ulaşılamadı, `response_status = 0`).
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
mvn spring-boot:run
```

`mini_jpa` kurum içi repodan çekilir; `pom.xml` içindeki `mini-jpa.version` değerini ve Maven `settings.xml` repo ayarını kontrol edin.
