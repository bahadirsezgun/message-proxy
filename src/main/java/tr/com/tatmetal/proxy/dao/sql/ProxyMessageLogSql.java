package tr.com.tatmetal.proxy.dao.sql;

public final class ProxyMessageLogSql {

    private ProxyMessageLogSql() {
    }

    public static final String PROXY_MESSAGE_LOG_GET_ONE_SQL =
            "SELECT * FROM message_proxy.proxy_message_log WHERE id=:id and etag=:etag";

    /** getByCondition için taban sorgu; koşullar ParamsToCondition ile eklenir. */
    public static final String PROXY_MESSAGE_LOG_GET_SQL =
            "SELECT * FROM message_proxy.proxy_message_log";
}
