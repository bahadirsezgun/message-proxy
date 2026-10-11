package tr.com.tatmetal.proxy.auth;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * auth_profile içindeki gizli alanları (password, client_secret) AES-256-GCM ile şifreler / çözer.
 * <p>
 * Anahtar {@code auth.profile.secret-key} (ortam değişkeni {@code AUTH_PROFILE_SECRET_KEY}) değerinin
 * SHA-256 özetidir; yani herhangi bir uzun parola kullanılabilir. Örnek üretim: {@code openssl rand -base64 32}.
 * <p>
 * Saklama biçimi: {@code enc:v1:<base64(12 byte IV + şifreli metin + 16 byte tag)>}.
 * {@code enc:v1:} ile başlamayan değerler düz metin kabul edilir (SQL ile elle girilmiş kayıtlar çalışsın diye).
 * <p>
 * Anahtar değişirse eski şifreli değerler çözülemez; tanımların şifresi API ile yeniden girilmelidir.
 */
@Component
public class AuthSecretCipher {

    public static final String PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();
    private final Logger logger = LoggerFactory.getLogger(AuthSecretCipher.class);

    public AuthSecretCipher(@Value("${auth.profile.secret-key:}") String secretKey) {
        this.key = secretKey == null || secretKey.isBlank() ? null : deriveKey(secretKey);
        if (this.key == null) {
            logger.warn("auth.profile.secret-key (AUTH_PROFILE_SECRET_KEY) tanımlı değil; "
                    + "auth_profile şifre / client_secret içeren kayıtlar API ile kaydedilemez.");
        }
    }

    public boolean isConfigured() {
        return key != null;
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /** Boş değer olduğu gibi döner; zaten şifreliyse tekrar şifrelenmez. */
    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty() || isEncrypted(plain)) {
            return plain;
        }
        requireKey();
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Gizli alan şifrelenemedi: " + e.getClass().getSimpleName(), e);
        }
    }

    /** Şifreli değilse (elle girilmiş düz metin) olduğu gibi döner. */
    public String decrypt(String stored) {
        if (!isEncrypted(stored)) {
            return stored;
        }
        requireKey();
        try {
            byte[] data = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (data.length <= IV_LENGTH) {
                throw new IllegalStateException("Şifreli değer bozuk");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, data, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Gizli alan çözülemedi; auth.profile.secret-key kayıt yapılırkenki "
                    + "anahtarla aynı mı? (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException("auth.profile.secret-key (ortam değişkeni AUTH_PROFILE_SECRET_KEY) "
                    + "tanımlı değil; şifre / client_secret şifrelenemez veya çözülemez.");
        }
    }

    private static SecretKeySpec deriveKey(String secretKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secretKey.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 kullanılamıyor", e);
        }
    }
}
