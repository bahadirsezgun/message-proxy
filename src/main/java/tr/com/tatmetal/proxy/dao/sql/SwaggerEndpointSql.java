package tr.com.tatmetal.proxy.dao.sql;

public final class SwaggerEndpointSql {

    private SwaggerEndpointSql() {
    }

    public static final String SWAGGER_ENDPOINT_GET_ONE_SQL =
            "SELECT * FROM message_proxy.swagger_endpoint WHERE id=:id and etag=:etag";

    /** getByCondition için taban sorgu; koşullar ParamsToCondition ile eklenir. */
    public static final String SWAGGER_ENDPOINT_GET_SQL =
            "SELECT * FROM message_proxy.swagger_endpoint";

    /**
     * Senkronizasyon zamanı veritabanından alınır (transaction başlangıç zamanı). Birden fazla uygulama
     * instance'ı arasındaki saat farkı "dokümandan kalkan endpoint" tespitini bozmasın diye.
     */
    public static final String SWAGGER_ENDPOINT_SYNC_TIME_SQL = "SELECT LOCALTIMESTAMP";

    /**
     * Aynı kaynağın aynı anda iki kez senkronize edilmesini engeller (zamanlayıcı + elle tetikleme,
     * ya da birden fazla uygulama instance'ı). Kilit transaction bitince kendiliğinden bırakılır.
     */
    public static final String SWAGGER_ENDPOINT_TRY_LOCK_SQL =
            "SELECT pg_try_advisory_xact_lock(hashtext(:lockKey))";

    /** Upsert içinde "içerik değişti mi" kontrolü (sadece SWAGGER_ENDPOINT_UPSERT_SQL içinde kullanılır). */
    private static final String CHANGED =
            "((t.full_url, t.operation_id, t.summary, t.description, t.tags, t.deprecated, "
                    + "t.request_content_type, t.parameters_json, t.request_body_json, t.responses_json) "
                    + "IS DISTINCT FROM "
                    + "(EXCLUDED.full_url, EXCLUDED.operation_id, EXCLUDED.summary, EXCLUDED.description, "
                    + "EXCLUDED.tags, EXCLUDED.deprecated, EXCLUDED.request_content_type, "
                    + "EXCLUDED.parameters_json, EXCLUDED.request_body_json, EXCLUDED.responses_json))";

    /**
     * Endpoint'i ekler ya da günceller (upsert).
     * <ul>
     *   <li>İş anahtarı: swagger_source_id + api_group + http_method + path (+ counter_of_unique_data = 0).</li>
     *   <li>Her senkronizasyonda last_seen_date güncellenir.</li>
     *   <li>etag, date_of_last_updated ve user_who_last_updated sadece içerik gerçekten değiştiyse yenilenir.</li>
     *   <li>status'a dokunulmaz; elle PASSIVE yapılmış endpoint PASSIVE kalır.</li>
     * </ul>
     * Dönen satır: inserted = yeni kayıt mı, changed = içerik değişti mi.
     */
    public static final String SWAGGER_ENDPOINT_UPSERT_SQL =
            "INSERT INTO message_proxy.swagger_endpoint AS t ("
                    + "swagger_source_id, api_group, http_method, path, full_url, operation_id, summary, "
                    + "description, tags, deprecated, request_content_type, parameters_json, "
                    + "request_body_json, responses_json, last_seen_date, "
                    + "date_of_recorded, user_who_recorded, date_of_last_updated, user_who_last_updated"
                    + ") VALUES ("
                    + ":swaggerSourceId, :apiGroup, :httpMethod, :path, :fullUrl, :operationId, :summary, "
                    + ":description, :tags, :deprecated, :requestContentType, :parametersJson, "
                    + ":requestBodyJson, :responsesJson, :syncTime, "
                    + ":syncTime, :user, :syncTime, :user"
                    + ") ON CONFLICT ON CONSTRAINT uk1_swagger_endpoint DO UPDATE SET "
                    + "last_seen_date = EXCLUDED.last_seen_date, "
                    + "etag = CASE WHEN " + CHANGED + " THEN gen_random_uuid() ELSE t.etag END, "
                    + "date_of_last_updated = CASE WHEN " + CHANGED
                    + " THEN EXCLUDED.date_of_last_updated ELSE t.date_of_last_updated END, "
                    + "user_who_last_updated = CASE WHEN " + CHANGED
                    + " THEN EXCLUDED.user_who_last_updated ELSE t.user_who_last_updated END, "
                    + "full_url = EXCLUDED.full_url, "
                    + "operation_id = EXCLUDED.operation_id, "
                    + "summary = EXCLUDED.summary, "
                    + "description = EXCLUDED.description, "
                    + "tags = EXCLUDED.tags, "
                    + "deprecated = EXCLUDED.deprecated, "
                    + "request_content_type = EXCLUDED.request_content_type, "
                    + "parameters_json = EXCLUDED.parameters_json, "
                    + "request_body_json = EXCLUDED.request_body_json, "
                    + "responses_json = EXCLUDED.responses_json "
                    + "RETURNING (xmax = 0) AS inserted, (date_of_last_updated = :syncTime) AS changed";

    /**
     * Bu senkronizasyonda dokümanda görülmeyen (kaldırılmış) endpoint'leri CANCELLED yapar.
     * Standart gereği silinmez; counter_of_unique_data genel sequence ile artırılır ki endpoint
     * dokümana geri gelirse yeni satır olarak eklenebilsin.
     */
    public static final String SWAGGER_ENDPOINT_CANCEL_MISSING_SQL =
            "UPDATE message_proxy.swagger_endpoint SET "
                    + "status = 1, "
                    + "counter_of_unique_data = nextval('message_proxy.counter_of_unique_data_seq'), "
                    + "etag = gen_random_uuid(), "
                    + "date_of_last_updated = :syncTime, "
                    + "user_who_last_updated = :user "
                    + "WHERE swagger_source_id = :swaggerSourceId "
                    + "AND status <> 1 "
                    + "AND last_seen_date < :syncTime";
}
