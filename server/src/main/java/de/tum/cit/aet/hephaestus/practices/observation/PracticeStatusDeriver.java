package de.tum.cit.aet.hephaestus.practices.observation;

/**
 * The single, shared mapping from a developer's problem/strength signals to a per-practice
 * {@link PracticeStatus}. Both the developer's own reflection cards
 * ({@link ObservationService#getPracticeReport}) and the mentor overview (cohort + roster) map through here, so
 * the {@code (hasProblems, hasStrengths) -> standing} decision is defined in exactly one place. The two
 * inputs themselves are computed per-surface (reflection in Java, the cohort/roster in the repository's
 * quarantine-aware SQL) — both apply the same quarantine floor so the surfaces agree.
 */
public final class PracticeStatusDeriver {

    private PracticeStatusDeriver() {}

    /**
     * Derive a standing from whether the developer has any problems and/or any strengths surfaced on a
     * practice in the window.
     *
     * <ul>
     *   <li>problems AND strengths → {@link PracticeStatus#MIXED}
     *   <li>problems only → {@link PracticeStatus#DEVELOPING}
     *   <li>strengths only → {@link PracticeStatus#STRENGTH}
     *   <li>neither → {@link PracticeStatus#NO_ACTIVITY}
     * </ul>
     *
     * @param hasProblems whether at least one actionable (BAD) item survived filtering for this practice
     * @param hasStrengths whether at least one strength (GOOD) surfaced for this practice
     */
    public static PracticeStatus derive(boolean hasProblems, boolean hasStrengths) {
        if (hasProblems && hasStrengths) {
            return PracticeStatus.MIXED;
        }
        if (hasProblems) {
            return PracticeStatus.DEVELOPING;
        }
        if (hasStrengths) {
            return PracticeStatus.STRENGTH;
        }
        return PracticeStatus.NO_ACTIVITY;
    }

    /**
     * A developer "needs attention" on a practice when their standing shows unresolved gaps — i.e. it is
     * {@link PracticeStatus#DEVELOPING} or {@link PracticeStatus#MIXED}. STRENGTH and NO_ACTIVITY do not.
     * This is a triage signal for a mentor, not a demerit.
     */
    public static boolean needsAttention(PracticeStatus standing) {
        return standing == PracticeStatus.DEVELOPING || standing == PracticeStatus.MIXED;
    }
}
