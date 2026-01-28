package de.tum.in.www1.hephaestus.gitprovider.sync;

/**
 * Represents the result of a synchronization operation.
 * <p>
 * This record captures both the outcome status and the count of items synced.
 * The status indicates whether the sync completed fully or was aborted due to
 * rate limiting or other errors.
 * <p>
 * The timestamp should only be updated when the status is {@link Status#COMPLETED},
 * ensuring that incomplete syncs will be retried from the same point on the next run.
 *
 * @param status the completion status of the sync operation
 * @param count  the number of items successfully synced before completion or abort
 */
public record SyncResult(Status status, int count) {
    /**
     * The completion status of a sync operation.
     */
    public enum Status {
        /**
         * Sync completed successfully - all pages were fetched.
         * The timestamp should be updated.
         */
        COMPLETED,

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
     * Creates a successful completion result.
     *
     * @param count the number of items synced
     * @return a SyncResult with COMPLETED status
     */
    public static SyncResult completed(int count) {
        return new SyncResult(Status.COMPLETED, count);
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
     * Creates an aborted result due to an error.
     *
     * @param count the number of items synced before abort
     * @return a SyncResult with ABORTED_ERROR status
     */
    public static SyncResult abortedError(int count) {
        return new SyncResult(Status.ABORTED_ERROR, count);
    }

    /**
     * Checks if the sync completed successfully.
     *
     * @return true if status is COMPLETED
     */
    public boolean isCompleted() {
        return status == Status.COMPLETED;
    }

    /**
     * Checks if the sync was aborted for any reason.
     *
     * @return true if status is not COMPLETED
     */
    public boolean isAborted() {
        return status != Status.COMPLETED;
    }
}
