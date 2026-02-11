/**
 * Achievement system for tracking and rewarding user milestones.
 *
 * <h2>Architecture</h2>
 * <p>The achievement system is event-driven: it listens for {@link ActivitySavedEvent}
 * published by the activity module and incrementally updates progress toward achievements.
 *
 * <h3>Core Components</h3>
 * <ul>
 *   <li>{@link AchievementType} - Enum defining all achievements (source of truth)</li>
 *   <li>{@link UserAchievement} - JPA entity tracking achievement progress and unlocks per user</li>
 *   <li>{@link AchievementService} - Business logic for incrementing progress and unlocking achievements</li>
 *   <li>{@link AchievementEvaluator} - Strategy interface for progress updates</li>
 *   <li>{@link StandardCountEvaluator} - Default evaluator that increments by one</li>
 *   <li>{@link AchievementEventListener} - Listens to activity events to trigger evaluations</li>
 * </ul>
 *
 * <h3>Achievement Hierarchy</h3>
 * <p>Achievements are organized into categories ({@link AchievementCategory}) and form
 * progression chains via the {@code parent} relationship. For example:
 * <pre>
 * FIRST_PULL (1 PR) → PR_BEGINNER (3 PRs) → PR_APPRENTICE (5 PRs) → ...
 * </pre>
 *
 * <h3>Event Flow (Incremental Updates)</h3>
 * <pre>
 * ActivityEventService.record()
 *     ↓
 * ActivitySavedEvent published
 *     ↓
 * AchievementEventListener receives event
 *     ↓
 * AchievementService.checkAndUnlock() increments progress and unlocks if threshold met
 * </pre>
 *
 * @see de.tum.in.www1.hephaestus.activity.ActivityEventService
 */
@org.springframework.lang.NonNullApi
package de.tum.in.www1.hephaestus.achievement;
