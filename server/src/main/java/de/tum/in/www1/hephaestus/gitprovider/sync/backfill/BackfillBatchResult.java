package de.tum.in.www1.hephaestus.gitprovider.sync.backfill;

import org.springframework.lang.Nullable;

/**
 * Result of a single backfill batch (one pagination round) for a repository.
 * Used by both GitLab issue and merge request historical backfill.
 *
 * @param count       number of items processed in this batch
 * @param minIid      lowest IID (internal ID) seen in this batch (-1 if none)
 * @param maxIid      highest IID seen in this batch (-1 if none)
 * @param nextCursor  pagination cursor for the next batch, null if complete
 * @param complete    true if there are no more pages to fetch
 * @param aborted     true if the batch was aborted due to an error
 */
public record BackfillBatchResult(
    int count,
    int minIid,
    int maxIid,
    @Nullable String nextCursor,
    boolean complete,
    boolean aborted
) {
    public static BackfillBatchResult empty() {
        return new BackfillBatchResult(0, -1, -1, null, true, false);
    }

    public static BackfillBatchResult abortedWithError() {
        return new BackfillBatchResult(0, -1, -1, null, false, true);
    }
}
