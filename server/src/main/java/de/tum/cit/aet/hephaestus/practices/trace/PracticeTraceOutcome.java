package de.tum.cit.aet.hephaestus.practices.trace;

/**
 * What became of one practice on one artifact — the answer to "why didn't it say anything?".
 *
 * <p>Every constant is derived from something already recorded — a {@code SignalStateReason} on the
 * ledger, a readiness decision on the run, a tier on the practice, or the absence of a signal the
 * practice watches. Nothing is inferred from a guess.
 *
 * <p>The split that matters most is {@link #SKIPPED} against {@link #NOT_ASSESSABLE}: we chose not to
 * look, against we looked and could not see. A review that could not read the diff is telemetry about
 * our instrument, not a measurement of anybody's behaviour.
 */
public enum PracticeTraceOutcome {
    /** A review assessed this practice on this artifact. It may still have found nothing to say. */
    REVIEWED,

    /** A review carrying this practice is queued or running. */
    RUNNING,

    /**
     * Recorded and not yet acted on, for a reason that lifts on its own or when somebody changes a
     * setting. The reaper keeps re-offering it.
     */
    PENDING,

    /** Deliberately not run, for a reason that will not change for this artifact. */
    SKIPPED,

    /**
     * A review ran and could not read what this practice needs. A measurement was NOT taken — this is
     * telemetry about the evidence, and it must never be read as "the practice was absent".
     */
    NOT_ASSESSABLE,

    /**
     * The workspace turned this practice down to {@code OFF}; it is not measured at all.
     *
     * <p>Not "silenced": that describes the {@code PROPOSE} tier — measured and kept quiet — which
     * {@code FeedbackSuppressionReason.PRACTICE_TIER_QUIET} already covers. This is the tier above
     * nothing at all, and every reader of it, the webapp included, has to render it as "Turned off".
     */
    TURNED_OFF,

    /** Nothing this practice watches has happened to this artifact. The ordinary quiet answer. */
    NOT_OCCASIONED,

    /**
     * The practice watches only signals that no integration connected to this workspace raises. It is
     * not broken and not quiet; it is waiting for a connection.
     */
    DORMANT,

    /** Waited longer than the ledger keeps re-offering it, and was retired unreviewed. */
    LAPSED,

    /** The review that carried this practice did not finish. */
    FAILED,
}
