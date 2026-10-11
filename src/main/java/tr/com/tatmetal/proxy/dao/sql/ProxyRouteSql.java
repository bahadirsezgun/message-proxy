package tr.com.tatmetal.proxy.dao.sql;

public final class ProxyRouteSql {

    private ProxyRouteSql() {
    }

    public static final String PROXY_ROUTE_GET_ONE_SQL =
            "SELECT * FROM message_proxy.proxy_route WHERE id=:id and etag=:etag";

    /** getByCondition için taban sorgu; koşullar ParamsToCondition ile eklenir. */
    public static final String PROXY_ROUTE_GET_SQL =
            "SELECT * FROM message_proxy.proxy_route";

    /** Bir uygulamayı çağıran ya da ona giden (iptal edilmemiş) route'lar. */
    public static final String PROXY_ROUTE_GET_BY_APP_SQL =
            "SELECT * FROM message_proxy.proxy_route WHERE status <> 1"
                    + " AND (source_app_registry_id = :appRegistryId OR target_app_registry_id = :appRegistryId)"
                    + " ORDER BY route_code";

    /** Herhangi bir uygulamaya bağlı (iptal edilmemiş) tüm route'lar (entegrasyon haritası için). */
    public static final String PROXY_ROUTE_GET_APP_LINKED_SQL =
            "SELECT * FROM message_proxy.proxy_route WHERE status <> 1"
                    + " AND (source_app_registry_id IS NOT NULL OR target_app_registry_id IS NOT NULL)"
                    + " ORDER BY route_code";

    /** Redis'e yüklenecek aktif route'lar (gelen path tanımlı olanlar). */
    public static final String PROXY_ROUTE_GET_ACTIVE_SQL =
            "SELECT * FROM message_proxy.proxy_route WHERE status = 3 AND source_path <> '-' ORDER BY id_serial";
}
