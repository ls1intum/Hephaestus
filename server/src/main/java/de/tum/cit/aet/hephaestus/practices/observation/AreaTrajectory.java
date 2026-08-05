package de.tum.cit.aet.hephaestus.practices.observation;

/**
 * Direction of a developer's standing over two comparable evidence snapshots. The numeric delta is
 * calculated per practice first and only then aggregated for a practice area; this enum is the final,
 * developer-facing label rather than the measurement itself.
 */
public enum AreaTrajectory {
    /** The current evidence snapshot is more positive than the previous snapshot. */
    IMPROVING,
    /** The two comparable snapshots carry the same standing score. */
    STEADY,
    /** The current evidence snapshot is less positive than the previous snapshot. */
    REGRESSING;

    private static final double ZERO_EPSILON = 1.0e-9;

    /**
     * Turns a normalized score delta into its display direction. Missing comparison data is represented
     * before this method by the absence of a trajectory, not by {@link #STEADY}.
     */
    public static AreaTrajectory fromDelta(double delta) {
        if (delta > ZERO_EPSILON) {
            return IMPROVING;
        }
        if (delta < -ZERO_EPSILON) {
            return REGRESSING;
        }
        return STEADY;
    }
}
