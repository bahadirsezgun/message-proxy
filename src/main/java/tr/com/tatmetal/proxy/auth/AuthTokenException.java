package tr.com.tatmetal.proxy.auth;

/**
 * Token alınamadığında fırlatılır. Mesaj, gizli değer (şifre, token) içermez.
 */
public class AuthTokenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Token servisinin döndüğü HTTP status; ulaşılamadıysa 0. */
    private final int httpStatus;

    public AuthTokenException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public AuthTokenException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
