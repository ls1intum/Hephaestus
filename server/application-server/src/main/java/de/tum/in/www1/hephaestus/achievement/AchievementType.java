package de.tum.in.www1.hephaestus.achievement;

import de.tum.in.www1.hephaestus.activity.ActivityEventType;
import lombok.Getter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;


import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

// TODO: Consider refactoring this using a "Registry Pattern" and json serialization for the data.

/**
 * The source of truth for all achievement definitions.
 *
 * <p>Each achievement is defined by:
 * <ul>
 *   <li>{@code id} - Unique identifier (stored in database)</li>
 *   <li>{@code name} - Human-readable name for UI</li>
 *   <li>{@code description} - Explanation of how to earn it</li>
 *   <li>{@code icon} - Icon identifier (e.g., "git-merge", "star")</li>
 *   <li>{@code category} - Grouping category</li>
 *   <li>{@code rarity} - Visual tier for UI ring/badge styling</li>
 *   <li>{@code requiredCount} - Number of events needed to unlock</li>
 *   <li>{@code parent} - Previous achievement in the progression chain (nullable)</li>
 *   <li>{@code triggerEvents} - Activity event types that contribute to this achievement</li>
 * </ul>
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
 */
@Getter
public enum AchievementType {
    // ========================================================================
    // Pull Request Achievements (Level 1-7)
    // ========================================================================

    FIRST_PULL(
        "first_pull",
        "First Merge",
        "Merge your first pull request",
        "git-merge",
        AchievementCategory.PULL_REQUESTS,
        AchievementRarity.COMMON,
        1,
        null,
        Set.of(ActivityEventType.PULL_REQUEST_MERGED)
    ),

    PR_BEGINNER(
        "pr_beginner",
        "Beginner Integrator",
        "Merge 3 pull requests",
        "git-merge",
        AchievementCategory.PULL_REQUESTS,
        AchievementRarity.COMMON,
        3,
        FIRST_PULL,
        Set.of(ActivityEventType.PULL_REQUEST_MERGED)
    ),

    PR_APPRENTICE(
        "pr_apprentice",
        "Apprentice Integrator",
        "Merge 5 pull requests",
        "git-merge",
        AchievementCategory.PULL_REQUESTS,
        AchievementRarity.UNCOMMON,
        5,
        PR_BEGINNER,
        Set.of(ActivityEventType.PULL_REQUEST_MERGED)
    ),

    INTEGRATION_REGULAR(
        "integration_regular",
        "Integration Regular",
        "Merge 10 pull requests",
        "git-merge",
        AchievementCategory.PULL_REQUESTS,
        AchievementRarity.UNCOMMON,
        10,
        PR_APPRENTICE,
        Set.of(ActivityEventType.PULL_REQUEST_MERGED)
    ),

    PR_SPECIALIST(
        "pr_specialist",
        "Integration Specialist",
        "Merge 25 pull requests",
        "git-merge",
        AchievementCategory.PULL_REQUESTS,
        AchievementRarity.RARE,
        25,
        INTEGRATION_REGULAR,
        Set.of(ActivityEventType.PULL_REQUEST_MERGED)
    ),

    INTEGRATION_EXPERT(
        "integration_expert",
        "Integration Expert",
        "Merge 50 pull requests",
        "git-merge",
        AchievementCategory.PULL_REQUESTS,
        AchievementRarity.EPIC,
        50,
        PR_SPECIALIST,
        Set.of(ActivityEventType.PULL_REQUEST_MERGED)
    ),

    MASTER_INTEGRATOR(
        "master_integrator",
        "Master Integrator",
        "Merge 100 pull requests",
        "git-merge",
        AchievementCategory.PULL_REQUESTS,
        AchievementRarity.LEGENDARY,
        100,
        INTEGRATION_EXPERT,
        Set.of(ActivityEventType.PULL_REQUEST_MERGED)
    ),

    // ========================================================================
    // Review Achievements (triggered by any review activity)
    // ========================================================================

    FIRST_REVIEW(
        "first_review",
        "First Review",
        "Submit your first code review",
        "Eye",
        AchievementCategory.COMMUNICATION,
        AchievementRarity.COMMON,
        1,
        null,
        Set.of(ActivityEventType.REVIEW_APPROVED, ActivityEventType.REVIEW_CHANGES_REQUESTED, ActivityEventType.REVIEW_COMMENTED)
    ),

    REVIEW_ROOKIE(
        "review_rookie",
        "Review Rookie",
        "Submit 10 code reviews",
        "Eye",
        AchievementCategory.COMMUNICATION,
        AchievementRarity.COMMON,
        10,
        FIRST_REVIEW,
        Set.of(ActivityEventType.REVIEW_APPROVED, ActivityEventType.REVIEW_CHANGES_REQUESTED, ActivityEventType.REVIEW_COMMENTED)
    ),

    REVIEW_MASTER(
        "review_master",
        "Review Master",
        "Submit 100 code reviews",
        "Eye",
        AchievementCategory.COMMUNICATION,
        AchievementRarity.EPIC,
        100,
        REVIEW_ROOKIE,
        Set.of(ActivityEventType.REVIEW_APPROVED, ActivityEventType.REVIEW_CHANGES_REQUESTED, ActivityEventType.REVIEW_COMMENTED)
    ),

    // ========================================================================
    // Comment Achievements (triggered by inline code review comments)
    // ========================================================================

    CODE_COMMENTER(
        "code_commenter",
        "Code Commenter",
        "Post 100 code comments",
        "MessageSquare",
        AchievementCategory.COMMUNICATION,
        AchievementRarity.EPIC,
        100,
        null,
        Set.of(ActivityEventType.REVIEW_COMMENT_CREATED)
    ),

    // ========================================================================
    // Approval Achievements (triggered by review approvals)
    // ========================================================================

    HELPFUL_REVIEWER(
        "helpful_reviewer",
        "Helpful Reviewer",
        "Approve 50 pull requests",
        "HandHelping",
        AchievementCategory.COMMUNICATION,
        AchievementRarity.LEGENDARY,
        50,
        null,
        Set.of(ActivityEventType.REVIEW_APPROVED)
    );

    @NonNull
    private final String id;
    private final String name;
    private final String description;
    private final String icon;
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
     */
    private final long requiredCount;
    @Nullable
    private final AchievementType parent;
    /**
     * -- GETTER --
     * Activity event types that contribute to unlocking this achievement.
     */
    private final Set<ActivityEventType> triggerEvents;

    AchievementType(
        String id,
        String name,
        String description,
        String icon,
        AchievementCategory category,
        AchievementRarity rarity,
        long requiredCount,
        @Nullable AchievementType parent,
        Set<ActivityEventType> triggerEvents
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.category = category;
        this.rarity = rarity;
        this.requiredCount = requiredCount;
        this.parent = parent;
        this.triggerEvents = triggerEvents;
    }

    /**
     * Parent achievement that must be unlocked before this one.
     * Returns null if this is the first achievement in the chain.
     */
    @Nullable
    public AchievementType getParent() {
        return parent;
    }

    /**
     * Find an achievement type by its ID.
     *
     * @param id the achievement ID
     * @return the matching achievement type
     * @throws IllegalArgumentException if ID is unknown
     */
    public static AchievementType fromId(String id) {
        if (id == null) {
            throw new IllegalArgumentException("Achievement ID cannot be null");
        }
        for (AchievementType type : values()) {
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
    public static List<AchievementType> getByCategory(AchievementCategory category) {
        return Arrays.stream(values())
            .filter(a -> a.category == category)
            .sorted(Comparator.comparing(AchievementType::getRarity, AchievementRarity.RARITY_COMPARATOR))
            .toList();
    }

    /**
     * Get all achievements triggered by a specific event type.
     *
     * @param eventType the activity event type
     * @return list of achievements that this event contributes to
     */
    public static List<AchievementType> getByTriggerEvent(ActivityEventType eventType) {
        return Arrays.stream(values())
            .filter(a -> a.triggerEvents.contains(eventType))
            .toList();
    }
}
