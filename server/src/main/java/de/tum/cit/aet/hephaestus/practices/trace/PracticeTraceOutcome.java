package de.tum.cit.aet.hephaestus.practices.trace;

/**
 * What became of one practice on one artifact — the answer to "why didn't it say anything?". Every
 * constant is derived from something already recorded (a {@code SignalStateReason}, a readiness decision,
 * an autonomy, or the absence of a watched signal), never from a guess.
 *
 * <p>The split that matters most is {@link #SKIPPED} against {@link #NOT_ASSESSABLE}: we chose not to
 * look, against we looked and could not see. A review that could not read the diff is telemetry about our
 * instrument, not a measurement of anybody's behaviour.
 */
public enum PracticeTraceOutcome {
    REVIEWED,

    RUNNING,

    PENDING,

    SKIPPED,

    /** A review ran but lacked the evidence required to assess this practice. */
    NOT_ASSESSABLE,

    TURNED_OFF,

    NOT_OCCASIONED,

    DORMANT,

    LAPSED,

    FAILED,
}
