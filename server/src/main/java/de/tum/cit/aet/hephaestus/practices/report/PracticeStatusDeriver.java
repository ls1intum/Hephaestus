package de.tum.cit.aet.hephaestus.practices.report;

/**
 * The one mapping from a developer's problem/strength signals to a {@link PracticeStatus}, and from two
 * windows' statuses to a {@link PracticeTrend}.
 *
 * <p>Every practice surface routes through here, so the {@code (hasProblems, hasStrengths) → status}
 * decision exists in exactly one place. The two booleans are computed per surface — the cards in Java over
 * the quarantine-filtered items, the roster and health in the repository's quarantine-aware SQL, both bound
 * to the same thresholds.
 */
public final class PracticeStatusDeriver {

    private PracticeStatusDeriver() {}

    /**
     * Derive a status from whether the developer has any problems and/or any strengths on a practice in the
     * window.
     *
     * <ul>
     *   <li>problems AND strengths → {@link PracticeStatus#MIXED}
     *   <li>problems only → {@link PracticeStatus#DEVELOPING}
     *   <li>strengths only → {@link PracticeStatus#STRENGTH}
     *   <li>neither → {@link PracticeStatus#NO_ACTIVITY}
     * </ul>
     *
     * @param hasProblems whether at least one actionable (BAD) item survived the quarantine floor
     * @param hasStrengths whether at least one strength (GOOD) surfaced
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

    /** Unresolved gaps: DEVELOPING or MIXED. A triage signal for a mentor's limited time, not a demerit. */
    public static boolean needsAttention(PracticeStatus status) {
        return status == PracticeStatus.DEVELOPING || status == PracticeStatus.MIXED;
    }

    /**
     * Problem-load order used ONLY to diff two windows ({@link #trendOf}); never serialised, because it is
     * the sort key a leaderboard would need. Higher rank = fewer unresolved problems.
     */
    private static int problemLoadRank(PracticeStatus status) {
        return switch (status) {
            case DEVELOPING -> 0;
            case MIXED -> 1;
            case STRENGTH -> 2;
            case NO_ACTIVITY -> -1; // neutral; both callers guard NO_ACTIVITY before ranking (see trendOf)
        };
    }

    /**
     * Trend between the previous window's status and the current one.
     *
     * <ul>
     *   <li>current is {@link PracticeStatus#NO_ACTIVITY} → {@link PracticeTrend#STEADY} (nothing happened
     *       this window to read a direction from; calling that "improving" would reward inactivity)
     *   <li>previous is {@link PracticeStatus#NO_ACTIVITY} and current is not → {@link PracticeTrend#NEW}
     *   <li>otherwise compare {@link #problemLoadRank}: higher → {@link PracticeTrend#IMPROVING}, lower →
     *       {@link PracticeTrend#WORSENING}, equal → {@link PracticeTrend#STEADY}
     * </ul>
     */
    public static PracticeTrend trendOf(PracticeStatus previousStatus, PracticeStatus currentStatus) {
        if (currentStatus == PracticeStatus.NO_ACTIVITY) {
            return PracticeTrend.STEADY;
        }
        if (previousStatus == PracticeStatus.NO_ACTIVITY) {
            return PracticeTrend.NEW;
        }
        int previousRank = problemLoadRank(previousStatus);
        int currentRank = problemLoadRank(currentStatus);
        if (currentRank > previousRank) {
            return PracticeTrend.IMPROVING;
        }
        if (currentRank < previousRank) {
            return PracticeTrend.WORSENING;
        }
        return PracticeTrend.STEADY;
    }
}
