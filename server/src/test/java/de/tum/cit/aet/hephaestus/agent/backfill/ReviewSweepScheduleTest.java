package de.tum.cit.aet.hephaestus.agent.backfill;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The arithmetic a recurring sweep is made of: which days each run covers, and when the next one is.
 *
 * <p>Both are load-bearing rather than incidental. The window decides whether a sweep's findings may be
 * read beside reviews that events triggered; the advance decides whether a nightly sweep is still
 * nightly a month later.
 */
@DisplayName("Review sweep schedule")
class ReviewSweepScheduleTest extends BaseUnitTest {

    private static final Instant NOW = Instant.parse("2026-08-09T02:00:00Z");

    @Test
    void aSweepReachesBackExactlyItsLookback() {
        ReviewSweepSchedule schedule = schedule(ReviewSweepCadence.DAILY, 2);

        assertThat(schedule.windowStart(NOW)).isEqualTo(NOW.minus(Duration.ofDays(2)));
    }

    /**
     * The property that makes a missed artifact recoverable, and the reason the lookback ceiling is twice
     * the cadence rather than once.
     *
     * <p>Anchoring each window where the previous one ended would look tidier and would be wrong: a
     * campaign that paused on an exhausted budget, or was cancelled, leaves artifacts it never offered,
     * and an abutting window would have moved past them for good. Overlapping means the next night
     * covers them — and costs nothing, because an artifact already measured at its current state
     * produces the key the first sweep recorded and the ledger refuses the second offer.
     */
    @Test
    void consecutiveDailySweepsOverlapSoAMissedArtifactGetsAnotherTurn() {
        ReviewSweepSchedule schedule = schedule(ReviewSweepCadence.DAILY, 2);
        Instant tomorrow = NOW.plus(Duration.ofDays(1));

        assertThat(schedule.windowStart(tomorrow))
            .as("tomorrow's window still reaches back over today's")
            .isBefore(NOW);
    }

    /**
     * The rule that keeps a sweep admissible as a live measurement, at the one moment it is most tempting
     * to break: an instance that was unreachable for a month comes back, and "review everything since we
     * last looked" would sweep a month of history and file it in the same population as reviews that
     * events triggered. That is a corpus chosen by an outage, which is hindsight by another name. The
     * window does not depend on when the last sweep ran, so there is nothing for an outage to stretch.
     */
    @Test
    void anOutageDoesNotWidenTheWindowItComesBackTo() {
        ReviewSweepSchedule schedule = schedule(ReviewSweepCadence.DAILY, 2);
        schedule.setLastRunAt(NOW.minus(Duration.ofDays(30)));

        assertThat(schedule.windowStart(NOW)).isEqualTo(NOW.minus(Duration.ofDays(2)));
    }

    /**
     * The next occurrence is derived from the previous one, never from the moment the tick ran. A
     * schedule that added a day to "now" would slip later by however long each tick was delayed, and a
     * sweep an admin set for the small hours would be running in the middle of the working day within a
     * month.
     */
    @Test
    void theNextOccurrenceKeepsItsPhaseRatherThanDriftingByTheTicksDelay() {
        ReviewSweepSchedule schedule = schedule(ReviewSweepCadence.DAILY, 2);
        Instant due = Instant.parse("2026-08-09T02:00:00Z");
        schedule.setNextRunAt(due);

        // Eleven minutes late, as a tick that waited on the lock would be.
        schedule.advancePast(due.plus(Duration.ofMinutes(11)));

        assertThat(schedule.getNextRunAt()).isEqualTo(Instant.parse("2026-08-10T02:00:00Z"));
    }

    /**
     * Missed occurrences are skipped, not queued. A week of downtime must not come back as seven
     * campaigns in seven minutes — each would review the same recent work, only the first would find
     * anything unsettled, and all seven would be priced and audited.
     */
    @Test
    void aWeekOfDowntimeProducesOneSweepAndNotSeven() {
        ReviewSweepSchedule schedule = schedule(ReviewSweepCadence.DAILY, 2);
        Instant due = Instant.parse("2026-08-01T02:00:00Z");
        schedule.setNextRunAt(due);

        schedule.advancePast(Instant.parse("2026-08-08T09:13:00Z"));

        assertThat(schedule.getNextRunAt()).isEqualTo(Instant.parse("2026-08-09T02:00:00Z"));
    }

    @ParameterizedTest
    @EnumSource(ReviewSweepCadence.class)
    void anAdvanceAlwaysLandsStrictlyInTheFuture(ReviewSweepCadence cadence) {
        ReviewSweepSchedule schedule = schedule(cadence, 1);
        schedule.setNextRunAt(NOW.minus(Duration.ofDays(400)));

        schedule.advancePast(NOW);

        assertThat(schedule.getNextRunAt()).isAfter(NOW);
    }

    /**
     * The jitter is a phase offset applied once, not a delay added on every advance. Added each time it
     * would be the same forward drift the phase-keeping advance exists to prevent; applied once it
     * spreads an instance's schedules across an hour permanently.
     */
    @Test
    void twoWorkspacesCreatedTheSameSecondDoNotWakeTheSameMinuteForEver() {
        Instant firstA = ReviewSweepSchedule.firstRunAt(7L, NOW);
        Instant firstB = ReviewSweepSchedule.firstRunAt(23L, NOW);

        assertThat(firstA).isNotEqualTo(firstB);
        assertThat(Duration.between(NOW, firstA)).isLessThan(Duration.ofHours(1));
        assertThat(Duration.between(NOW, firstB)).isLessThan(Duration.ofHours(1));
    }

    /** A daily sweep may overlap itself once; a weekly one may reach back a week and no further. */
    @Test
    void theLookbackCeilingIsTwiceTheCadenceCappedAtAWeek() {
        assertThat(ReviewSweepCadence.DAILY.maxLookback()).isEqualTo(Duration.ofDays(2));
        assertThat(ReviewSweepCadence.WEEKLY.maxLookback()).isEqualTo(Duration.ofDays(7));
        assertThat(ReviewSweepCadence.WEEKLY.maxLookback()).isEqualTo(ReviewSweepCadence.MAX_LOOKBACK);
    }

    private static ReviewSweepSchedule schedule(ReviewSweepCadence cadence, int lookbackDays) {
        ReviewSweepSchedule schedule = new ReviewSweepSchedule();
        schedule.setArtifactKind(ArtifactKinds.PULL_REQUEST.value());
        schedule.setCadence(cadence);
        schedule.setLookbackDays(lookbackDays);
        schedule.setEnabled(true);
        schedule.setNextRunAt(NOW);
        return schedule;
    }
}
