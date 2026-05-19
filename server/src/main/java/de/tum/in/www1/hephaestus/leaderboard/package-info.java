/**
 * Leaderboard module - read model for developer activity rankings.
 *
 * <h2>Architecture (CQRS Read Side)</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │                      LEADERBOARD MODULE (Read Path)                         │
 * │                                                                             │
 * │  LeaderboardController                                                      │
 * │         ↓                                                                   │
 * │  LeaderboardService (orchestration + DTO mapping)                           │
 * │         ↓                                                                   │
 * │  ┌─────────────────────────────────────────────────────────────┐           │
 * │  │ LeaderboardXpQueryService    TeamPathResolver               │           │
 * │  │   (XP aggregation)            (team hierarchy)              │           │
 * │  └─────────────────────────────────────────────────────────────┘           │
 * │         ↓                                                                   │
 * │  ┌─────────────────────────────────────────────────────────────┐           │
 * │  │ ActivityEventRepository                                     │           │
 * │  │   (SUM/GROUP BY on activity_event table)                    │           │
 * │  └─────────────────────────────────────────────────────────────┘           │
 * └─────────────────────────────────────────────────────────────────────────────┘
 *                                   ↑
 *                         activity_event table
 *                         (source of truth for XP)
 *                                   ↑
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │                      ACTIVITY MODULE (Write Path)                           │
 * │  DomainEvents → ActivityEventListener → ActivityEventService → DB          │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link de.tum.in.www1.hephaestus.leaderboard.LeaderboardService} - Main orchestrator</li>
 *   <li>{@link de.tum.in.www1.hephaestus.leaderboard.LeaderboardXpQueryService} - XP aggregation from events</li>
 *   <li>{@link de.tum.in.www1.hephaestus.leaderboard.TeamPathResolver} - Team hierarchy resolution</li>
 *   <li>{@link de.tum.in.www1.hephaestus.leaderboard.LeaderboardUserXp} - Immutable user XP record</li>
 *   <li>{@link de.tum.in.www1.hephaestus.leaderboard.LeaguePointsService} - League ranking logic</li>
 * </ul>
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><strong>Read-only</strong>: This module never writes to activity_event</li>
 *   <li><strong>Pre-computed XP</strong>: Reads aggregated XP, doesn't recalculate</li>
 *   <li><strong>DB aggregation</strong>: Uses SUM/GROUP BY in SQL, not in-memory</li>
 *   <li><strong>Immutable DTOs</strong>: All data carriers are Java records</li>
 *   <li><strong>Team-aware</strong>: Supports individual and team leaderboards</li>
 * </ul>
 *
 * <h2>Scoring Sources</h2>
 * <ul>
 *   <li><strong>XP (Experience Points)</strong>: From {@code activity_event.xp} column</li>
 *   <li><strong>League Points</strong>: From {@code workspace_membership.league_points}</li>
 * </ul>
 *
 * @see de.tum.in.www1.hephaestus.activity Activity module (write side)
 */
package de.tum.in.www1.hephaestus.leaderboard;
