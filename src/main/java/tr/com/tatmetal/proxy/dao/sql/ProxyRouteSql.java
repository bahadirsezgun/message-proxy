package tr.com.tatmetal.proxy.dao.sql;

public final class ProxyRouteSql {

    private ProxyRouteSql() {
    }

    public static final String PROXY_ROUTE_GET_ONE_SQL =
            "SELECT * FROM message_proxy.proxy_route WHERE id=:id and etag=:etag";

    /** getByCondition için taban sorgu; koşullar ParamsToCondition ile eklenir. */
    public static final String PROXY_ROUTE_GET_SQL =
            "SELECT * FROM message_proxy.proxy_route";

    /** Redis'e yüklenecek aktif route'lar (gelen path tanımlı olanlar). */
    public static final String PROXY_ROUTE_GET_ACTIVE_SQL =
            "SELECT * FROM message_proxy.proxy_route WHERE status = 3 AND source_path <> '-' ORDER BY id_serial";
}
