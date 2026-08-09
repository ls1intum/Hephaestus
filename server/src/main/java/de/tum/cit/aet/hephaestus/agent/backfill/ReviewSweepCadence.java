package de.tum.cit.aet.hephaestus.agent.backfill;

import java.time.Duration;

/**
 * How often a schedule re-offers a workspace's recent work to the review path.
 *
 * <p>Two values, not a cron expression. A cron field would let an operator write a schedule whose next
 * occurrence nobody can predict from the row, and the one property this feature has to keep — that a
 * sweep's window is bounded to the recent past — is stated relative to the cadence. A vocabulary of two
 * makes {@link #maxLookback()} a fact rather than an evaluation.
 */
public enum ReviewSweepCadence {
    DAILY(Duration.ofDays(1)),
    WEEKLY(Duration.ofDays(7));

    /**
     * The longest window any sweep may look back over, whatever its cadence.
     *
     * <p>This is what keeps a sweep honest as a LIVE measurement. A sweep files its observations in the
     * same population as the event path (see {@code SignalOrigins}), and that is only defensible while
     * its corpus is "work from the last few days" — a population defined by when the sweep ran, not by
     * anybody's choice. Stretch the window and it becomes a corpus somebody selected with hindsight,
     * which is a campaign, and a campaign records itself as BACKFILL for exactly that reason.
     */
    public static final Duration MAX_LOOKBACK = Duration.ofDays(7);

    private final Duration interval;

    ReviewSweepCadence(Duration interval) {
        this.interval = interval;
    }

    /** How long between one sweep and the next. */
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
