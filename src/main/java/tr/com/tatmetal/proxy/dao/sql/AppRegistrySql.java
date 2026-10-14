package tr.com.tatmetal.proxy.dao.sql;

public final class AppRegistrySql {

    private AppRegistrySql() {
    }

    public static final String APP_REGISTRY_GET_ONE_SQL =
            "SELECT * FROM message_proxy.app_registry WHERE id=:id and etag=:etag";

    /** getByCondition için taban sorgu; koşullar ParamsToCondition ile eklenir. */
    public static final String APP_REGISTRY_GET_SQL =
            "SELECT * FROM message_proxy.app_registry";

    /** etag'siz tekil okuma (CANCELLED hariç). */
    public static final String APP_REGISTRY_GET_BY_ID_SQL =
            "SELECT * FROM message_proxy.app_registry WHERE id=:id AND status<>1";

    /** İptal edilmemiş tüm uygulamalar (route adreslerini çözmek ve genel bakış için). */
    public static final String APP_REGISTRY_GET_NOT_CANCELLED_SQL =
            "SELECT * FROM message_proxy.app_registry WHERE status<>1 ORDER BY app_code";
}
