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
     * Pull Request Achievements (Level 1-7)
     *
     * <h3>Pull Request Achievement Chain</h3>
     * <pre>
     * Level 1: FIRST_PULL      (1 PR)   - "First Merge"
     * Level 2: PR_BEGINNER     (3 PRs)  - "Beginner Integrator"
     * Level 3: PR_APPRENTICE   (5 PRs)  - "Apprentice Integrator"
     * Level 4: INTEGRATION_REGULAR (10 PRs) - "Integration Regular"
     * Level 5: PR_SPECIALIST   (25 PRs) - "Integration Specialist"
     * Level 6: INTEGRATION_EXPERT (50 PRs) - "Integration Expert"
     * Level 7: MASTER_INTEGRATOR (100 PRs) - "Master Integrator"
     * </pre>
     * ======================================================================== */


    FIRST_PULL(
        "first_pull",
        AchievementCategory.PULL_REQUESTS,
        AchievementRarity.COMMON,
        new LinearAchievementProgress(1),
        null,
        Set.of(ActivityEventType.PULL_REQUEST_MERGED),
        StandardCountEvaluator.class
    ),

    PR_BEGINNER(
        "pr_beginner",
        AchievementCategory.PULL_REQUESTS,
        AchievementRarity.COMMON,
        new LinearAchievementProgress(3),
        FIRST_PULL,
        Set.of(ActivityEventType.PULL_REQUEST_MERGED),
        StandardCountEvaluator.class
    ),

    PR_APPRENTICE(
        "pr_apprentice",
        AchievementCategory.PULL_REQUESTS,
        AchievementRarity.UNCOMMON,
        new LinearAchievementProgress(5),
        PR_BEGINNER,
        Set.of(ActivityEventType.PULL_REQUEST_MERGED),
        StandardCountEvaluator.class
    ),

    INTEGRATION_REGULAR(
        "integration_regular",
        AchievementCategory.PULL_REQUESTS,
        AchievementRarity.UNCOMMON,
        new LinearAchievementProgress( 10),
        PR_APPRENTICE,
        Set.of(ActivityEventType.PULL_REQUEST_MERGED),
        StandardCountEvaluator.class
    ),

    PR_SPECIALIST(
        "pr_specialist",
        AchievementCategory.PULL_REQUESTS,
        AchievementRarity.RARE,
        new LinearAchievementProgress( 25),
        INTEGRATION_REGULAR,
        Set.of(ActivityEventType.PULL_REQUEST_MERGED),
        StandardCountEvaluator.class
    ),

    INTEGRATION_EXPERT(
        "integration_expert",
        AchievementCategory.PULL_REQUESTS,
        AchievementRarity.EPIC,
        new LinearAchievementProgress( 50),
        PR_SPECIALIST,
        Set.of(ActivityEventType.PULL_REQUEST_MERGED),
        StandardCountEvaluator.class
    ),

    MASTER_INTEGRATOR(
        "master_integrator",
        AchievementCategory.PULL_REQUESTS,
        AchievementRarity.LEGENDARY,
        new LinearAchievementProgress( 100),
        INTEGRATION_EXPERT,
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
        Set.of(ActivityEventType.REVIEW_APPROVED),
        StandardCountEvaluator.class
    );

    @NonNull
    @Getter(onMethod_ = @JsonValue)
    private final String id;

    /* TODO: Moving the Achievements display characteristics to the frontend
        as it is part of the client and potentially useful for localization */


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
        Set<ActivityEventType> triggerEvents,
        Class<? extends AchievementEvaluator> evaluatorClass
    ) {
        this.id = id;
        this.category = category;
        this.rarity = rarity;
        this.requirements = requirements;
        this.parent = parent;
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
        if (id == null) {
            throw new IllegalArgumentException("Achievement ID cannot be null");
        }
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
