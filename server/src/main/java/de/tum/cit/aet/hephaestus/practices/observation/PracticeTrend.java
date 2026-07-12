package de.tum.cit.aet.hephaestus.practices.observation;

/**
 * Cycle-over-cycle direction of a developer's {@link PracticeStatus} on one practice or area, comparing the
 * current review-cycle window's standing to the immediately-prior cycle's. Criterion-referenced, like
 * {@link PracticeStatus} itself — this describes the developer's OWN trajectory, never a comparison against
 * peers (ADR 0023 / {@code NonCompetitiveSurfaceArchTest}).
 *
 * <p>Derived by {@link PracticeStatusDeriver#trendOf}.
 */
public enum PracticeTrend {
    /** The standing's problem-load rank improved (e.g. DEVELOPING → STRENGTH). */
    IMPROVING,
    /** The standing's problem-load rank worsened (e.g. STRENGTH → DEVELOPING). */
    WORSENING,
    /** No meaningful change in rank between the two cycles. */
    STEADY,
    /** No activity in the prior cycle, so there is nothing to compare against — a first appearance. */
    NEW,
}
