package tr.com.tatmetal.proxy.dao.sql;

public final class SwaggerSourceSql {

    private SwaggerSourceSql() {
    }

    public static final String SWAGGER_SOURCE_GET_ONE_SQL =
            "SELECT * FROM message_proxy.swagger_source WHERE id=:id and etag=:etag";

    /** getByCondition için taban sorgu; koşullar ParamsToCondition ile eklenir. */
    public static final String SWAGGER_SOURCE_GET_SQL =
            "SELECT * FROM message_proxy.swagger_source";

    /** Senkronizasyon için etag'siz tekil okuma (CANCELLED hariç). */
    public static final String SWAGGER_SOURCE_GET_BY_ID_SQL =
            "SELECT * FROM message_proxy.swagger_source WHERE id=:id AND status<>1";

    /** Uygulamaya bağlı (iptal edilmemiş) swagger kaynağı. */
    public static final String SWAGGER_SOURCE_GET_BY_APP_SQL =
            "SELECT * FROM message_proxy.swagger_source WHERE app_registry_id=:appRegistryId AND status<>1";

    /** Aynı kodlu (iptal edilmemiş) swagger kaynağı; uygulama oluşturulurken mevcut kaynağı sahiplenmek için. */
    public static final String SWAGGER_SOURCE_GET_BY_CODE_SQL =
            "SELECT * FROM message_proxy.swagger_source WHERE source_code=:sourceCode AND status<>1";

    /** Zamanlanmış senkronizasyona girecek kaynaklar: ACTIVE ve auto_sync = 1. */
    public static final String SWAGGER_SOURCE_GET_AUTO_SYNC_SQL =
            "SELECT * FROM message_proxy.swagger_source WHERE status=3 AND auto_sync=1 ORDER BY id_serial";

    /**
     * Senkronizasyon sonucunu yazar. Sistem tarafından yazılan durum bilgisi olduğu için etag
     * bilerek değiştirilmez; aksi halde kaynağı ekranda düzenleyen kullanıcının etag'i her
     * senkronizasyonda geçersiz kalır. endpoint_count NULL gelirse eski değer korunur (hata durumu).
     */
    public static final String SWAGGER_SOURCE_UPDATE_SYNC_RESULT_SQL =
            "UPDATE message_proxy.swagger_source SET "
                    + "last_sync_date=:lastSyncDate, "
                    + "last_sync_result=:lastSyncResult, "
                    + "last_sync_message=:lastSyncMessage, "
                    + "endpoint_count=COALESCE(CAST(:endpointCount AS integer), endpoint_count) "
                    + "WHERE id=:id";
}
