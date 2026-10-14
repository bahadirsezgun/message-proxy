-- =====================================================================
-- Message Proxy - Veritabanı şeması
-- Uygulama ayağa kalkarken çalışır (spring.sql.init.mode=always).
-- Tüm ifadeler idempotent'tir: tablo/şema varsa dokunulmaz.
--
-- Standart (Paketleme Veritabanı ile aynı):
--   * Her tabloda <BASE_COLUMNS>: id, id_serial, etag, status, date_of_recorded,
--     user_who_recorded, date_of_last_updated, user_who_last_updated, counter_of_unique_data
--   * PRIMARY KEY (id)
--   * uk0_<tablo>  UNIQUE (id_serial)
--   * ukN_<tablo>  iş anahtarı + counter_of_unique_data
--   * fkN_<tablo>  FOREIGN KEY
--   * status: 1 CANCELLED, 2 PASSIVE, 3 ACTIVE, 4 CLOSED (delete yerine CANCELLED)
-- gen_random_uuid() PostgreSQL 13+ ile çekirdekte gelir.
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS message_proxy;

-- CANCELLED'a çekilen kayıtlarda counter_of_unique_data bu genel sequence ile artırılır.
CREATE SEQUENCE IF NOT EXISTS message_proxy.counter_of_unique_data_seq START WITH 1 INCREMENT BY 1;
-- mini_jpa BaseEntity, update ile status = 1 (CANCELLED) yapılan kayıtta counter_of_unique_data'yı
-- <şema>.system_table_uniqueless_control_seq ile artırır (@Sequence). Bu sequence yoksa CANCELLED'a çekme hata verir.
CREATE SEQUENCE IF NOT EXISTS message_proxy.system_table_uniqueless_control_seq START WITH 1 INCREMENT BY 1;

-- ---------------------------------------------------------------------
-- proxy_route : Gelen mesajın iletileceği hedef tanımları
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS message_proxy.proxy_route (
    -- <BASE_COLUMNS>
    id                      uuid                        NOT NULL DEFAULT gen_random_uuid(),
    id_serial               SERIAL                      NOT NULL,
    etag                    uuid                        NOT NULL DEFAULT gen_random_uuid(),
    status                  smallint                    NOT NULL DEFAULT 3,
    date_of_recorded        timestamp without time zone NOT NULL DEFAULT now(),
    user_who_recorded       character varying(50)       NOT NULL DEFAULT '-'::character varying,
    date_of_last_updated    timestamp without time zone NOT NULL DEFAULT now(),
    user_who_last_updated   character varying(50)       NOT NULL DEFAULT '-'::character varying,
    counter_of_unique_data  bigint                      NOT NULL DEFAULT 0,
    -- Tabloya özel alanlar
    route_code              character varying(50)       NOT NULL,
    route_description       character varying(150)      NOT NULL DEFAULT '-'::character varying,
    source_path             character varying(500)      NOT NULL DEFAULT '-'::character varying,
    source_method           smallint                    NOT NULL DEFAULT 0,
    target_url              character varying(500)      NOT NULL,
    target_method           smallint                    NOT NULL DEFAULT 0,
    connect_timeout_ms      integer                     NOT NULL DEFAULT 5000,
    read_timeout_ms         integer                     NOT NULL DEFAULT 30000,
    log_body                smallint                    NOT NULL DEFAULT 1,
    pass_unmapped_fields    smallint                    NOT NULL DEFAULT 1,
    CONSTRAINT proxy_route_pkey PRIMARY KEY (id),
    CONSTRAINT uk0_proxy_route UNIQUE (id_serial),
    CONSTRAINT uk1_proxy_route UNIQUE (route_code, counter_of_unique_data)
);

-- Önceki sürümle oluşmuş tablolar için yeni kolonlar (idempotent)
ALTER TABLE message_proxy.proxy_route ADD COLUMN IF NOT EXISTS source_path character varying(500) NOT NULL DEFAULT '-'::character varying;
ALTER TABLE message_proxy.proxy_route ADD COLUMN IF NOT EXISTS source_method smallint NOT NULL DEFAULT 0;
ALTER TABLE message_proxy.proxy_route ADD COLUMN IF NOT EXISTS pass_unmapped_fields smallint NOT NULL DEFAULT 1;

-- uk2: aynı gelen path + metot için tek aktif route. source_path '-' (henüz tanımlanmamış) kayıtlar hariç.
CREATE UNIQUE INDEX IF NOT EXISTS uk2_proxy_route
    ON message_proxy.proxy_route (source_path, source_method, counter_of_unique_data)
    WHERE source_path <> '-';

COMMENT ON TABLE  message_proxy.proxy_route IS 'Gelen mesajın iletileceği hedef tanımları';
COMMENT ON COLUMN message_proxy.proxy_route.id_serial IS 'Sıralı ID için. Sorgulardaki sıralama kolaylığı için';
COMMENT ON COLUMN message_proxy.proxy_route.etag IS 'Versiyon kontrolü yaparak aynı anda tek transaction a müsade etmek için';
COMMENT ON COLUMN message_proxy.proxy_route.status IS '1: CANCELLED, 2: PASSIVE, 3: ACTIVE, 4: CLOSED';
COMMENT ON COLUMN message_proxy.proxy_route.counter_of_unique_data IS 'CANCELLED kayıtlarda sequence ile artırılır; aynı iş anahtarı tekrar eklenebilsin diye UK''ya dahildir';
COMMENT ON COLUMN message_proxy.proxy_route.route_code IS 'Route kodu (gelen istekte kullanılacak anahtar)';
COMMENT ON COLUMN message_proxy.proxy_route.route_description IS 'Açıklaması';
COMMENT ON COLUMN message_proxy.proxy_route.source_path IS 'Gelen isteğin path bilgisi (gateway prefix hariç). Örn: /erp/order veya /erp/order/{id}';
COMMENT ON COLUMN message_proxy.proxy_route.source_method IS 'Gelen isteğin metodu. 0: Tüm metotlar, 1: GET, 2: POST, 3: PUT, 4: PATCH, 5: DELETE';
COMMENT ON COLUMN message_proxy.proxy_route.pass_unmapped_fields IS '0: Hayır - sadece eşleştirilen alanlar gönderilir, 1: Evet - eşleşmeyen alanlar aynı isimle aktarılır';
COMMENT ON COLUMN message_proxy.proxy_route.target_url IS 'Mesajın iletileceği hedef URL. Path değişkeni içerebilir: http://erp/api/orders/{id}';
COMMENT ON COLUMN message_proxy.proxy_route.target_method IS '0: PASS_THROUGH, 1: GET, 2: POST, 3: PUT, 4: PATCH, 5: DELETE';
COMMENT ON COLUMN message_proxy.proxy_route.connect_timeout_ms IS 'Hedefe bağlantı timeout (ms)';
COMMENT ON COLUMN message_proxy.proxy_route.read_timeout_ms IS 'Hedeften cevap okuma timeout (ms)';
COMMENT ON COLUMN message_proxy.proxy_route.log_body IS '0: Hayır, 1: Evet - mesaj gövdesi loglansın mı';

-- ---------------------------------------------------------------------
-- proxy_field_map : Gelen istek / dönen cevap alanlarının hedef alan isimleriyle eşleştirmesi
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS message_proxy.proxy_field_map (
    -- <BASE_COLUMNS>
    id                      uuid                        NOT NULL DEFAULT gen_random_uuid(),
    id_serial               SERIAL                      NOT NULL,
    etag                    uuid                        NOT NULL DEFAULT gen_random_uuid(),
    status                  smallint                    NOT NULL DEFAULT 3,
    date_of_recorded        timestamp without time zone NOT NULL DEFAULT now(),
    user_who_recorded       character varying(50)       NOT NULL DEFAULT '-'::character varying,
    date_of_last_updated    timestamp without time zone NOT NULL DEFAULT now(),
    user_who_last_updated   character varying(50)       NOT NULL DEFAULT '-'::character varying,
    counter_of_unique_data  bigint                      NOT NULL DEFAULT 0,
    -- Tabloya özel alanlar
    proxy_route_id          uuid                        NOT NULL,
    map_direction           smallint                    NOT NULL DEFAULT 1,
    source_field            character varying(250)      NOT NULL,
    target_field            character varying(250)      NOT NULL,
    is_required             smallint                    NOT NULL DEFAULT 0,
    default_value           character varying(500),
    field_order             integer                     NOT NULL DEFAULT 0,
    map_description         character varying(150)      NOT NULL DEFAULT '-'::character varying,
    CONSTRAINT proxy_field_map_pkey PRIMARY KEY (id),
    CONSTRAINT uk0_proxy_field_map UNIQUE (id_serial),
    CONSTRAINT uk1_proxy_field_map UNIQUE (proxy_route_id, map_direction, target_field, counter_of_unique_data),
    CONSTRAINT fk1_proxy_field_map FOREIGN KEY (proxy_route_id) REFERENCES message_proxy.proxy_route (id)
);

CREATE INDEX IF NOT EXISTS ix1_proxy_field_map ON message_proxy.proxy_field_map (proxy_route_id);

COMMENT ON TABLE  message_proxy.proxy_field_map IS 'Gelen/dönen mesaj alanlarının hedef alan isimleriyle eşleştirmesi';
COMMENT ON COLUMN message_proxy.proxy_field_map.proxy_route_id IS 'Eşleştirmenin ait olduğu route (proxy_route.id)';
COMMENT ON COLUMN message_proxy.proxy_field_map.map_direction IS '1: REQUEST - gelen istek gövdesi hedefe giderken, 2: RESPONSE - hedefin cevabı çağırana dönerken';
COMMENT ON COLUMN message_proxy.proxy_field_map.source_field IS 'Kaynak alan yolu. Nokta ile iç içe, [] ile dizi: customer.name, items[].qty';
COMMENT ON COLUMN message_proxy.proxy_field_map.target_field IS 'Hedef alan yolu. Kaynakla aynı sayıda [] içermeli: musteri.ad, kalemler[].miktar';
COMMENT ON COLUMN message_proxy.proxy_field_map.is_required IS '0: Hayır, 1: Evet - kaynakta yoksa ve default_value boşsa istek 400 ile reddedilir';
COMMENT ON COLUMN message_proxy.proxy_field_map.default_value IS 'Kaynakta alan yoksa yazılacak değer. JSON olarak yorumlanır (10, true, "abc", {}), değilse metin';
COMMENT ON COLUMN message_proxy.proxy_field_map.field_order IS 'Hedef mesajda alanların yazılma sırası';
COMMENT ON COLUMN message_proxy.proxy_field_map.map_description IS 'Açıklaması';

-- ---------------------------------------------------------------------
-- proxy_message_log : İletilen her mesajın istek/cevap kaydı
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS message_proxy.proxy_message_log (
    -- <BASE_COLUMNS>
    id                      uuid                        NOT NULL DEFAULT gen_random_uuid(),
    id_serial               SERIAL                      NOT NULL,
    etag                    uuid                        NOT NULL DEFAULT gen_random_uuid(),
    status                  smallint                    NOT NULL DEFAULT 3,
    date_of_recorded        timestamp without time zone NOT NULL DEFAULT now(),
    user_who_recorded       character varying(50)       NOT NULL DEFAULT '-'::character varying,
    date_of_last_updated    timestamp without time zone NOT NULL DEFAULT now(),
    user_who_last_updated   character varying(50)       NOT NULL DEFAULT '-'::character varying,
    counter_of_unique_data  bigint                      NOT NULL DEFAULT 0,
    -- Tabloya özel alanlar
    proxy_route_id          uuid                        NOT NULL,
    route_code              character varying(50)       NOT NULL,
    correlation_id          character varying(100)      NOT NULL,
    client_ip               character varying(50)       NOT NULL DEFAULT '-'::character varying,
    request_method          character varying(10)       NOT NULL,
    request_path            character varying(1000)     NOT NULL DEFAULT '-'::character varying,
    target_url              character varying(1000)     NOT NULL,
    request_headers         text,
    request_body            text,
    response_status         integer                     NOT NULL DEFAULT 0,
    response_headers        text,
    response_body           text,
    duration_ms             bigint                      NOT NULL DEFAULT 0,
    result_type             smallint                    NOT NULL,
    error_message           character varying(2000)     NOT NULL DEFAULT '-'::character varying,
    CONSTRAINT proxy_message_log_pkey PRIMARY KEY (id),
    CONSTRAINT uk0_proxy_message_log UNIQUE (id_serial),
    CONSTRAINT fk1_proxy_message_log FOREIGN KEY (proxy_route_id) REFERENCES message_proxy.proxy_route (id)
);

-- Gelen (eşleştirme öncesi) istek gövdesi; request_body hedefe giden (eşleştirilmiş) gövdedir.
ALTER TABLE message_proxy.proxy_message_log ADD COLUMN IF NOT EXISTS source_request_body text;

CREATE INDEX IF NOT EXISTS ix1_proxy_message_log ON message_proxy.proxy_message_log (proxy_route_id, date_of_recorded);
CREATE INDEX IF NOT EXISTS ix2_proxy_message_log ON message_proxy.proxy_message_log (correlation_id);
CREATE INDEX IF NOT EXISTS ix3_proxy_message_log ON message_proxy.proxy_message_log (date_of_recorded);

COMMENT ON TABLE  message_proxy.proxy_message_log IS 'İletilen her mesajın istek/cevap kaydı';
COMMENT ON COLUMN message_proxy.proxy_message_log.proxy_route_id IS 'İletimde kullanılan route (proxy_route.id)';
COMMENT ON COLUMN message_proxy.proxy_message_log.route_code IS 'Route kodu (route sonradan değişse de kayıtta kalsın diye kopyalanır)';
COMMENT ON COLUMN message_proxy.proxy_message_log.correlation_id IS 'X-Correlation-Id header değeri; yoksa üretilir ve hedefe de iletilir';
COMMENT ON COLUMN message_proxy.proxy_message_log.client_ip IS 'İsteği gönderen IP';
COMMENT ON COLUMN message_proxy.proxy_message_log.request_method IS 'Hedefe gönderilen HTTP metodu';
COMMENT ON COLUMN message_proxy.proxy_message_log.request_path IS 'Gelen isteğin path + query bilgisi';
COMMENT ON COLUMN message_proxy.proxy_message_log.target_url IS 'Mesajın iletildiği hedef URL';
COMMENT ON COLUMN message_proxy.proxy_message_log.request_headers IS 'Hedefe gönderilen header''lar (hassas olanlar maskelenir)';
COMMENT ON COLUMN message_proxy.proxy_message_log.request_body IS 'İstek gövdesi (proxy_route.log_body = 1 ise)';
COMMENT ON COLUMN message_proxy.proxy_message_log.response_status IS 'Hedefin HTTP status kodu; ulaşılamadıysa 0';
COMMENT ON COLUMN message_proxy.proxy_message_log.response_headers IS 'Hedeften dönen header''lar (hassas olanlar maskelenir)';
COMMENT ON COLUMN message_proxy.proxy_message_log.response_body IS 'Cevap gövdesi (proxy_route.log_body = 1 ise)';
COMMENT ON COLUMN message_proxy.proxy_message_log.duration_ms IS 'Hedef çağrısının süresi (ms)';
COMMENT ON COLUMN message_proxy.proxy_message_log.source_request_body IS 'Gelen isteğin eşleştirme öncesi gövdesi (proxy_route.log_body = 1 ise)';
COMMENT ON COLUMN message_proxy.proxy_message_log.result_type IS '1: SUCCESS (2xx/3xx), 2: TARGET_ERROR (4xx/5xx), 3: CONNECTION_ERROR (timeout/ulaşılamadı), 4: MAPPING_ERROR (alan eşleştirme hatası)';
COMMENT ON COLUMN message_proxy.proxy_message_log.error_message IS 'Hata mesajı';

-- ---------------------------------------------------------------------
-- swagger_source : Endpoint'leri okunacak servislerin swagger/OpenAPI adresleri
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS message_proxy.swagger_source (
    -- <BASE_COLUMNS>
    id                      uuid                        NOT NULL DEFAULT gen_random_uuid(),
    id_serial               SERIAL                      NOT NULL,
    etag                    uuid                        NOT NULL DEFAULT gen_random_uuid(),
    status                  smallint                    NOT NULL DEFAULT 3,
    date_of_recorded        timestamp without time zone NOT NULL DEFAULT now(),
    user_who_recorded       character varying(50)       NOT NULL DEFAULT '-'::character varying,
    date_of_last_updated    timestamp without time zone NOT NULL DEFAULT now(),
    user_who_last_updated   character varying(50)       NOT NULL DEFAULT '-'::character varying,
    counter_of_unique_data  bigint                      NOT NULL DEFAULT 0,
    -- Tabloya özel alanlar
    source_code             character varying(50)       NOT NULL,
    source_description      character varying(150)      NOT NULL DEFAULT '-'::character varying,
    swagger_url             character varying(500)      NOT NULL,
    target_base_url         character varying(500)      NOT NULL DEFAULT '-'::character varying,
    auto_sync               smallint                    NOT NULL DEFAULT 1,
    last_sync_date          timestamp without time zone,
    last_sync_result        smallint                    NOT NULL DEFAULT 0,
    last_sync_message       character varying(2000)     NOT NULL DEFAULT '-'::character varying,
    endpoint_count          integer                     NOT NULL DEFAULT 0,
    CONSTRAINT swagger_source_pkey PRIMARY KEY (id),
    CONSTRAINT uk0_swagger_source UNIQUE (id_serial),
    CONSTRAINT uk1_swagger_source UNIQUE (source_code, counter_of_unique_data)
);

COMMENT ON TABLE  message_proxy.swagger_source IS 'Endpoint listesi swagger/OpenAPI dokümanından okunacak servisler';
COMMENT ON COLUMN message_proxy.swagger_source.id_serial IS 'Sıralı ID için. Sorgulardaki sıralama kolaylığı için';
COMMENT ON COLUMN message_proxy.swagger_source.etag IS 'Versiyon kontrolü yaparak aynı anda tek transaction a müsade etmek için';
COMMENT ON COLUMN message_proxy.swagger_source.status IS '1: CANCELLED, 2: PASSIVE, 3: ACTIVE, 4: CLOSED';
COMMENT ON COLUMN message_proxy.swagger_source.counter_of_unique_data IS 'CANCELLED kayıtlarda sequence ile artırılır; aynı iş anahtarı tekrar eklenebilsin diye UK''ya dahildir';
COMMENT ON COLUMN message_proxy.swagger_source.source_code IS 'Servis kodu';
COMMENT ON COLUMN message_proxy.swagger_source.source_description IS 'Açıklaması';
COMMENT ON COLUMN message_proxy.swagger_source.swagger_url IS 'swagger-ui adresi (…/swagger-ui/index.html) veya doğrudan doküman adresi (…/v3/api-docs)';
COMMENT ON COLUMN message_proxy.swagger_source.target_base_url IS 'Endpoint tam adresleri için taban URL; ''-'' ise dokümandaki servers/host bilgisi kullanılır';
COMMENT ON COLUMN message_proxy.swagger_source.auto_sync IS '0: Hayır, 1: Evet - zamanlanmış senkronizasyona dahil mi';
COMMENT ON COLUMN message_proxy.swagger_source.last_sync_date IS 'Son senkronizasyon zamanı';
COMMENT ON COLUMN message_proxy.swagger_source.last_sync_result IS '0: NEVER, 1: SUCCESS, 2: ERROR';
COMMENT ON COLUMN message_proxy.swagger_source.last_sync_message IS 'Son senkronizasyon özeti veya hata mesajı';
COMMENT ON COLUMN message_proxy.swagger_source.endpoint_count IS 'Son başarılı senkronizasyonda dokümanda bulunan endpoint sayısı';

-- ---------------------------------------------------------------------
-- swagger_endpoint : Swagger dokümanından okunan endpoint'ler (senkron servisi yazar)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS message_proxy.swagger_endpoint (
    -- <BASE_COLUMNS>
    id                      uuid                        NOT NULL DEFAULT gen_random_uuid(),
    id_serial               SERIAL                      NOT NULL,
    etag                    uuid                        NOT NULL DEFAULT gen_random_uuid(),
    status                  smallint                    NOT NULL DEFAULT 3,
    date_of_recorded        timestamp without time zone NOT NULL DEFAULT now(),
    user_who_recorded       character varying(50)       NOT NULL DEFAULT '-'::character varying,
    date_of_last_updated    timestamp without time zone NOT NULL DEFAULT now(),
    user_who_last_updated   character varying(50)       NOT NULL DEFAULT '-'::character varying,
    counter_of_unique_data  bigint                      NOT NULL DEFAULT 0,
    -- Tabloya özel alanlar
    swagger_source_id       uuid                        NOT NULL,
    api_group               character varying(100)      NOT NULL DEFAULT '-'::character varying,
    http_method             character varying(10)       NOT NULL,
    path                    character varying(1000)     NOT NULL,
    full_url                character varying(1500)     NOT NULL DEFAULT '-'::character varying,
    operation_id            character varying(200)      NOT NULL DEFAULT '-'::character varying,
    summary                 character varying(500)      NOT NULL DEFAULT '-'::character varying,
    description             text,
    tags                    character varying(500)      NOT NULL DEFAULT '-'::character varying,
    deprecated              smallint                    NOT NULL DEFAULT 0,
    request_content_type    character varying(500)      NOT NULL DEFAULT '-'::character varying,
    parameters_json         text,
    request_body_json       text,
    responses_json          text,
    last_seen_date          timestamp without time zone NOT NULL DEFAULT now(),
    CONSTRAINT swagger_endpoint_pkey PRIMARY KEY (id),
    CONSTRAINT uk0_swagger_endpoint UNIQUE (id_serial),
    CONSTRAINT uk1_swagger_endpoint UNIQUE (swagger_source_id, api_group, http_method, path, counter_of_unique_data),
    CONSTRAINT fk1_swagger_endpoint FOREIGN KEY (swagger_source_id) REFERENCES message_proxy.swagger_source (id)
);

CREATE INDEX IF NOT EXISTS ix1_swagger_endpoint ON message_proxy.swagger_endpoint (swagger_source_id, status);

COMMENT ON TABLE  message_proxy.swagger_endpoint IS 'Swagger/OpenAPI dokümanından okunan endpoint''ler';
COMMENT ON COLUMN message_proxy.swagger_endpoint.status IS '1: CANCELLED (dokümandan kalktı), 2: PASSIVE, 3: ACTIVE, 4: CLOSED';
COMMENT ON COLUMN message_proxy.swagger_endpoint.counter_of_unique_data IS 'CANCELLED kayıtlarda sequence ile artırılır; endpoint dokümana geri gelirse yeni satır açılır';
COMMENT ON COLUMN message_proxy.swagger_endpoint.swagger_source_id IS 'Endpoint''in okunduğu kaynak (swagger_source.id)';
COMMENT ON COLUMN message_proxy.swagger_endpoint.api_group IS 'springdoc grup adı; grup yoksa ''-''';
COMMENT ON COLUMN message_proxy.swagger_endpoint.http_method IS 'GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS, TRACE';
COMMENT ON COLUMN message_proxy.swagger_endpoint.path IS 'Dokümandaki path (ör. /api/v1/orders/{id})';
COMMENT ON COLUMN message_proxy.swagger_endpoint.full_url IS 'Taban URL + path';
COMMENT ON COLUMN message_proxy.swagger_endpoint.operation_id IS 'OpenAPI operationId';
COMMENT ON COLUMN message_proxy.swagger_endpoint.summary IS 'OpenAPI summary';
COMMENT ON COLUMN message_proxy.swagger_endpoint.description IS 'OpenAPI description';
COMMENT ON COLUMN message_proxy.swagger_endpoint.tags IS 'Virgülle ayrılmış tag listesi';
COMMENT ON COLUMN message_proxy.swagger_endpoint.deprecated IS '0: Hayır, 1: Evet';
COMMENT ON COLUMN message_proxy.swagger_endpoint.request_content_type IS 'İstek gövdesi içerik tipleri (virgülle ayrılmış)';
COMMENT ON COLUMN message_proxy.swagger_endpoint.parameters_json IS 'Path/query/header parametreleri (JSON)';
COMMENT ON COLUMN message_proxy.swagger_endpoint.request_body_json IS 'İstek gövdesi tanımı (JSON)';
COMMENT ON COLUMN message_proxy.swagger_endpoint.responses_json IS 'Cevap tanımları (JSON)';
COMMENT ON COLUMN message_proxy.swagger_endpoint.last_seen_date IS 'Endpoint''in dokümanda en son görüldüğü senkronizasyon zamanı';

-- ---------------------------------------------------------------------
-- auth_profile : Korunan servislere erişim için token alma tanımları
-- (şimdilik swagger dokümanı okurken kullanılır: swagger_source.auth_profile_id)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS message_proxy.auth_profile (
    -- <BASE_COLUMNS>
    id                      uuid                        NOT NULL DEFAULT gen_random_uuid(),
    id_serial               SERIAL                      NOT NULL,
    etag                    uuid                        NOT NULL DEFAULT gen_random_uuid(),
    status                  smallint                    NOT NULL DEFAULT 3,
    date_of_recorded        timestamp without time zone NOT NULL DEFAULT now(),
    user_who_recorded       character varying(50)       NOT NULL DEFAULT '-'::character varying,
    date_of_last_updated    timestamp without time zone NOT NULL DEFAULT now(),
    user_who_last_updated   character varying(50)       NOT NULL DEFAULT '-'::character varying,
    counter_of_unique_data  bigint                      NOT NULL DEFAULT 0,
    -- Tabloya özel alanlar
    profile_code            character varying(50)       NOT NULL,
    profile_description     character varying(150)      NOT NULL DEFAULT '-'::character varying,
    token_url               character varying(500)      NOT NULL,
    token_method            smallint                    NOT NULL DEFAULT 2,
    content_type            smallint                    NOT NULL DEFAULT 1,
    grant_type              character varying(50)       NOT NULL DEFAULT 'password'::character varying,
    client_id               character varying(100)      NOT NULL DEFAULT '-'::character varying,
    client_secret           character varying(1000),
    username                character varying(100)      NOT NULL DEFAULT '-'::character varying,
    password                character varying(1000),
    extra_body_json         text,
    custom_headers_json     text,
    token_field             character varying(100)      NOT NULL DEFAULT 'access_token'::character varying,
    expires_field           character varying(100)      NOT NULL DEFAULT 'expires_in'::character varying,
    default_ttl_seconds     integer                     NOT NULL DEFAULT 300,
    token_prefix            character varying(20)       NOT NULL DEFAULT 'Bearer'::character varying,
    connect_timeout_ms      integer                     NOT NULL DEFAULT 5000,
    read_timeout_ms         integer                     NOT NULL DEFAULT 30000,
    CONSTRAINT auth_profile_pkey PRIMARY KEY (id),
    CONSTRAINT uk0_auth_profile UNIQUE (id_serial),
    CONSTRAINT uk1_auth_profile UNIQUE (profile_code, counter_of_unique_data)
);

COMMENT ON TABLE  message_proxy.auth_profile IS 'Korunan servislere erişim için token alma tanımları';
COMMENT ON COLUMN message_proxy.auth_profile.id_serial IS 'Sıralı ID için. Sorgulardaki sıralama kolaylığı için';
COMMENT ON COLUMN message_proxy.auth_profile.etag IS 'Versiyon kontrolü yaparak aynı anda tek transaction a müsade etmek için';
COMMENT ON COLUMN message_proxy.auth_profile.status IS '1: CANCELLED, 2: PASSIVE, 3: ACTIVE, 4: CLOSED';
COMMENT ON COLUMN message_proxy.auth_profile.counter_of_unique_data IS 'CANCELLED kayıtlarda sequence ile artırılır; aynı iş anahtarı tekrar eklenebilsin diye UK''ya dahildir';
COMMENT ON COLUMN message_proxy.auth_profile.profile_code IS 'Tanım kodu';
COMMENT ON COLUMN message_proxy.auth_profile.profile_description IS 'Açıklaması';
COMMENT ON COLUMN message_proxy.auth_profile.token_url IS 'Token alınacak adres';
COMMENT ON COLUMN message_proxy.auth_profile.token_method IS 'Token isteğinin metodu. 2: POST, 3: PUT';
COMMENT ON COLUMN message_proxy.auth_profile.content_type IS 'Token isteği gövde biçimi. 1: JSON (application/json), 2: FORM (application/x-www-form-urlencoded)';
COMMENT ON COLUMN message_proxy.auth_profile.grant_type IS 'Gövdedeki grant_type; ''-'' ise gönderilmez';
COMMENT ON COLUMN message_proxy.auth_profile.client_id IS 'Gövdedeki client_id; ''-'' ise gönderilmez';
COMMENT ON COLUMN message_proxy.auth_profile.client_secret IS 'Gövdedeki client_secret; boşsa gönderilmez. API ile kaydedilince şifreli (enc:v1:) saklanır';
COMMENT ON COLUMN message_proxy.auth_profile.username IS 'Gövdedeki username; ''-'' ise gönderilmez';
COMMENT ON COLUMN message_proxy.auth_profile.password IS 'Gövdedeki password; boşsa gönderilmez. API ile kaydedilince şifreli (enc:v1:) saklanır';
COMMENT ON COLUMN message_proxy.auth_profile.extra_body_json IS 'Gövdeye eklenecek diğer alanlar (JSON nesnesi)';
COMMENT ON COLUMN message_proxy.auth_profile.custom_headers_json IS 'Token isteğine eklenecek header''lar (JSON nesnesi). Örn: {"realm":"zenergy"}';
COMMENT ON COLUMN message_proxy.auth_profile.token_field IS 'Token cevabında access token alanının yolu. İç içe için nokta: data.access_token';
COMMENT ON COLUMN message_proxy.auth_profile.expires_field IS 'Token cevabında geçerlilik süresi (saniye) alanının yolu';
COMMENT ON COLUMN message_proxy.auth_profile.default_ttl_seconds IS 'Cevapta süre yoksa token kaç saniye kullanılsın';
COMMENT ON COLUMN message_proxy.auth_profile.token_prefix IS 'Authorization header değerinin öneki. Örn: Bearer';
COMMENT ON COLUMN message_proxy.auth_profile.connect_timeout_ms IS 'Token isteği bağlantı timeout (ms)';
COMMENT ON COLUMN message_proxy.auth_profile.read_timeout_ms IS 'Token isteği cevap okuma timeout (ms)';

-- swagger_source → auth_profile (NULL: swagger dokümanı token istemiyor).
-- Kolon ve FK birlikte eklenir; kolon zaten varsa ifadenin tamamı atlanır (idempotent).
ALTER TABLE message_proxy.swagger_source ADD COLUMN IF NOT EXISTS auth_profile_id uuid
    CONSTRAINT fk1_swagger_source REFERENCES message_proxy.auth_profile (id);
COMMENT ON COLUMN message_proxy.swagger_source.auth_profile_id IS 'Swagger dokümanı okunurken kullanılacak token tanımı (auth_profile.id); NULL ise token gönderilmez';

-- ---------------------------------------------------------------------
-- app_registry : Entegre olunan uygulamalar (adres, base path, swagger, amaç)
-- swagger_source ve proxy_route bu tabloya bağlanır.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS message_proxy.app_registry (
    -- <BASE_COLUMNS>
    id                      uuid                        NOT NULL DEFAULT gen_random_uuid(),
    id_serial               SERIAL                      NOT NULL,
    etag                    uuid                        NOT NULL DEFAULT gen_random_uuid(),
    status                  smallint                    NOT NULL DEFAULT 3,
    date_of_recorded        timestamp without time zone NOT NULL DEFAULT now(),
    user_who_recorded       character varying(50)       NOT NULL DEFAULT '-'::character varying,
    date_of_last_updated    timestamp without time zone NOT NULL DEFAULT now(),
    user_who_last_updated   character varying(50)       NOT NULL DEFAULT '-'::character varying,
    counter_of_unique_data  bigint                      NOT NULL DEFAULT 0,
    -- Tabloya özel alanlar
    app_code                character varying(50)       NOT NULL,
    app_name                character varying(150)      NOT NULL,
    app_purpose             text,
    app_owner               character varying(100)      NOT NULL DEFAULT '-'::character varying,
    base_url                character varying(500)      NOT NULL,
    base_path               character varying(200)      NOT NULL DEFAULT '-'::character varying,
    has_swagger             smallint                    NOT NULL DEFAULT 0,
    swagger_path            character varying(300)      NOT NULL DEFAULT '/swagger-ui/index.html'::character varying,
    auth_profile_id         uuid,
    CONSTRAINT app_registry_pkey PRIMARY KEY (id),
    CONSTRAINT uk0_app_registry UNIQUE (id_serial),
    CONSTRAINT uk1_app_registry UNIQUE (app_code, counter_of_unique_data),
    CONSTRAINT fk1_app_registry FOREIGN KEY (auth_profile_id) REFERENCES message_proxy.auth_profile (id)
);

COMMENT ON TABLE  message_proxy.app_registry IS 'Entegre olunan uygulamalar; swagger_source ve proxy_route bu tabloya bağlanır';
COMMENT ON COLUMN message_proxy.app_registry.id_serial IS 'Sıralı ID için. Sorgulardaki sıralama kolaylığı için';
COMMENT ON COLUMN message_proxy.app_registry.etag IS 'Versiyon kontrolü yaparak aynı anda tek transaction a müsade etmek için';
COMMENT ON COLUMN message_proxy.app_registry.status IS '1: CANCELLED, 2: PASSIVE, 3: ACTIVE, 4: CLOSED';
COMMENT ON COLUMN message_proxy.app_registry.counter_of_unique_data IS 'CANCELLED kayıtlarda sequence ile artırılır; aynı iş anahtarı tekrar eklenebilsin diye UK''ya dahildir';
COMMENT ON COLUMN message_proxy.app_registry.app_code IS 'Uygulama kodu';
COMMENT ON COLUMN message_proxy.app_registry.app_name IS 'Uygulama adı';
COMMENT ON COLUMN message_proxy.app_registry.app_purpose IS 'Uygulamanın amacı';
COMMENT ON COLUMN message_proxy.app_registry.app_owner IS 'Sorumlu ekip / kişi';
COMMENT ON COLUMN message_proxy.app_registry.base_url IS 'Şema + host + port. Örn: http://localhost:7261';
COMMENT ON COLUMN message_proxy.app_registry.base_path IS 'Context path; yoksa ''-''. Örn: /erp. Tam adres = base_url + base_path';
COMMENT ON COLUMN message_proxy.app_registry.has_swagger IS '0: Hayır, 1: Evet - 1 ise swagger_source otomatik oluşturulur / güncellenir';
COMMENT ON COLUMN message_proxy.app_registry.swagger_path IS 'Swagger adresinin tam adrese göre yolu. Örn: /swagger-ui/index.html veya /v3/api-docs';
COMMENT ON COLUMN message_proxy.app_registry.auth_profile_id IS 'Swagger dokümanı okunurken kullanılacak token tanımı (auth_profile.id)';

-- swagger_source → app_registry (1:1; NULL: uygulamaya bağlı olmayan, elle yönetilen kaynak)
ALTER TABLE message_proxy.swagger_source ADD COLUMN IF NOT EXISTS app_registry_id uuid
    CONSTRAINT fk2_swagger_source REFERENCES message_proxy.app_registry (id);
COMMENT ON COLUMN message_proxy.swagger_source.app_registry_id IS 'Kaynağın ait olduğu uygulama (app_registry.id); doluysa adres/token alanları uygulamadan yönetilir';
-- uk2: bir uygulamanın tek (iptal edilmemiş) swagger kaynağı olur
CREATE UNIQUE INDEX IF NOT EXISTS uk2_swagger_source
    ON message_proxy.swagger_source (app_registry_id, counter_of_unique_data)
    WHERE app_registry_id IS NOT NULL;

-- proxy_route → app_registry
ALTER TABLE message_proxy.proxy_route ADD COLUMN IF NOT EXISTS target_app_registry_id uuid
    CONSTRAINT fk1_proxy_route REFERENCES message_proxy.app_registry (id);
ALTER TABLE message_proxy.proxy_route ADD COLUMN IF NOT EXISTS source_app_registry_id uuid
    CONSTRAINT fk2_proxy_route REFERENCES message_proxy.app_registry (id);
COMMENT ON COLUMN message_proxy.proxy_route.target_app_registry_id IS 'İsteğin iletildiği uygulama; doluysa target_url göreli (/ ile başlayan) yazılabilir: tam adres = uygulama adresi + target_url';
COMMENT ON COLUMN message_proxy.proxy_route.source_app_registry_id IS 'Bu route''u çağıran uygulama (bilgi amaçlı; entegrasyon haritası için)';
CREATE INDEX IF NOT EXISTS ix1_proxy_route ON message_proxy.proxy_route (target_app_registry_id);
CREATE INDEX IF NOT EXISTS ix2_proxy_route ON message_proxy.proxy_route (source_app_registry_id);
