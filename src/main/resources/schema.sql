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
    target_url              character varying(500)      NOT NULL,
    target_method           smallint                    NOT NULL DEFAULT 0,
    connect_timeout_ms      integer                     NOT NULL DEFAULT 5000,
    read_timeout_ms         integer                     NOT NULL DEFAULT 30000,
    log_body                smallint                    NOT NULL DEFAULT 1,
    CONSTRAINT proxy_route_pkey PRIMARY KEY (id),
    CONSTRAINT uk0_proxy_route UNIQUE (id_serial),
    CONSTRAINT uk1_proxy_route UNIQUE (route_code, counter_of_unique_data)
);

COMMENT ON TABLE  message_proxy.proxy_route IS 'Gelen mesajın iletileceği hedef tanımları';
COMMENT ON COLUMN message_proxy.proxy_route.id_serial IS 'Sıralı ID için. Sorgulardaki sıralama kolaylığı için';
COMMENT ON COLUMN message_proxy.proxy_route.etag IS 'Versiyon kontrolü yaparak aynı anda tek transaction a müsade etmek için';
COMMENT ON COLUMN message_proxy.proxy_route.status IS '1: CANCELLED, 2: PASSIVE, 3: ACTIVE, 4: CLOSED';
COMMENT ON COLUMN message_proxy.proxy_route.counter_of_unique_data IS 'CANCELLED kayıtlarda sequence ile artırılır; aynı iş anahtarı tekrar eklenebilsin diye UK''ya dahildir';
COMMENT ON COLUMN message_proxy.proxy_route.route_code IS 'Route kodu (gelen istekte kullanılacak anahtar)';
COMMENT ON COLUMN message_proxy.proxy_route.route_description IS 'Açıklaması';
COMMENT ON COLUMN message_proxy.proxy_route.target_url IS 'Mesajın iletileceği hedef URL';
COMMENT ON COLUMN message_proxy.proxy_route.target_method IS '0: PASS_THROUGH, 1: GET, 2: POST, 3: PUT, 4: PATCH, 5: DELETE';
COMMENT ON COLUMN message_proxy.proxy_route.connect_timeout_ms IS 'Hedefe bağlantı timeout (ms)';
COMMENT ON COLUMN message_proxy.proxy_route.read_timeout_ms IS 'Hedeften cevap okuma timeout (ms)';
COMMENT ON COLUMN message_proxy.proxy_route.log_body IS '0: Hayır, 1: Evet - mesaj gövdesi loglansın mı';

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
COMMENT ON COLUMN message_proxy.proxy_message_log.result_type IS '1: SUCCESS (2xx/3xx), 2: TARGET_ERROR (4xx/5xx), 3: CONNECTION_ERROR (timeout/ulaşılamadı)';
COMMENT ON COLUMN message_proxy.proxy_message_log.error_message IS 'Hata mesajı';
