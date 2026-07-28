package de.tum.cit.aet.hephaestus.integration.scm.github.sync.backfill;

import de.tum.cit.aet.hephaestus.integration.core.spi.SyncExecutionHandle;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncPhase;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncProgress;

/**
 * Per-vendor-page callback a backfill service invokes so its driving runner can report progress at the
 * grain the work actually happens at.
 *
 * <p>Why the indirection: a backfill batch is up to {@code hephaestus.sync.backfill.batch-size} GraphQL
 * pages of issues <em>plus</em> that many pages of pull requests — minutes of wall time. A runner that
 * reports once per batch is silent for that whole stretch. Only the service knows when a page lands, and
 * only the runner knows the job-global totals across every repository; this interface is the seam.
 *
 * <p>Runners must not throttle themselves here — over-calling is free by design, because
 * {@link SyncExecutionHandle}'s implementation owns the write budget.
 *
 * <p>Implementations must be safe to call from the thread running the backfill and must not throw: a
 * progress callback failing has no business aborting the sync it is only observing.
 */
@FunctionalInterface
public interface BackfillPageObserver {
    /**
     * One vendor page has been fetched and persisted.
     *
     * @param syncTargetId       the sync target (repository) being backfilled
     * @param repositoryName     display name of that repository, for the progress narrative
     * @param phase              {@link SyncPhase#ISSUES} or {@link SyncPhase#PULL_REQUESTS}
     * @param lowestNumberSeen   the lowest issue/PR <em>number</em> reached so far in this batch.
     *                           Backfill counts down from the high-water mark toward #1, so this is the
     *                           live equivalent of the checkpoint column that will be persisted when the
     *                           batch ends — which is what lets the runner compute a determinate
     *                           percentage mid-batch instead of waiting for the write.
     * @param itemsSyncedInBatch items persisted so far in this batch, for the narrative only
     */
    void onPageComplete(
        Long syncTargetId,
        String repositoryName,
        SyncPhase phase,
        int lowestNumberSeen,
        int itemsSyncedInBatch
    );

    /** No-op observer for the scheduled cycle, which has no job handle to report to. */
    BackfillPageObserver NOOP = (syncTargetId, repositoryName, phase, lowestNumberSeen, itemsSyncedInBatch) -> {};
}
