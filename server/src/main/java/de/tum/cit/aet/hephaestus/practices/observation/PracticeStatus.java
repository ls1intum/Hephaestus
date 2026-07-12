package de.tum.cit.aet.hephaestus.practices.observation;

/**
 * Criterion-referenced status of one developer on one practice, derived from their own feedback within
 * the recency window. This is a coarse, human reading of "where this developer stands against the practice
 * standard". The developer's own reflection dashboard uses the same values (it simply never emits
 * {@link #NO_ACTIVITY} — a contentless card is skipped), while the roster/workspace-health views additionally
 * use {@link #NO_ACTIVITY} where a developer has no observations on a given practice in the window.
 *
 * <p>Both the developer's own reflection cards and the mentor overview (workspace health aggregation +
 * roster) map to a status through the single {@link PracticeStatusDeriver}, so the {@code (hasProblems,
 * hasStrengths) -> status} decision is defined once. The two inputs are computed per-surface — reflection in
 * Java, the health/roster in SQL — and both apply the same quarantine floor so they agree; keep the two in
 * sync.
 */
public enum PracticeStatus {
    /** Only problems surfaced (no strengths) — the focus of attention. */
    DEVELOPING,
    /** Only strengths — a confirmed good habit. */
    STRENGTH,
    /** Both problems and strengths across the developer's work in the window. */
    MIXED,
    /** No observations for this practice in the window — nothing to say either way. */
    NO_ACTIVITY,
}
