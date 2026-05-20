/**
 * Read model for developer activity rankings. Reads from
 * {@code activity_event} (written by {@link de.tum.cit.aet.hephaestus.activity}); never
 * writes. XP aggregation uses SUM/GROUP BY in SQL, not in-memory iteration.
 *
 * <p>Also owns {@code workspace_membership.league_points} via {@link LeaguePointsService}
 * and the Elo-style rating constants in {@link LeaguePointsConstants}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Leaderboard")
package de.tum.cit.aet.hephaestus.leaderboard;
