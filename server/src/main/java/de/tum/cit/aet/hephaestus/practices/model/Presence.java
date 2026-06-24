package de.tum.cit.aet.hephaestus.practices.model;

/**
 * Presence for a practice finding — whether the practice behaviour was <em>observed</em> in the
 * developer's work, was <em>not observed</em>, or whether the practice is irrelevant.
 *
 * <p><b>Sign-neutral.</b> A observation states only what the detector saw; it does NOT encode "good"
 * or "bad". The good/bad direction is supplied by {@link Practice#getKind()}: for a
 * {@code GOOD_PRACTICE} practice {@code OBSERVED} is the strength and {@code NOT_OBSERVED} the gap; for
 * an {@code BAD_PRACTICE} practice the directions invert. Keeping observation sign-free is what lets one
 * detector schema serve both good practices ({@code OBSERVED} = strength) and bad practices
 * ({@code NOT_OBSERVED} = strength) without overloading the observation value (see ADR 0021, F-6).
 *
 * <p>Orthogonal to {@link Severity}: observation captures <em>presence</em> (observed vs not), severity
 * captures <em>impact</em> (critical vs informational).
 */
public enum Presence {
    /** The practice behaviour is present in the developer's changed work. */
    OBSERVED,
    /** The practice behaviour is absent where it was expected in the developer's changed work. */
    NOT_OBSERVED,
    /** The practice does not apply to the changed work (e.g., no network calls → error-state-handling is irrelevant). */
    NOT_APPLICABLE,
}
