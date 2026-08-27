package de.tum.cit.aet.hephaestus.integration.scm.sync.status;

import de.tum.cit.aet.hephaestus.integration.core.spi.SyncPhase;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncTargetProvider.SyncTarget;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The job-global determinate progress fraction for an SCM backfill, kept live across the pages of a
 * batch. Shared by both SCM runners: GitHub and GitLab drive different page loops but persist the same
 * high-water-mark / checkpoint columns on {@link SyncTarget}, so the arithmetic is identical.
 *
 * <p><b>How the total is known.</b> Backfill walks issue and PR <em>numbers</em> down from a
 * high-water mark toward #1, and both the mark and the checkpoint are already persisted per repository.
 * Summing them yields a determinate total.
 *
 * <p><b>The unit is issue/PR numbers, not rows.</b> {@code itemsTotal = Σ highWaterMark} and
 * {@code itemsProcessed = Σ (highWaterMark − remaining)} are consistent with each other and with the
 * per-resource percent the status provider already renders. They are deliberately not mixed with counts
 * of rows actually persisted: numbering has gaps (deleted issues, transferred PRs), so a row count and a
 * number range are different quantities and averaging them would produce a bar that lies in both
 * directions.
 *
 * <p><b>Why it tracks live minimums.</b> The checkpoint column is only written when a batch ends. Within
 * a batch the lowest number seen so far <em>is</em> the checkpoint-to-be, so folding it in lets the
 * fraction move on every page instead of stepping once per batch.
 *
 * <p>Not thread-safe: one instance per backfill pass, mutated by the single thread running it.
 */
public final class BackfillTally {

    /** Per-target high-water marks and persisted remainders, refreshed at each batch boundary. */
    private Map<Long, TargetMarks> marks = new HashMap<>();

    /** Live checkpoint overrides from pages of the batch currently running. */
    private final Map<Long, Integer> liveIssueRemaining = new HashMap<>();
    private final Map<Long, Integer> livePullRequestRemaining = new HashMap<>();

    public BackfillTally(List<SyncTarget> targets) {
        refresh(targets);
    }

    /**
     * Re-reads the persisted marks and drops the live overrides they now supersede.
     *
     * <p>Clearing matters: an override left over from a previous batch would keep asserting that batch's
     * minimum for a repository the loop has moved past.
     */
    public void refresh(List<SyncTarget> targets) {
        Map<Long, TargetMarks> refreshed = new HashMap<>();
        for (SyncTarget target : targets) {
            refreshed.put(
                target.id(),
                new TargetMarks(
                    target.issueBackfillHighWaterMark(),
                    target.pullRequestBackfillHighWaterMark(),
                    target.getIssueBackfillRemaining(),
                    target.getPullRequestBackfillRemaining()
                )
            );
        }
        this.marks = refreshed;
        liveIssueRemaining.clear();
        livePullRequestRemaining.clear();
    }

    /** Folds one page's lowest-number-seen into the live fraction. */
    public void observe(Long syncTargetId, SyncPhase phase, int lowestNumberSeen) {
        int remaining = Math.max(0, lowestNumberSeen);
        if (phase == SyncPhase.ISSUES) {
            liveIssueRemaining.put(syncTargetId, remaining);
        } else if (phase == SyncPhase.PULL_REQUESTS) {
            livePullRequestRemaining.put(syncTargetId, remaining);
        }
    }

    /**
     * Σ high-water marks over every <em>initialized</em> target, or {@code null} when none is yet.
     *
     * <p>Null rather than 0 on purpose: before the first batch captures a mark, the total is genuinely
     * unknowable, and the honest render for that is an indeterminate spinner with a narrative. Faking a
     * denominator would draw a precise-looking bar out of nothing.
     *
     * <p>The total does grow as later repositories initialize, which can make the percentage step back.
     * That is the truth — the job just learned there is more work than it could previously see — and is
     * preferable to hiding it behind a spinner for the whole run.
     */
    @Nullable
    public Integer itemsTotal() {
        long total = 0;
        boolean anyInitialized = false;
        for (TargetMarks m : marks.values()) {
            if (m.issueHighWaterMark() != null) {
                anyInitialized = true;
                total += m.issueHighWaterMark();
            }
            if (m.pullRequestHighWaterMark() != null) {
                anyInitialized = true;
                total += m.pullRequestHighWaterMark();
            }
        }
        return anyInitialized ? (int) Math.min(total, Integer.MAX_VALUE) : null;
    }

    /** Σ (highWaterMark − remaining), with live page minimums preferred over persisted checkpoints. */
    public Integer itemsProcessed() {
        long done = 0;
        for (Map.Entry<Long, TargetMarks> entry : marks.entrySet()) {
            Long id = entry.getKey();
            TargetMarks m = entry.getValue();
            done += phaseDone(m.issueHighWaterMark(), liveIssueRemaining.get(id), m.issueRemaining());
            done += phaseDone(m.pullRequestHighWaterMark(), livePullRequestRemaining.get(id), m.pullRequestRemaining());
        }
        return (int) Math.min(done, Integer.MAX_VALUE);
    }

    /** The high-water mark for one target and phase, for the narrative's "#4812 → #3200" range. */
    @Nullable
    public Integer highWaterMarkFor(Long syncTargetId, SyncPhase phase) {
        TargetMarks m = marks.get(syncTargetId);
        if (m == null) {
            return null;
        }
        return phase == SyncPhase.ISSUES ? m.issueHighWaterMark() : m.pullRequestHighWaterMark();
    }

    private static long phaseDone(
        @Nullable Integer highWaterMark,
        @Nullable Integer liveRemaining,
        int persistedRemaining
    ) {
        if (highWaterMark == null) {
            // Uninitialized: contributes to neither side, so it cannot inflate the numerator against a
            // denominator that does not yet include it.
            return 0;
        }
        int remaining = liveRemaining != null ? liveRemaining : persistedRemaining;
        return Math.max(0, highWaterMark - remaining);
    }

    private record TargetMarks(
        @Nullable Integer issueHighWaterMark,
        @Nullable Integer pullRequestHighWaterMark,
        int issueRemaining,
        int pullRequestRemaining
    ) {}
}
