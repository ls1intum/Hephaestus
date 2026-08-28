package de.tum.cit.aet.hephaestus.leaderboard;

/**
 * Leaderboard aggregation mode.
 *
 * <p>Determines whether rankings are computed per individual user
 * or aggregated by team.
 */
public enum LeaderboardMode {
    /** Rank individual users by their personal scores */
    INDIVIDUAL,
    /** Rank teams by aggregated member scores */
    TEAM,
}
