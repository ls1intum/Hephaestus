package de.tum.cit.aet.hephaestus.integration.scm.sync.status;

import de.tum.cit.aet.hephaestus.integration.core.spi.BackfillSummary;
import de.tum.cit.aet.hephaestus.integration.core.spi.RepoBackfillProgress;
import java.util.List;

/**
 * Connection-level backfill rollup shared by both SCM providers (GitHub, GitLab).
 *
 * <p>Both vendors' historical backfill writes the <em>same</em> {@code repository_to_monitor} columns
 * (issue/PR high-water-mark, checkpoint, initialized/complete flags) — GitHub via
 * {@code GitHubHistoricalBackfillService}, GitLab via {@code GitLabHistoricalBackfillService} — so the
 * "how far back have we gone" summary is computed identically. Kept as one pure function rather than
 * copied into each provider, mirroring how {@link ScmResourceCountReader} is shared for the per-class
 * mirror counts.
 *
 * <p><b>Cost contract.</b> Operates purely on an already-loaded {@code findByWorkspaceId} list — no
 * per-target {@code getProgress} re-fetch (which would be an N+1 over the monitor set) and no live
 * vendor call, per the {@code ConnectionSyncStateProvider#describe} contract.
 */
public final class ScmBackfillRollup {

    private ScmBackfillRollup() {}

    /**
     * Summarizes connection-level backfill progress from the monitored-repository rows.
     *
     * <p>The {@code scheduledBackfillEnabled} gate is the {@code hephaestus.sync.backfill.enabled}
     * flag that governs the <em>scheduled</em> backfill cycle for both vendors
     * ({@code GitHubHistoricalBackfillService#runBackfillCycle},
     * {@code GitLabHistoricalBackfillService#runBackfillCycle}). When it is off the recurring job never
     * ticks, so the honest connection-level state is {@code "DISABLED"} — matching what the admin UI's
     * "Scheduled backfill" diagnostic asks about.
     *
     * @param scheduledBackfillEnabled whether the scheduled backfill cycle is enabled
     * @param monitors                 the workspace's monitored-repository progress projections (may be empty)
     * @return a non-null {@link BackfillSummary} describing the rollup state and coarse percent
     */
    public static BackfillSummary summarize(boolean scheduledBackfillEnabled, List<RepoBackfillProgress> monitors) {
        if (!scheduledBackfillEnabled) {
            return new BackfillSummary("DISABLED", null, null);
        }
        if (monitors.isEmpty()) {
            return new BackfillSummary("NOT_STARTED", null, null);
        }

        boolean anyInitialized = false;
        boolean allComplete = true;
        long totalItems = 0;
        long doneItems = 0;
        for (RepoBackfillProgress rtm : monitors) {
            anyInitialized = anyInitialized || rtm.initialized();
            allComplete = allComplete && rtm.complete();
            int highWaterMark =
                (rtm.issueHighWaterMark() != null ? rtm.issueHighWaterMark() : 0) +
                (rtm.pullRequestHighWaterMark() != null ? rtm.pullRequestHighWaterMark() : 0);
            totalItems += highWaterMark;
            doneItems += Math.max(0, highWaterMark - rtm.remaining());
        }

        String state = allComplete ? "COMPLETE" : (anyInitialized ? "IN_PROGRESS" : "NOT_STARTED");
        Integer percent = null;
        if (totalItems > 0) {
            percent = (int) Math.round((100.0 * doneItems) / totalItems);
        } else if (anyInitialized) {
            percent = 100;
        }

        // No per-item timestamp exists for a NUMBER-based backfill horizon (issue/PR number, not date),
        // so completedThrough stays null for both vendors.
        return new BackfillSummary(state, null, percent);
    }
}
