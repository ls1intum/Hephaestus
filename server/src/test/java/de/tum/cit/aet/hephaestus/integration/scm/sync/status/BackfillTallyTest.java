package de.tum.cit.aet.hephaestus.integration.scm.sync.status;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.spi.AuthMode;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncPhase;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncTargetProvider.SyncTarget;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The checkpoint arithmetic that turns backfill from an endless spinner into a percent-done bar. */
class BackfillTallyTest extends BaseUnitTest {

    /**
     * @param issueHwm        highest issue number at backfill start, or null when not yet initialized
     * @param issueCheckpoint lowest issue number reached so far (counts down to 0 = complete)
     */
    private static SyncTarget target(
        long id,
        Integer issueHwm,
        Integer issueCheckpoint,
        Integer prHwm,
        Integer prCheckpoint
    ) {
        return new SyncTarget(
            id,
            1L,
            null,
            null,
            AuthMode.PERSONAL_ACCESS_TOKEN,
            "owner/repo" + id,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            issueHwm,
            issueCheckpoint,
            prHwm,
            prCheckpoint,
            null,
            null,
            null,
            null
        );
    }

    @Test
    void noTargetInitialized_totalIsNullSoTheBarStaysHonestlyIndeterminate() {
        BackfillTally tally = new BackfillTally(List.of(target(1L, null, null, null, null)));

        // Not 0: the total is genuinely unknown until the first batch captures a high-water mark, and a
        // fabricated denominator would draw a precise-looking bar out of nothing.
        assertThat(tally.itemsTotal()).isNull();
        assertThat(tally.itemsProcessed()).isZero();
    }

    @Test
    void initializedTargets_totalIsTheSumOfIssueAndPullRequestHighWaterMarks() {
        BackfillTally tally = new BackfillTally(List.of(target(1L, 100, 100, 20, 20), target(2L, 50, 50, 5, 5)));

        assertThat(tally.itemsTotal()).isEqualTo(175);
    }

    @Test
    void processed_isHighWaterMarkMinusRemainingCheckpoint() {
        // Issues walked from #100 down to #60 => 40 done; PRs from #20 down to #15 => 5 done.
        BackfillTally tally = new BackfillTally(List.of(target(1L, 100, 60, 20, 15)));

        assertThat(tally.itemsProcessed()).isEqualTo(45);
        assertThat(tally.itemsTotal()).isEqualTo(120);
    }

    @Test
    void completedTarget_countsFullyOnBothSidesSoTheBarReadsOneHundredPercent() {
        BackfillTally tally = new BackfillTally(List.of(target(1L, 100, 0, 20, 0)));

        assertThat(tally.itemsProcessed()).isEqualTo(120);
        assertThat(tally.itemsTotal()).isEqualTo(120);
    }

    @Test
    void uninitializedTargetAlongsideAnInitializedOne_contributesToNeitherSide() {
        BackfillTally tally = new BackfillTally(
            List.of(target(1L, 100, 40, null, null), target(2L, null, null, null, null))
        );

        // The uninitialized target must not inflate the numerator against a denominator it isn't in.
        assertThat(tally.itemsTotal()).isEqualTo(100);
        assertThat(tally.itemsProcessed()).isEqualTo(60);
    }

    /** The live half: this is what makes the bar move DURING a batch rather than once per batch. */
    @Test
    void observedPage_movesProcessedBeforeTheCheckpointIsPersisted() {
        BackfillTally tally = new BackfillTally(List.of(target(1L, 100, 100, 0, 0)));
        assertThat(tally.itemsProcessed()).isZero();

        tally.observe(1L, SyncPhase.ISSUES, 80);
        assertThat(tally.itemsProcessed()).isEqualTo(20);

        tally.observe(1L, SyncPhase.ISSUES, 55);
        assertThat(tally.itemsProcessed()).isEqualTo(45);
    }

    @Test
    void observedPage_isTrackedPerPhase() {
        BackfillTally tally = new BackfillTally(List.of(target(1L, 100, 100, 40, 40)));

        tally.observe(1L, SyncPhase.ISSUES, 70);
        tally.observe(1L, SyncPhase.PULL_REQUESTS, 30);

        // 30 issues + 10 PRs — the phases must not overwrite each other.
        assertThat(tally.itemsProcessed()).isEqualTo(40);
    }

    @Test
    void refresh_dropsLiveOverridesSoAStalePageMinimumCannotOutliveItsBatch() {
        BackfillTally tally = new BackfillTally(List.of(target(1L, 100, 100, 0, 0)));
        tally.observe(1L, SyncPhase.ISSUES, 20);
        assertThat(tally.itemsProcessed()).isEqualTo(80);

        // The batch ended having only reached #90 (e.g. it aborted); the persisted truth must win over
        // the optimistic in-flight minimum rather than the two being max()-ed together.
        tally.refresh(List.of(target(1L, 100, 90, 0, 0)));

        assertThat(tally.itemsProcessed()).isEqualTo(10);
    }

    @Test
    void refresh_picksUpAHighWaterMarkCapturedByTheFirstBatch() {
        BackfillTally tally = new BackfillTally(List.of(target(1L, null, null, null, null)));
        assertThat(tally.itemsTotal()).isNull();

        tally.refresh(List.of(target(1L, 100, 60, 20, 20)));

        // The moment the first batch initializes the marks, the bar can become determinate.
        assertThat(tally.itemsTotal()).isEqualTo(120);
        assertThat(tally.itemsProcessed()).isEqualTo(40);
    }

    @Test
    void observedNumberBelowZero_isClampedRatherThanOverCountingPastTheHighWaterMark() {
        BackfillTally tally = new BackfillTally(List.of(target(1L, 100, 100, 0, 0)));

        tally.observe(1L, SyncPhase.ISSUES, -5);

        assertThat(tally.itemsProcessed()).isEqualTo(100);
        assertThat(tally.itemsProcessed()).isLessThanOrEqualTo(tally.itemsTotal());
    }

    @Test
    void highWaterMarkFor_exposesThePerPhaseMarkForTheNarrativeRange() {
        BackfillTally tally = new BackfillTally(List.of(target(1L, 4812, 3200, 900, 900)));

        assertThat(tally.highWaterMarkFor(1L, SyncPhase.ISSUES)).isEqualTo(4812);
        assertThat(tally.highWaterMarkFor(1L, SyncPhase.PULL_REQUESTS)).isEqualTo(900);
        assertThat(tally.highWaterMarkFor(999L, SyncPhase.ISSUES)).isNull();
    }
}
