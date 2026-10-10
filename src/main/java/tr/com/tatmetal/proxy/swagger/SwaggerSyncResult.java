package tr.com.tatmetal.proxy.swagger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import tr.com.tatmetal.proxy.enums.SyncResultEnum;

/**
 * Bir kaynağın senkronizasyon özeti (API cevabı olarak döner).
 */
public class SwaggerSyncResult {

    private UUID swaggerSourceId;
    private String sourceCode;
    private SyncResultEnum result;
    /** Kaynak için başka bir senkronizasyon sürdüğü için bu çalışma atlandı mı. */
    private boolean skipped;
    private String message;
    private List<String> documentUrls = new ArrayList<>();
    private int endpointCount;
    private int insertedCount;
    private int updatedCount;
    private int unchangedCount;
    private int cancelledCount;
    private long durationMs;

    public SwaggerSyncResult() {
    }

    public UUID getSwaggerSourceId() {
        return swaggerSourceId;
    }

    public void setSwaggerSourceId(UUID swaggerSourceId) {
        this.swaggerSourceId = swaggerSourceId;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public SyncResultEnum getResult() {
        return result;
    }

    public void setResult(SyncResultEnum result) {
        this.result = result;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public void setSkipped(boolean skipped) {
        this.skipped = skipped;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getDocumentUrls() {
        return documentUrls;
    }

    public void setDocumentUrls(List<String> documentUrls) {
        this.documentUrls = documentUrls;
    }

    public int getEndpointCount() {
        return endpointCount;
    }

    public void setEndpointCount(int endpointCount) {
        this.endpointCount = endpointCount;
    }

    public int getInsertedCount() {
        return insertedCount;
    }

    public void setInsertedCount(int insertedCount) {
        this.insertedCount = insertedCount;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public void setUpdatedCount(int updatedCount) {
        this.updatedCount = updatedCount;
    }

    public int getUnchangedCount() {
        return unchangedCount;
    }

    public void setUnchangedCount(int unchangedCount) {
        this.unchangedCount = unchangedCount;
    }

    public int getCancelledCount() {
        return cancelledCount;
    }

    public void setCancelledCount(int cancelledCount) {
        this.cancelledCount = cancelledCount;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    @Override
    public String toString() {
        return "SwaggerSyncResult{"
                + "sourceCode='" + sourceCode + '\''
                + ", result=" + result
                + ", skipped=" + skipped
                + ", endpointCount=" + endpointCount
                + ", inserted=" + insertedCount
                + ", updated=" + updatedCount
                + ", unchanged=" + unchangedCount
                + ", cancelled=" + cancelledCount
                + ", durationMs=" + durationMs
                + ", message='" + message + '\''
                + '}';
    }
}
