package tr.com.tatmetal.proxy.exception;

/**
 * Alan eşleştirmesi yapılamadığında fırlatılır (zorunlu alan yok, dizi beklenen yerde dizi yok, hatalı tanım vb.).
 */
public class ProxyMappingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProxyMappingException(String message) {
        super(message);
    }

    public ProxyMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
