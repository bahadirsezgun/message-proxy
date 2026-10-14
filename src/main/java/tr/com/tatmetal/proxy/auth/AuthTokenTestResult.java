package tr.com.tatmetal.proxy.auth;

import java.util.UUID;

/**
 * POST /api/v1/auth-profile/{id}/test cevabı. Token'ın tamamı bilerek dönmez.
 */
public class AuthTokenTestResult {

    private UUID authProfileId;
    private String profileCode;
    private boolean success;
    /** Token servisinin HTTP status'u; ulaşılamadıysa 0. */
    private int httpStatus;
    private String message;
    /** Token'ın ilk 10 karakteri + "…" (doğru token geldiğini görmek için). */
    private String tokenPreview;
    /** Token'ın kullanılacağı süre (saniye). */
    private long expiresInSeconds;
    private long durationMs;

    public AuthTokenTestResult() {
    }

    public UUID getAuthProfileId() {
        return authProfileId;
    }

    public void setAuthProfileId(UUID authProfileId) {
        this.authProfileId = authProfileId;
    }

    public String getProfileCode() {
        return profileCode;
    }

    public void setProfileCode(String profileCode) {
        this.profileCode = profileCode;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTokenPreview() {
        return tokenPreview;
    }

    public void setTokenPreview(String tokenPreview) {
        this.tokenPreview = tokenPreview;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    @Override
    public String toString() {
        return "AuthTokenTestResult{"
                + "profileCode='" + profileCode + '\''
                + ", success=" + success
                + ", httpStatus=" + httpStatus
                + ", message='" + message + '\''
                + ", expiresInSeconds=" + expiresInSeconds
                + ", durationMs=" + durationMs
                + '}';
    }
}
