package de.tum.in.www1.hephaestus.achievement;

import com.fasterxml.jackson.annotation.JsonValue;
import de.tum.in.www1.hephaestus.achievement.progress.AchievementProgress;
import de.tum.in.www1.hephaestus.activity.ActivityEventType;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Set;

/**
 * Record acting as the central source of truth for all achievement definitions.
 * Replaces the previous AchievementDefinition enum and maintains metadata from achievements.yml.
 *
 * @param id             Unique identifier string (used for storage in database)
 * @param category       Grouping category
 * @param rarity         Visual rarity for UI styling. Higher rarities have more prestigious ring/badge designs.
 * @param requirements   Number of qualifying events required to unlock this achievement.
 *                       Requirements are initialized as a subclass of an {@link AchievementProgress} object.
 * @param parent         Previous achievement in the progression chain (nullable)
 * @param isHidden       Boolean value that denotes if the achievement should be hidden to the player when not unlocked
 * @param triggerEvents  Activity event types that contribute to unlocking this achievement.
 * @param evaluatorClass The implementation class used to evaluate progress for this achievement.
 *                       Resolved at runtime via the Spring-managed evaluator strategy map.
 */
public record AchievementDefinition(
    @NonNull @JsonValue String id,
    @NonNull AchievementCategory category,
    @NonNull AchievementRarity rarity,
    @NonNull AchievementProgress requirements,
    @Nullable String parent,
    boolean isHidden,
    @Nullable Set<ActivityEventType> triggerEvents,
    @NonNull String evaluatorClass
) {
}
