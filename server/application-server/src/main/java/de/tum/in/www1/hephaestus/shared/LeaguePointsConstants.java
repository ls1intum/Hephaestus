package de.tum.in.www1.hephaestus.shared;

/**
 * Constants for the league points (Elo-style rating) system.
 *
 * <p>These constants are shared between:
 * <ul>
 *   <li>{@code workspace} - For initializing new member league points</li>
 *   <li>{@code leaderboard} - For calculating league point changes</li>
 * </ul>
 *
 * <p>Placed in the {@code shared} package to avoid coupling between modules.
 */
public final class LeaguePointsConstants {

    private LeaguePointsConstants() {
        // Utility class - prevent instantiation
    }

    /** Default starting points for new members */
    public static final int POINTS_DEFAULT = 1000;

    /** Threshold above which players are considered "high points" */
    public static final int POINTS_THRESHOLD_HIGH = 1750;

    /** Threshold below which players are considered "low points" */
    public static final int POINTS_THRESHOLD_LOW = 1250;

    /** Minimum decay amount per period of inactivity */
    public static final int DECAY_MINIMUM = 10;

    /** Decay factor applied per period of inactivity */
    public static final double DECAY_FACTOR = 0.05;

    /** K-factor for new players (more volatile rating) */
    public static final double K_FACTOR_NEW_PLAYER = 2.0;

    /** K-factor for low-points players */
    public static final double K_FACTOR_LOW_POINTS = 1.5;

    /** K-factor for medium-points players */
    public static final double K_FACTOR_MEDIUM_POINTS = 1.2;

    /** K-factor for high-points players (more stable rating) */
    public static final double K_FACTOR_HIGH_POINTS = 1.1;
}
