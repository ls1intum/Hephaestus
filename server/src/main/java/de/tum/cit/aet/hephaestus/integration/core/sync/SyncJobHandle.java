package de.tum.cit.aet.hephaestus.integration.core.sync;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Live handle a runner body ({@link IntegrationSyncRunner}-style {@code Consumer<SyncJobHandle>})
 * uses to report progress and observe cooperative cancellation while {@link SyncJobService#run} is
 * executing it.
 *
 * <p>{@link #isCancellationRequested()} is refreshed from the database on {@link SyncJobService}'s 60s
 * lease-heartbeat pass AND immediately on {@link SyncJobService#requestCancel} for the local case (no
 * need to wait out the sweep when the request lands on the same JVM that's running the job).
 *
 * <p>{@link #progress} is throttled to at most one DB write per {@value #MIN_WRITE_INTERVAL_SECONDS}s;
 * the latest values are always retained in memory so {@link SyncJobService#run} can flush them as part
 * of the terminal status write even if they arrived inside the throttle window.
 */
public final class SyncJobHandle {

    private static final long MIN_WRITE_INTERVAL_SECONDS = 5;
    private static final Duration MIN_WRITE_INTERVAL = Duration.ofSeconds(MIN_WRITE_INTERVAL_SECONDS);

    /** Callback the owning {@link SyncJobService} supplies to actually persist a throttled write. */
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

    public long jobId() {
        return jobId;
    }

    public boolean isCancellationRequested() {
        return cancellationRequested;
    }

    /** Called by {@link SyncJobService}: the heartbeat sweep (DB-sourced) or an immediate local cancel. */
    void refreshCancellation(boolean requested) {
        this.cancellationRequested = requested;
    }

    /**
     * A runner calls this when it stops early in response to {@link #isCancellationRequested()}.
     * The service also honors a committed database cancellation during its terminal compare-and-set,
     * covering requests that arrive on another replica after the last heartbeat refresh.
     */
    public void reportCancelled() {
        this.cancelledReported = true;
    }

    boolean cancelledReported() {
        return cancelledReported;
    }

    /**
     * A runner calls this when it completed but some units failed (e.g. a partial Slack history replay),
     * finalizing the job as {@code SUCCEEDED_WITH_WARNINGS} rather than a bare success.
     */
    public void reportWarnings() {
        this.warningsReported = true;
    }

    boolean warningsReported() {
        return warningsReported;
    }

    /**
     * Report progress. Buffers the latest values in memory unconditionally, and additionally persists
     * to the database when at least {@value #MIN_WRITE_INTERVAL_SECONDS}s elapsed since the last write —
     * a runner is expected to call this frequently (e.g. once per page/repo), and DB writes on every
     * call would swamp the connection pool.
     */
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
