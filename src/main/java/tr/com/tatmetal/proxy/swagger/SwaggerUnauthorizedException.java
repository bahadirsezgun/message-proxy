package tr.com.tatmetal.proxy.swagger;

/**
 * Swagger dokümanı okunamadı ve denemelerden en az biri 401 / 403 döndü.
 * Token gönderildiyse çağıran token'ı yenileyip bir kez daha deneyebilir.
 */
public class SwaggerUnauthorizedException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    /** İstekte Authorization header'ı var mıydı. */
    private final boolean tokenSent;

    public SwaggerUnauthorizedException(String message, boolean tokenSent) {
        super(message);
        this.tokenSent = tokenSent;
    }

    public boolean isTokenSent() {
        return tokenSent;
    }
}
