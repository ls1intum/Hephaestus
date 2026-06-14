package de.tum.cit.aet.hephaestus.practices.model;

/**
 * Verdict for a practice finding — whether the practice behaviour was <em>observed</em> in the
 * contributor's work, was <em>not observed</em>, or whether the practice is irrelevant.
 *
 * <p><b>Sign-neutral.</b> A verdict states only what the detector saw; it does NOT encode "good"
 * or "bad". The good/bad direction is supplied by {@link Practice#getPolarity()}: for a
 * {@code DESIRABLE} practice {@code OBSERVED} is the strength and {@code NOT_OBSERVED} the gap; for
 * an {@code UNDESIRABLE} practice the directions invert. Keeping verdict sign-free is what lets one
 * detector schema serve both "did the good thing" and "did the bad thing" practices without the old
 * {@code NEGATIVE == bad} overload (see ADR 0021, F-6).
 *
 * <p>Orthogonal to {@link Severity}: verdict captures <em>presence</em> (observed vs not), severity
 * captures <em>impact</em> (critical vs informational).
 */
public enum Verdict {
    /** The practice behaviour is present in the contributor's changed work. */
    OBSERVED,
    /** The practice behaviour is absent where it was expected in the contributor's changed work. */
    NOT_OBSERVED,
    /** The practice does not apply to the changed work (e.g., no network calls → error-state-handling is irrelevant). */
    NOT_APPLICABLE,
}
