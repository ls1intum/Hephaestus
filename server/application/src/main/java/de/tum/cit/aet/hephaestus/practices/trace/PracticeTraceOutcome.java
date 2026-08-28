package de.tum.cit.aet.hephaestus.practices.trace;

/**
 * Recorded disposition of a practice on an artifact. {@link #SKIPPED} was excluded or inapplicable;
 * {@link #NOT_REACHED} was eligible but unevaluated; {@link #NOT_ASSESSABLE} lacked required evidence.
 */
public enum PracticeTraceOutcome {
    REVIEWED,

    RUNNING,

    PENDING,

    SKIPPED,

    NOT_REACHED,

    NOT_ASSESSABLE,

    TURNED_OFF,

    NOT_OCCASIONED,

    DORMANT,

    LAPSED,

    FAILED,
}
