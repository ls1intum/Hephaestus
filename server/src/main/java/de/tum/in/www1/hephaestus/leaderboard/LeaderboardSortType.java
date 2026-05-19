package de.tum.in.www1.hephaestus.leaderboard;

/**
 * Leaderboard ranking metric.
 *
 * <p>Determines the primary sort order for leaderboard entries.
 */
public enum LeaderboardSortType {
    /** Rank by XP score earned in the current timeframe */
    SCORE,
    /** Rank by accumulated league points (Elo-style rating) */
    LEAGUE_POINTS,
}
