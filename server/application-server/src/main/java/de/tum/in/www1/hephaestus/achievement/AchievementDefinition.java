package de.tum.in.www1.hephaestus.achievement;

import com.fasterxml.jackson.annotation.JsonValue;
import de.tum.in.www1.hephaestus.achievement.evaluator.AchievementEvaluator;
import de.tum.in.www1.hephaestus.achievement.evaluator.StandardCountEvaluator;
import de.tum.in.www1.hephaestus.achievement.progress.AchievementProgress;
import de.tum.in.www1.hephaestus.achievement.progress.LinearAchievementProgress;
import de.tum.in.www1.hephaestus.activity.ActivityEventType;
import lombok.Getter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.*;

/**
 * The source of truth for all achievement definitions.
 *
 * <p>Each achievement is defined by:
 * <ul>
 *   <li>{@code id} - Unique identifier string (used for storage in database)</li>
 *   <li>{@code category} - Grouping category</li>
 *   <li>{@code rarity} - intended difficulty parameter (also used for styling)</li>
 *   <li>{@code requiredCount} - Number of events needed to unlock</li>
 *   <li>{@code parent} - Previous achievement in the progression chain (nullable)</li>
 *   <li>{@code triggerEvents} - Activity event types that contribute to this achievement</li>
 * </ul>
 *
 * <h3>Enum-Strategy Pattern</h3>
 * <p>Each constant declares an {@code evaluatorClass} linking it to a Spring-managed
 * {@link AchievementEvaluator} implementation. This lets the {@link AchievementService}
 * resolve the correct evaluation strategy at runtime, keeping the system open for extensions
 * (like new evaluators) without modification.
 */
@Getter
public enum AchievementDefinition {

    /* ========================================================================
     * Pull Request Achievements
     *
     * <h3>Pull Request Achievement Chain</h3>
     * <pre>
     * Level 1: PR_MERGED_COMMON_1      (1 PR)   - "First Leaf"
     * Level 2: BRANCH_GRAFTER  (3 PRs)  - "Branch Grafter"
     * Level 3: TREE_SURGEON    (7 PRs)  - "Tree Surgeon"
     * Level 4: TRUNK_MASTER    (15 PRs) - "Trunk Master"
     * Level 5: FOREST_KEEPER   (30 PRs) - "Forest Keeper"
     * Level 6: ROOT_OF_ORIGIN  (50 PRs) - "Root of Origin"
     *
     * Special: SPEEDSTER       (Fast PR) - "Speedster"
     * </pre>
     * ======================================================================== */


    PR_MERGED_COMMON_1(
        "pr.merged.common.1",
        AchievementCategory.PULL_REQUESTS,
        AchievementRarity.COMMON,
        new LinearAchievementProgress(1),
        null,
        false,
        Set.of(ActivityEventType.PULL_REQUEST_MERGED),
        StandardCountEvaluator.class
    ),

    PR_MERGED_COMMON_2(
        "pr.merged.common.2",
        AchievementCategory.PULL_REQUESTS,
        AchievementRarity.COMMON,
        new LinearAchievementProgress(3),
        PR_MERGED_COMMON_1,
        false,
        Set.of(ActivityEventType.PULL_REQUEST_MERGED),
        StandardCountEvaluator.class
    ),

    PR_MERGED_UNCOMMON(
        "pr.merged.uncommon",
        AchievementCategory.PULL_REQUESTS,
        AchievementRarity.UNCOMMON,
        new LinearAchievementProgress(7),
        PR_MERGED_COMMON_2,
        false,
        Set.of(ActivityEventType.PULL_REQUEST_MERGED),
        StandardCountEvaluator.class
    ),

    PR_MERGED_RARE(
        "pr.merged.rare",
        AchievementCategory.PULL_REQUESTS,
        AchievementRarity.RARE,
        new LinearAchievementProgress(15),
        PR_MERGED_UNCOMMON,
        false,
        Set.of(ActivityEventType.PULL_REQUEST_MERGED),
        StandardCountEvaluator.class
    ),

    PR_MERGED_EPIC(
        "pr.merged.epic",
        AchievementCategory.PULL_REQUESTS,
        AchievementRarity.EPIC,
        new LinearAchievementProgress(30),
        PR_MERGED_RARE,
        false,
        Set.of(ActivityEventType.PULL_REQUEST_MERGED),
        StandardCountEvaluator.class
    ),

    PR_MERGED_LEGENDARY(
        "pr.merged.legendary",
        AchievementCategory.PULL_REQUESTS,
        AchievementRarity.LEGENDARY,
        new LinearAchievementProgress(50),
        PR_MERGED_EPIC,
        false,
        Set.of(ActivityEventType.PULL_REQUEST_MERGED),
        StandardCountEvaluator.class
    ),

    PR_SPECIAL_SPEEDSTER(
        "pr.special.speedster",
        AchievementCategory.PULL_REQUESTS,
        AchievementRarity.EPIC,
        new LinearAchievementProgress(1),
        null, // tbd parent
        false,
        Set.of(ActivityEventType.PULL_REQUEST_MERGED),
        StandardCountEvaluator.class
    ),

    // ========================================================================
    // Review Achievements (triggered by any review activity)
    // ========================================================================

    FIRST_REVIEW(
        "first_review",
        AchievementCategory.COMMUNICATION,
        AchievementRarity.COMMON,
        new LinearAchievementProgress( 1),
        null,
        false,
        Set.of(
            ActivityEventType.REVIEW_APPROVED,
            ActivityEventType.REVIEW_CHANGES_REQUESTED,
            ActivityEventType.REVIEW_COMMENTED
        ),
        StandardCountEvaluator.class
    ),

    REVIEW_ROOKIE(
        "review_rookie",
        AchievementCategory.COMMUNICATION,
        AchievementRarity.COMMON,
        new LinearAchievementProgress( 10),
        FIRST_REVIEW,
        false,
        Set.of(
            ActivityEventType.REVIEW_APPROVED,
            ActivityEventType.REVIEW_CHANGES_REQUESTED,
            ActivityEventType.REVIEW_COMMENTED
        ),
        StandardCountEvaluator.class
    ),

    REVIEW_MASTER(
        "review_master",
        AchievementCategory.COMMUNICATION,
        AchievementRarity.EPIC,
        new LinearAchievementProgress( 100),
        REVIEW_ROOKIE,
        false,
        Set.of(
            ActivityEventType.REVIEW_APPROVED,
            ActivityEventType.REVIEW_CHANGES_REQUESTED,
            ActivityEventType.REVIEW_COMMENTED
        ),
        StandardCountEvaluator.class
    ),

    // ========================================================================
    // Comment Achievements (triggered by inline code review comments)
    // ========================================================================

    CODE_COMMENTER(
        "code_commenter",
        AchievementCategory.COMMUNICATION,
        AchievementRarity.EPIC,
        new LinearAchievementProgress( 100),
        null,
        false,
        Set.of(ActivityEventType.REVIEW_COMMENT_CREATED),
        StandardCountEvaluator.class
    ),

    // ========================================================================
    // Approval Achievements (triggered by review approvals)
    // ========================================================================

    HELPFUL_REVIEWER(
        "helpful_reviewer",
        AchievementCategory.COMMUNICATION,
        AchievementRarity.LEGENDARY,
        new LinearAchievementProgress( 50),
        null,
        false,
        Set.of(ActivityEventType.REVIEW_APPROVED),
        StandardCountEvaluator.class
    );

    @NonNull
    @Getter(onMethod_ = @JsonValue)
    private final String id;

    private final AchievementCategory category;
    /**
     * -- GETTER --
     * Visual rarity for UI styling.
     * Higher rarities have more prestigious ring/badge designs.
     */
    private final AchievementRarity rarity;
    /**
     * -- GETTER --
     * Number of qualifying events required to unlock this achievement.
     * Requirements are initialized as a subclass of an {@link AchievementProgress} object.
     */
    private final AchievementProgress requirements;

    @Nullable
    private final AchievementDefinition parent;

    /**
     * -- GETTER --
     * Boolean value that denotes if the achievement should be hidden to the player when not unlocked
     */
    private final boolean isHidden;

    /**
     * -- GETTER --
     * Activity event types that contribute to unlocking this achievement.
     */
    private final Set<ActivityEventType> triggerEvents;
    /**
     * -- GETTER --
     * The {@link AchievementEvaluator} implementation class used to evaluate
     * progress for this achievement. Resolved at runtime via the Spring-managed
     * evaluator strategy map in {@link AchievementService}.
     */
    private final Class<? extends AchievementEvaluator> evaluatorClass;

    AchievementDefinition(
        String id,
        AchievementCategory category,
        AchievementRarity rarity,
        AchievementProgress requirements,
        @Nullable AchievementDefinition parent,
        boolean isHidden,
        Set<ActivityEventType> triggerEvents,
        Class<? extends AchievementEvaluator> evaluatorClass
    ) {
        this.id = id;
        this.category = category;
        this.rarity = rarity;
        this.requirements = requirements;
        this.parent = parent;
        this.isHidden = isHidden;
        this.triggerEvents = triggerEvents;
        this.evaluatorClass = evaluatorClass;
    }

    /**
     * Parent achievement that must be unlocked before this one.
     * Returns null if this is the first achievement in the chain.
     */
    @Nullable
    public AchievementDefinition getParent() {
        return parent;
    }

    /**
     * Find an achievement type by its ID.
     *
     * @param id the achievement ID
     * @return the matching achievement type
     * @throws IllegalArgumentException if ID is unknown
     */
    public static AchievementDefinition fromId(String id) {
        for (AchievementDefinition type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown achievement ID: " + id);
    }

    /**
     * Get all achievements in a specific category.
     *
     * @param category the category to filter by
     * @return list of achievements in that category, ordered by rarity
     */
    public static List<AchievementDefinition> getByCategory(AchievementCategory category) {
        return Arrays.stream(values())
            .filter(a -> a.category == category)
            .sorted(Comparator.comparing(AchievementDefinition::getRarity, AchievementRarity.RARITY_COMPARATOR))
            .toList();
    }

    /**
     * Get all achievements triggered by a specific event type.
     *
     * @param eventType the activity event type
     * @return list of achievements that this event contributes to
     */
    public static List<AchievementDefinition> getByTriggerEvent(ActivityEventType eventType) {
        return Arrays.stream(values())
            .filter(a -> a.triggerEvents.contains(eventType))
            .toList();
    }
}
