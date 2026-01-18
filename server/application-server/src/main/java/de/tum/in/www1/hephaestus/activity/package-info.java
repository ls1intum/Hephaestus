/**
 * Activity Event Log - append-only event log for developer activity tracking and gamification.
 *
 * <h2>Bounded Context</h2>
 * <p>This module is a distinct <strong>Bounded Context</strong> focused exclusively on:
 * <ul>
 *   <li>Recording developer activity events (PRs, reviews, comments)</li>
 *   <li>Computing and storing XP for gamification</li>
 *   <li>Providing data for leaderboard aggregation</li>
 * </ul>
 *
 * <p><strong>Note:</strong> Bad practice detection is a separate bounded context in the
 * {@link de.tum.in.www1.hephaestus.practices} module. This separation follows DDD principles
 * where "activity tracking" and "code health analysis" are distinct domain concerns.
 *
 * <h2>Pattern: Event Log (not Event Sourcing)</h2>
 * <p>This module implements an <strong>Event Log</strong> pattern - an append-only, immutable
 * record of state changes with pre-computed derived values (XP). This is the scientifically
 * correct pattern for gamification and leaderboard systems.
 *
 * <p><strong>Event Log vs Event Sourcing:</strong>
 * <ul>
 *   <li><em>Event Log</em>: Records facts with pre-computed aggregates. Optimized for queries.</li>
 *   <li><em>Event Sourcing</em>: Derives state by replaying events. Optimized for audit trails.</li>
 * </ul>
 *
 * <p>For leaderboards, Event Log is the superior choice because:
 * <ul>
 *   <li>XP is computed once at write time (O(1) per event)</li>
 *   <li>Aggregation uses database SUM/GROUP BY (index-only scans)</li>
 *   <li>No replay required = constant-time reads regardless of event count</li>
 * </ul>
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
 *   <li>{@link de.tum.in.www1.hephaestus.activity.ActivityEventService} - Event persistence with retry</li>
 *   <li>{@link de.tum.in.www1.hephaestus.activity.ActivityIntegrityScheduler} - Periodic hash verification</li>
 *   <li>{@link de.tum.in.www1.hephaestus.activity.scoring.ExperiencePointCalculator} - XP formulas</li>
 * </ul>
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><strong>Bounded Context</strong>: Pure activity tracking, no AI/detection concerns</li>
 *   <li><strong>Append-only</strong>: Events are immutable once written (no @Setter)</li>
 *   <li><strong>Idempotent</strong>: event_key ensures deduplication</li>
 *   <li><strong>Pre-computed XP</strong>: XP calculated at write time, not on read</li>
 *   <li><strong>DB-level aggregation</strong>: SUM(xp) GROUP BY, not in-memory loops</li>
 *   <li><strong>Clean boundaries</strong>: Leaderboard queries live in leaderboard module</li>
 *   <li><strong>Verifiable</strong>: SHA-256 content hashes with scheduled verification</li>
 * </ul>
 *
 * <h2>Event Types</h2>
 * <p>See {@link de.tum.in.www1.hephaestus.activity.ActivityEventType} for supported types.
 *
 * @see de.tum.in.www1.hephaestus.practices Code Health module (AI-powered bad practice detection)
 * @see <a href="https://cloudevents.io/">CloudEvents Specification</a>
 * @see <a href="https://www.w3.org/TR/activitystreams-core/">ActivityStreams 2.0</a>
 */
package de.tum.in.www1.hephaestus.activity;
