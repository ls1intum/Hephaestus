package de.tum.cit.aet.hephaestus.practices.trace;

/**
 * What became of one practice on one artifact — the answer to "why didn't it say anything?". Every
 * constant is derived from something already recorded (a {@code SignalStateReason}, a readiness decision,
 * a tier, or the absence of a watched signal), never from a guess.
 *
 * <p>The split that matters most is {@link #SKIPPED} against {@link #NOT_ASSESSABLE}: we chose not to
 * look, against we looked and could not see. A review that could not read the diff is telemetry about our
 * instrument, not a measurement of anybody's behaviour.
 */
public enum PracticeTraceOutcome {
    /** May still have found nothing to say. */
    REVIEWED,

    /** Queued or running. */
    RUNNING,

    /** For a reason that lifts on its own or when somebody changes a setting; the reaper re-offers it. */
    PENDING,

    /** Deliberately not run, for a reason that will not change for this artifact. */
    SKIPPED,

    /** A review ran but could not read what this practice needs — never read this as "practice absent". */
    NOT_ASSESSABLE,

    /**
     * Not "silenced": that describes the {@code PROPOSE} tier (measured and kept quiet), covered by
     * {@code FeedbackSuppressionReason.PRACTICE_TIER_QUIET}.
     */
    TURNED_OFF,

    /** Nothing this practice watches has happened to this artifact. The ordinary quiet answer. */
    NOT_OCCASIONED,

    /** Only watches signals no connected integration raises — not broken, waiting for a connection. */
    DORMANT,

    /** Waited longer than the ledger keeps re-offering it, and was retired unreviewed. */
    LAPSED,

    FAILED,
}
