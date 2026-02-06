package de.tum.in.www1.hephaestus.gitprovider.sync;

import org.springframework.lang.Nullable;

/**
 * Represents the result of a synchronization operation with phase tracking.
 * <p>
 * This record captures the outcome status, count of items synced, and optional
 * phase-level success/failure information. The status indicates whether the sync
 * completed fully, completed with warnings (partial phase failures), or was aborted.
 * <p>
 * <h2>Phase Tracking</h2>
 * <p>
 * For complex sync operations with multiple phases (e.g., project sync with fields,
 * items, and status updates), phase tracking allows callers to understand which
 * phases succeeded and which failed. This enables:
 * <ul>
 *   <li>More informative logging and monitoring</li>
 *   <li>Targeted retry logic for specific failed phases</li>
 *   <li>Graceful degradation when non-critical phases fail</li>
 * </ul>
 * <p>
 * The timestamp should only be updated when the status is {@link Status#COMPLETED},
 * ensuring that incomplete syncs will be retried from the same point on the next run.
 *
 * @param status         the completion status of the sync operation
 * @param count          the number of items successfully synced before completion or abort
 * @param fieldsSynced   whether field sync completed successfully (null if not applicable)
 * @param statusUpdatesSynced whether status update sync completed successfully (null if not applicable)
 * @param itemsSynced    whether item sync completed successfully (null if not applicable)
 */
public record SyncResult(
    Status status,
    int count,
    @Nullable Boolean fieldsSynced,
    @Nullable Boolean statusUpdatesSynced,
    @Nullable Boolean itemsSynced
) {
    /**
     * The completion status of a sync operation.
     */
    public enum Status {
        /**
         * Sync completed successfully - all pages and phases were fetched.
         * The timestamp should be updated.
         */
        COMPLETED,

        /**
         * Sync completed but with warnings - the primary phase succeeded but
         * one or more secondary phases failed (e.g., item sync succeeded but
         * field sync or status update sync failed).
         * <p>
         * The timestamp for successfully completed phases should be updated.
         * Failed phases will retain their previous timestamp for retry.
         */
        COMPLETED_WITH_WARNINGS,

        /**
         * Sync was aborted due to rate limiting.
         * The timestamp should NOT be updated to allow retry from the same point.
         */
        ABORTED_RATE_LIMIT,

        /**
         * Sync was aborted due to an error (auth, not found, client error, etc.).
         * The timestamp should NOT be updated to allow retry from the same point.
         */
        ABORTED_ERROR,
    }

    /**
     * Backward-compatible constructor for simple sync results without phase tracking.
     *
     * @param status the completion status
     * @param count  the number of items synced
     */
    public SyncResult(Status status, int count) {
        this(status, count, null, null, null);
    }

    /**
     * Creates a successful completion result.
     *
     * @param count the number of items synced
     * @return a SyncResult with COMPLETED status
     */
    public static SyncResult completed(int count) {
        return new SyncResult(Status.COMPLETED, count);
    }

    /**
     * Creates a successful completion result with all phases tracked.
     *
     * @param count               the number of items synced
     * @param fieldsSynced        whether field sync completed successfully
     * @param statusUpdatesSynced whether status update sync completed successfully
     * @param itemsSynced         whether item sync completed successfully
     * @return a SyncResult with COMPLETED status and phase information
     */
    public static SyncResult completedWithPhases(
        int count,
        boolean fieldsSynced,
        boolean statusUpdatesSynced,
        boolean itemsSynced
    ) {
        return new SyncResult(Status.COMPLETED, count, fieldsSynced, statusUpdatesSynced, itemsSynced);
    }

    /**
     * Creates a completion result with warnings when some phases failed.
     * <p>
     * Use this when the primary sync (e.g., items) succeeded but secondary
     * phases (e.g., fields, status updates) failed.
     *
     * @param count               the number of items synced
     * @param fieldsSynced        whether field sync completed successfully
     * @param statusUpdatesSynced whether status update sync completed successfully
     * @param itemsSynced         whether item sync completed successfully
     * @return a SyncResult with COMPLETED_WITH_WARNINGS status and phase information
     */
    public static SyncResult completedWithWarnings(
        int count,
        boolean fieldsSynced,
        boolean statusUpdatesSynced,
        boolean itemsSynced
    ) {
        return new SyncResult(Status.COMPLETED_WITH_WARNINGS, count, fieldsSynced, statusUpdatesSynced, itemsSynced);
    }

    /**
     * Creates an aborted result due to rate limiting.
     *
     * @param count the number of items synced before abort
     * @return a SyncResult with ABORTED_RATE_LIMIT status
     */
    public static SyncResult abortedRateLimit(int count) {
        return new SyncResult(Status.ABORTED_RATE_LIMIT, count);
    }

    /**
     * Creates an aborted result due to rate limiting with phase tracking.
     *
     * @param count               the number of items synced before abort
     * @param fieldsSynced        whether field sync completed successfully before abort
     * @param statusUpdatesSynced whether status update sync completed successfully before abort
     * @param itemsSynced         whether item sync completed successfully before abort
     * @return a SyncResult with ABORTED_RATE_LIMIT status and phase information
     */
    public static SyncResult abortedRateLimitWithPhases(
        int count,
        @Nullable Boolean fieldsSynced,
        @Nullable Boolean statusUpdatesSynced,
        @Nullable Boolean itemsSynced
    ) {
        return new SyncResult(Status.ABORTED_RATE_LIMIT, count, fieldsSynced, statusUpdatesSynced, itemsSynced);
    }

    /**
     * Creates an aborted result due to an error.
     *
     * @param count the number of items synced before abort
     * @return a SyncResult with ABORTED_ERROR status
     */
    public static SyncResult abortedError(int count) {
        return new SyncResult(Status.ABORTED_ERROR, count);
    }

    /**
     * Creates an aborted result due to an error with phase tracking.
     *
     * @param count               the number of items synced before abort
     * @param fieldsSynced        whether field sync completed successfully before abort
     * @param statusUpdatesSynced whether status update sync completed successfully before abort
     * @param itemsSynced         whether item sync completed successfully before abort
     * @return a SyncResult with ABORTED_ERROR status and phase information
     */
    public static SyncResult abortedErrorWithPhases(
        int count,
        @Nullable Boolean fieldsSynced,
        @Nullable Boolean statusUpdatesSynced,
        @Nullable Boolean itemsSynced
    ) {
        return new SyncResult(Status.ABORTED_ERROR, count, fieldsSynced, statusUpdatesSynced, itemsSynced);
    }

    /**
     * Checks if the sync completed successfully (fully or with warnings).
     *
     * @return true if status is COMPLETED or COMPLETED_WITH_WARNINGS
     */
    public boolean isCompleted() {
        return status == Status.COMPLETED || status == Status.COMPLETED_WITH_WARNINGS;
    }

    /**
     * Checks if the sync completed fully without any warnings.
     *
     * @return true if status is COMPLETED
     */
    public boolean isFullyCompleted() {
        return status == Status.COMPLETED;
    }

    /**
     * Checks if the sync was aborted for any reason.
     *
     * @return true if status is ABORTED_RATE_LIMIT or ABORTED_ERROR
     */
    public boolean isAborted() {
        return status == Status.ABORTED_RATE_LIMIT || status == Status.ABORTED_ERROR;
    }

    /**
     * Checks if the sync has any warnings (phase failures).
     *
     * @return true if any tracked phase failed
     */
    public boolean hasWarnings() {
        return (
            status == Status.COMPLETED_WITH_WARNINGS ||
            (fieldsSynced != null && !fieldsSynced) ||
            (statusUpdatesSynced != null && !statusUpdatesSynced) ||
            (itemsSynced != null && !itemsSynced)
        );
    }

    /**
     * Checks if field sync specifically failed.
     *
     * @return true if field sync was tracked and failed
     */
    public boolean hasFieldSyncFailure() {
        return fieldsSynced != null && !fieldsSynced;
    }

    /**
     * Checks if status update sync specifically failed.
     *
     * @return true if status update sync was tracked and failed
     */
    public boolean hasStatusUpdateSyncFailure() {
        return statusUpdatesSynced != null && !statusUpdatesSynced;
    }

    /**
     * Checks if item sync specifically failed.
     *
     * @return true if item sync was tracked and failed
     */
    public boolean hasItemSyncFailure() {
        return itemsSynced != null && !itemsSynced;
    }

    /**
     * Returns a summary string of phase statuses for logging.
     *
     * @return a string like "fields=true, statusUpdates=false, items=true"
     */
    public String phaseSummary() {
        if (fieldsSynced == null && statusUpdatesSynced == null && itemsSynced == null) {
            return "no phase tracking";
        }

        StringBuilder sb = new StringBuilder();
        if (fieldsSynced != null) {
            sb.append("fields=").append(fieldsSynced);
        }
        if (statusUpdatesSynced != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("statusUpdates=").append(statusUpdatesSynced);
        }
        if (itemsSynced != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("items=").append(itemsSynced);
        }
        return sb.toString();
    }
}
