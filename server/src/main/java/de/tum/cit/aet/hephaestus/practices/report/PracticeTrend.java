package de.tum.cit.aet.hephaestus.practices.report;

/**
 * Direction of a developer's {@link PracticeStatus} between the previous report window and the current one.
 *
 * <p>The developer's own trajectory, not a comparison against peers. Derived by
 * {@link PracticeStatusDeriver#trendOf}.
 */
public enum PracticeTrend {
    /** Fewer unresolved problems than the previous window (e.g. DEVELOPING → STRENGTH). */
    IMPROVING,
    /** More unresolved problems than the previous window (e.g. STRENGTH → DEVELOPING). */
    WORSENING,
    /** No change in problem load between the two windows. */
    STEADY,
    /** No activity in the previous window, so there is nothing to compare against — a first appearance. */
    NEW,
}
