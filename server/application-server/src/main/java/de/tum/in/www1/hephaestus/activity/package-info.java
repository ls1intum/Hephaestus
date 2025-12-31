/**
 * Activity Event Ledger - append-only event log for developer activity tracking.
 *
 * <h2>Architecture (CQRS-style)</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │                           WRITE PATH (activity module)                      │
 * │  DomainEvents → ActivityEventListener → ActivityEventService → DB          │
 * │                        ↓                                                    │
 * │              ExperiencePointCalculator                                      │
 * │              (XP computed ONCE at write time)                               │
 * └─────────────────────────────────────────────────────────────────────────────┘
 *                                   ↓
 *                         activity_event table
 *                         (source of truth for XP)
 *                                   ↓
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │                           READ PATH (leaderboard module)                    │
 * │  LeaderboardService → LeaderboardXpQueryService → ActivityEventRepository  │
 * │                                                    (SUM, GROUP BY queries)  │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link de.tum.in.www1.hephaestus.activity.ActivityEvent} - Immutable event entity</li>
 *   <li>{@link de.tum.in.www1.hephaestus.activity.ActivityEventListener} - Domain→Activity bridge</li>
 *   <li>{@link de.tum.in.www1.hephaestus.activity.ActivityEventService} - Event persistence</li>
 *   <li>{@link de.tum.in.www1.hephaestus.activity.scoring.ExperiencePointCalculator} - XP formulas</li>
 * </ul>
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><strong>Append-only</strong>: Events are immutable once written (no @Setter)</li>
 *   <li><strong>Idempotent</strong>: event_key ensures deduplication</li>
 *   <li><strong>Pre-computed XP</strong>: XP calculated at write time, not on read</li>
 *   <li><strong>DB-level aggregation</strong>: SUM(xp) GROUP BY, not in-memory loops</li>
 *   <li><strong>Clean boundaries</strong>: Leaderboard queries live in leaderboard module</li>
 * </ul>
 *
 * <h2>Event Types</h2>
 * <p>See {@link de.tum.in.www1.hephaestus.activity.ActivityEventType} for supported types.
 *
 * @see <a href="https://cloudevents.io/">CloudEvents Specification</a>
 * @see <a href="https://www.w3.org/TR/activitystreams-core/">ActivityStreams 2.0</a>
 */
package de.tum.in.www1.hephaestus.activity;
