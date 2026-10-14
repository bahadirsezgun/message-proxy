package tr.com.tatmetal.proxy.dao.sql;

public final class AuthProfileSql {

    private AuthProfileSql() {
    }

    public static final String AUTH_PROFILE_GET_ONE_SQL =
            "SELECT * FROM message_proxy.auth_profile WHERE id=:id and etag=:etag";

    /** getByCondition için taban sorgu; koşullar ParamsToCondition ile eklenir. */
    public static final String AUTH_PROFILE_GET_SQL =
            "SELECT * FROM message_proxy.auth_profile";

    /** Token alırken / kaynağa bağlarken etag'siz tekil okuma (CANCELLED hariç). */
    public static final String AUTH_PROFILE_GET_BY_ID_SQL =
            "SELECT * FROM message_proxy.auth_profile WHERE id=:id AND status<>1";
}
