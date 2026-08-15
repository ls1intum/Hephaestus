package de.tum.cit.aet.hephaestus.agent.backfill;

import java.time.Duration;

/**
 * How often a schedule re-offers a workspace's recent work to the review path. Two values, not a cron
 * expression: a cron field would let an operator write a schedule whose next occurrence nobody can predict
 * from the row, and the property this feature must keep — that a sweep's window is bounded to the recent
 * past — is stated relative to the cadence.
 */
public enum ReviewSweepCadence {
    DAILY(Duration.ofDays(1)),
    WEEKLY(Duration.ofDays(7));

    /**
     * The longest window any sweep may look back over, whatever its cadence. This is what keeps a sweep
     * honest as a LIVE measurement: its corpus must be "work from the last few days", defined by when the
     * sweep ran rather than anybody's choice — stretch the window and it becomes a hindsight-selected
     * corpus, which is what BACKFILL is for.
     */
    public static final Duration MAX_LOOKBACK = Duration.ofDays(7);

    private final Duration interval;

    ReviewSweepCadence(Duration interval) {
        this.interval = interval;
    }

    public Duration interval() {
        return interval;
    }

    /**
     * The longest window a schedule at this cadence may sweep: twice its own interval, capped at
     * {@link #MAX_LOOKBACK}.
     *
     * <p>Twice rather than once so a sweep that runs late, or one whose predecessor was blocked, still
     * covers the ground its predecessor was meant to. Capped because "twice a week" is already the point
     * at which the corpus stops being recent work.
     */
    public Duration maxLookback() {
        Duration twice = interval.multipliedBy(2);
        return twice.compareTo(MAX_LOOKBACK) > 0 ? MAX_LOOKBACK : twice;
    }
}
