package de.tum.cit.aet.hephaestus.integration.core.sync;

import de.tum.cit.aet.hephaestus.integration.core.spi.SyncExecutionHandle;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Internal state and persistence adapter behind the runner-facing {@link SyncExecutionHandle}. */
public final class SyncJobHandle implements SyncExecutionHandle {

    private static final long MIN_WRITE_INTERVAL_SECONDS = 5;
    private static final Duration MIN_WRITE_INTERVAL = Duration.ofSeconds(MIN_WRITE_INTERVAL_SECONDS);

    @FunctionalInterface
    interface ProgressWriter {
        void writeProgress(
            long jobId,
            @Nullable Integer itemsProcessed,
            @Nullable Integer itemsTotal,
            Map<String, Object> progressDetail
        );
    }

    private final long jobId;
    private final ProgressWriter writer;

    private volatile boolean cancellationRequested;
    private volatile boolean cancelledReported;
    private volatile boolean warningsReported;
    private volatile Instant lastWriteAt = Instant.EPOCH;

    @Nullable
    private volatile Integer itemsProcessed;

    @Nullable
    private volatile Integer itemsTotal;

    private volatile Map<String, Object> progressDetail = Map.of();

    SyncJobHandle(long jobId, ProgressWriter writer) {
        this.jobId = jobId;
        this.writer = writer;
    }

    @Override
    public boolean isCancellationRequested() {
        return cancellationRequested;
    }

    void refreshCancellation(boolean requested) {
        if (requested) {
            this.cancellationRequested = true;
        }
    }

    @Override
    public void reportCancelled() {
        this.cancelledReported = true;
    }

    boolean cancelledReported() {
        return cancelledReported;
    }

    @Override
    public void reportWarnings() {
        this.warningsReported = true;
    }

    boolean warningsReported() {
        return warningsReported;
    }

    /** Buffers every update and persists at most once per five seconds. */
    @Override
    public void progress(
        @Nullable Integer itemsProcessed,
        @Nullable Integer itemsTotal,
        @Nullable Map<String, Object> progressDetail
    ) {
        this.itemsProcessed = itemsProcessed;
        this.itemsTotal = itemsTotal;
        this.progressDetail = progressDetail == null ? Map.of() : progressDetail;

        Instant now = Instant.now();
        if (Duration.between(lastWriteAt, now).compareTo(MIN_WRITE_INTERVAL) >= 0) {
            lastWriteAt = now;
            writer.writeProgress(jobId, this.itemsProcessed, this.itemsTotal, this.progressDetail);
        }
    }

    @Nullable
    Integer currentItemsProcessed() {
        return itemsProcessed;
    }

    @Nullable
    Integer currentItemsTotal() {
        return itemsTotal;
    }

    Map<String, Object> currentProgressDetail() {
        return progressDetail;
    }
}
