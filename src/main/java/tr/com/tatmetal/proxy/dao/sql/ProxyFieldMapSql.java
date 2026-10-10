package tr.com.tatmetal.proxy.dao.sql;

public final class ProxyFieldMapSql {

    private ProxyFieldMapSql() {
    }

    public static final String PROXY_FIELD_MAP_GET_ONE_SQL =
            "SELECT * FROM message_proxy.proxy_field_map WHERE id=:id and etag=:etag";

    /** getByCondition için taban sorgu; koşullar ParamsToCondition ile eklenir. */
    public static final String PROXY_FIELD_MAP_GET_SQL =
            "SELECT * FROM message_proxy.proxy_field_map";

    /** Redis'e yüklenecek, aktif route'lara ait aktif eşleştirmeler. */
    public static final String PROXY_FIELD_MAP_GET_ACTIVE_SQL =
            "SELECT m.* FROM message_proxy.proxy_field_map m"
                    + " JOIN message_proxy.proxy_route r ON r.id = m.proxy_route_id"
                    + " WHERE m.status = 3 AND r.status = 3"
                    + " ORDER BY m.proxy_route_id, m.map_direction, m.field_order, m.id_serial";
}
