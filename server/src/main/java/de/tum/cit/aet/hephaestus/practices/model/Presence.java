package de.tum.cit.aet.hephaestus.practices.model;

/**
 * Whether the target signal a practice looks for was seen in the developer's work, was expected but
 * absent, or whether the practice does not apply at all (ADR 0022).
 *
 * <p><b>Measurement, not evaluation.</b> Presence states only what the detector <em>saw</em>; it does
 * NOT encode "good" or "bad". The good/bad direction is a second, orthogonal axis carried by
 * {@link Assessment} and resolved per observation by the detector reading the practice criteria and
 * {@code what_good_looks_like}. The 2×2 of {@code (presence, assessment)} reads directly: a present
 * good behaviour is a strength, a present bad behaviour is a problem (commission), an absent good
 * behaviour is a gap (omission), an absent bad behaviour is clean.
 *
 * <p>Orthogonal to {@link Severity}: presence captures whether the signal was seen, severity captures
 * impact (critical vs informational) and is meaningful only for a {@link Assessment#BAD} observation.
 */
public enum Presence {
    /** The target signal is present in the developer's changed work. */
    PRESENT,
    /** The target signal is absent where it was expected in the developer's changed work. */
    ABSENT,
    /** The practice does not apply to the changed work (e.g., no network calls → error-state-handling is irrelevant). */
    NOT_APPLICABLE,
}
