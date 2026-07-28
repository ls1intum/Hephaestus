package de.tum.cit.aet.hephaestus.practices.report;

/**
 * Where one developer stands on one practice or area within the report window, read against the practice's
 * own standard (ADR 0028).
 *
 * <p>Derived once by {@link PracticeStatusDeriver} so every surface agrees. Cards never emit
 * {@link #NO_ACTIVITY} — a contentless card is skipped — while the roster and health rollup need it.
 */
public enum PracticeStatus {
    /** Only problems surfaced (no strengths) — the focus of attention. */
    DEVELOPING,
    /** Only strengths — a confirmed good habit. */
    STRENGTH,
    /** Both problems and strengths across the developer's work in the window. */
    MIXED,
    /**
     * Nothing to say either way. On a roster cell that means no observations on the area at all; in the
     * health rollup it means the developer had observations but none that survived the quarantine floor,
     * because the rollup only counts developers who appear in the window.
     */
    NO_ACTIVITY,
}
