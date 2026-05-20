/**
 * Achievement system — tracks per-user milestones and unlocks badges.
 *
 * <p>Event-driven write path: listens to {@code ActivitySavedEvent} from
 * {@link de.tum.cit.aet.hephaestus.activity} and increments progress via
 * {@code AchievementService} → {@code AchievementEvaluator} strategies.
 *
 * <p>Achievements are loaded from {@code achievements.yml} and form progression chains
 * via the {@code parent} field. A retroactive recalculation endpoint
 * ({@code AchievementRecalculationService}) replays history to assign accurate unlock
 * dates when needed.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Achievement")
package de.tum.cit.aet.hephaestus.achievement;
