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

    /**
     * Problem-load rank used ONLY to diff two standings across cycles ({@link #trendOf}) — never exposed on
     * the wire (that would re-create a leaderboard, ADR 0023). Lower rank = more unresolved problems.
     */
    private static int problemLoadRank(PracticeStatus standing) {
        return switch (standing) {
            case DEVELOPING -> 0;
            case MIXED -> 1;
            case STRENGTH -> 2;
            case NO_ACTIVITY -> -1; // neutral; callers guard NO_ACTIVITY before ranking (see trendOf)
        };
    }

    /**
     * Cycle-over-cycle {@link PracticeTrend} comparing a prior-cycle standing to the current one.
     *
     * <ul>
     *   <li>current is {@link PracticeStatus#NO_ACTIVITY} → {@link PracticeTrend#STEADY} (nothing this cycle
     *       to read a direction from)
     *   <li>prior is {@link PracticeStatus#NO_ACTIVITY} (and current is not) → {@link PracticeTrend#NEW} (a
     *       first appearance — no prior cycle to compare against)
     *   <li>otherwise, compare {@link #problemLoadRank}: current &gt; prior → {@link PracticeTrend#IMPROVING},
     *       current &lt; prior → {@link PracticeTrend#WORSENING}, equal → {@link PracticeTrend#STEADY}
     * </ul>
     */
    public static PracticeTrend trendOf(PracticeStatus priorStanding, PracticeStatus currentStanding) {
        if (currentStanding == PracticeStatus.NO_ACTIVITY) {
            return PracticeTrend.STEADY;
        }
        if (priorStanding == PracticeStatus.NO_ACTIVITY) {
            return PracticeTrend.NEW;
        }
        int priorRank = problemLoadRank(priorStanding);
        int currentRank = problemLoadRank(currentStanding);
        if (currentRank > priorRank) {
            return PracticeTrend.IMPROVING;
        }
        if (currentRank < priorRank) {
            return PracticeTrend.WORSENING;
        }
        return PracticeTrend.STEADY;
    }
}
