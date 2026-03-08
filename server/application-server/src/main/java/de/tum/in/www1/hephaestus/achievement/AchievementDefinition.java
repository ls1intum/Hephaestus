package de.tum.in.www1.hephaestus.achievement;

import com.fasterxml.jackson.annotation.JsonValue;
import de.tum.in.www1.hephaestus.achievement.evaluator.AchievementEvaluator;
import de.tum.in.www1.hephaestus.achievement.evaluator.ReviewCountEvaluator;
import de.tum.in.www1.hephaestus.achievement.evaluator.StandardCountEvaluator;
import de.tum.in.www1.hephaestus.achievement.progress.AchievementProgress;
import de.tum.in.www1.hephaestus.achievement.progress.BinaryAchievementProgress;
import de.tum.in.www1.hephaestus.achievement.progress.LinearAchievementProgress;
import de.tum.in.www1.hephaestus.activity.ActivityEventType;
import lombok.Getter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

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
    // Commit Achievements
    // ========================================================================

    /* !!! currently not available since Commit Events are not processed !!!

    COMMIT_COMMON_1(
        "commit.common.1",
        AchievementCategory.COMMITS,
        AchievementRarity.COMMON,
        new LinearAchievementProgress(1),
        null,
        false,
        Set.of(ActivityEventType.COMMIT_PUSHED),
        StandardCountEvaluator.class
    ),

    COMMIT_COMMON_2(
        "commit.common.2",
        AchievementCategory.COMMITS,
        AchievementRarity.COMMON,
        new LinearAchievementProgress(10),
        COMMIT_COMMON_1,
        false,
        Set.of(ActivityEventType.COMMIT_PUSHED),
        StandardCountEvaluator.class
    ),

    COMMIT_UNCOMMON_1(
        "commit.uncommon.1",
        AchievementCategory.COMMITS,
        AchievementRarity.UNCOMMON,
        new LinearAchievementProgress(50),
        COMMIT_COMMON_2,
        false,
        Set.of(ActivityEventType.COMMIT_PUSHED),
        StandardCountEvaluator.class
    ),

    COMMIT_UNCOMMON_2(
        "commit.uncommon.2",
        AchievementCategory.COMMITS,
        AchievementRarity.UNCOMMON,
        new LinearAchievementProgress(100),
        COMMIT_UNCOMMON_1,
        false,
        Set.of(ActivityEventType.COMMIT_PUSHED),
        StandardCountEvaluator.class
    ),

    COMMIT_RARE(
        "commit.rare",
        AchievementCategory.COMMITS,
        AchievementRarity.RARE,
        new LinearAchievementProgress(250),
        COMMIT_UNCOMMON_2,
        false,
        Set.of(ActivityEventType.COMMIT_PUSHED),
        StandardCountEvaluator.class
    ),

    COMMIT_EPIC(
        "commit.epic",
        AchievementCategory.COMMITS,
        AchievementRarity.EPIC,
        new LinearAchievementProgress(500),
        COMMIT_RARE,
        false,
        Set.of(ActivityEventType.COMMIT_PUSHED),
        StandardCountEvaluator.class
    ),

    COMMIT_LEGENDARY(
        "commit.legendary",
        AchievementCategory.COMMITS,
        AchievementRarity.LEGENDARY,
        new LinearAchievementProgress(1000),
        COMMIT_EPIC,
        false,
        Set.of(ActivityEventType.COMMIT_PUSHED),
        StandardCountEvaluator.class
    ),

    COMMIT_MYTHIC(
        "commit.mythic",
        AchievementCategory.COMMITS,
        AchievementRarity.MYTHIC,
        new LinearAchievementProgress(2000),
        COMMIT_LEGENDARY,
        false,
        Set.of(ActivityEventType.COMMIT_PUSHED),
        StandardCountEvaluator.class
    ),

    COMMIT_SPECIAL_ITSY_BITSY(
        "commit.special.itsy_bitsy",
        AchievementCategory.COMMITS,
        AchievementRarity.UNCOMMON,
        new BinaryAchievementProgress(false),
        null,
        false,
        Set.of(ActivityEventType.COMMIT_PUSHED),
        AchievementEvaluator.class // TODO: adjust that later based on single line change
    ),

    COMMIT_SPECIAL_ATOMIC_CHANGES(
        "commit.special.atomic_changes",
        AchievementCategory.COMMITS,
        AchievementRarity.RARE,
        new BinaryAchievementProgress(false),
        null,
        false,
        Set.of(ActivityEventType.COMMIT_PUSHED),
        AchievementEvaluator.class // TODO: adjust that later (10 commits in row, max 3 lines in 2 files)
    ),

    COMMIT_SPECIAL_BRUTE_FORCE(
        "commit.special.brute_force",
        AchievementCategory.COMMITS,
        AchievementRarity.UNCOMMON,
        new BinaryAchievementProgress(false),
        null,
        false,
        Set.of(ActivityEventType.COMMIT_PUSHED),
        AchievementEvaluator.class // TODO: adjust that later (5 commits in 5 mins)
    ),

    COMMIT_SPECIAL_CROSS_BOUNDARY(
        "commit.special.cross_boundary",
        AchievementCategory.COMMITS,
        AchievementRarity.RARE,
        new BinaryAchievementProgress(false),
        null,
        false,
        Set.of(ActivityEventType.COMMIT_PUSHED),
        AchievementEvaluator.class // TODO: adjust that later (2 different programming languages)
    ),

     TODO: Remove the block comment markers to re-enable when the DomainEvent for commits is implemented! */

    // ========================================================================
    // Review Achievements (triggered by any review activity)
    // ========================================================================

    REVIEW_COMMON_1(
        "review.common.1",
        AchievementCategory.COMMUNICATION,
        AchievementRarity.COMMON,
        new LinearAchievementProgress(1),
        null,
        false,
        Set.of(
            ActivityEventType.REVIEW_APPROVED,
            ActivityEventType.REVIEW_CHANGES_REQUESTED,
            ActivityEventType.REVIEW_COMMENTED
        ),
        ReviewCountEvaluator.class
    ),

    REVIEW_COMMON_2(
        "review.common.2",
        AchievementCategory.COMMUNICATION,
        AchievementRarity.COMMON,
        new LinearAchievementProgress(10),
        REVIEW_COMMON_1,
        false,
        Set.of(
            ActivityEventType.REVIEW_APPROVED,
            ActivityEventType.REVIEW_CHANGES_REQUESTED,
            ActivityEventType.REVIEW_COMMENTED
        ),
        ReviewCountEvaluator.class
    ),

    REVIEW_UNCOMMON_1(
        "review.uncommon.1",
        AchievementCategory.COMMUNICATION,
        AchievementRarity.UNCOMMON,
        new LinearAchievementProgress(25),
        REVIEW_COMMON_2,
        false,
        Set.of(
            ActivityEventType.REVIEW_APPROVED,
            ActivityEventType.REVIEW_CHANGES_REQUESTED,
            ActivityEventType.REVIEW_COMMENTED
        ),
        ReviewCountEvaluator.class
    ),

    REVIEW_UNCOMMON_2(
        "review.uncommon.2",
        AchievementCategory.COMMUNICATION,
        AchievementRarity.UNCOMMON,
        new LinearAchievementProgress(50),
        REVIEW_UNCOMMON_1,
        false,
        Set.of(
            ActivityEventType.REVIEW_APPROVED,
            ActivityEventType.REVIEW_CHANGES_REQUESTED,
            ActivityEventType.REVIEW_COMMENTED
        ),
        ReviewCountEvaluator.class
    ),

    REVIEW_RARE(
        "review.rare",
        AchievementCategory.COMMUNICATION,
        AchievementRarity.RARE,
        new LinearAchievementProgress(100),
        REVIEW_UNCOMMON_2,
        false,
        Set.of(
            ActivityEventType.REVIEW_APPROVED,
            ActivityEventType.REVIEW_CHANGES_REQUESTED,
            ActivityEventType.REVIEW_COMMENTED
        ),
        ReviewCountEvaluator.class
    ),

    REVIEW_EPIC(
        "review.epic",
        AchievementCategory.COMMUNICATION,
        AchievementRarity.EPIC,
        new LinearAchievementProgress(200),
        REVIEW_RARE,
        false,
        Set.of(
            ActivityEventType.REVIEW_APPROVED,
            ActivityEventType.REVIEW_CHANGES_REQUESTED,
            ActivityEventType.REVIEW_COMMENTED
        ),
        ReviewCountEvaluator.class
    ),

    REVIEW_LEGENDARY(
        "review.legendary",
        AchievementCategory.COMMUNICATION,
        AchievementRarity.LEGENDARY,
        new LinearAchievementProgress(500),
        REVIEW_EPIC,
        false,
        Set.of(
            ActivityEventType.REVIEW_APPROVED,
            ActivityEventType.REVIEW_CHANGES_REQUESTED,
            ActivityEventType.REVIEW_COMMENTED
        ),
        ReviewCountEvaluator.class
    ),

    REVIEW_MYTHIC(
        "review.mythic",
        AchievementCategory.COMMUNICATION,
        AchievementRarity.MYTHIC,
        new LinearAchievementProgress(1000),
        REVIEW_LEGENDARY,
        false,
        Set.of(
            ActivityEventType.REVIEW_APPROVED,
            ActivityEventType.REVIEW_CHANGES_REQUESTED,
            ActivityEventType.REVIEW_COMMENTED
        ),
        ReviewCountEvaluator.class
    ),

    // ========================================================================
    // Comment Achievements (triggered by inline code review comments)
    // !!! Currently no achievements in this category (is part of communication)
    // ========================================================================

    // ========================================================================
    // ISSUES (triggered by issue open and closed events)
    // ========================================================================

    ISSUE_OPEN_COMMON_1(
        "issue.open.common.1",
        AchievementCategory.ISSUES,
        AchievementRarity.COMMON,
        new LinearAchievementProgress(1),
        null,
        false,
        Set.of(ActivityEventType.ISSUE_CREATED),
        StandardCountEvaluator.class
    ),

    ISSUE_OPEN_COMMON_2(
        "issue.open.common.2",
        AchievementCategory.ISSUES,
        AchievementRarity.COMMON,
        new LinearAchievementProgress(5),
        ISSUE_OPEN_COMMON_1,
        false,
        Set.of(ActivityEventType.ISSUE_CREATED),
        StandardCountEvaluator.class
    ),

    ISSUE_OPEN_UNCOMMON(
        "issue.open.uncommon",
        AchievementCategory.ISSUES,
        AchievementRarity.UNCOMMON,
        new LinearAchievementProgress(10),
        ISSUE_OPEN_COMMON_2,
        false,
        Set.of(ActivityEventType.ISSUE_CREATED),
        StandardCountEvaluator.class
    ),

    ISSUE_OPEN_RARE(
        "issue.open.rare",
        AchievementCategory.ISSUES,
        AchievementRarity.RARE,
        new LinearAchievementProgress(15),
        ISSUE_OPEN_UNCOMMON,
        false,
        Set.of(ActivityEventType.ISSUE_CREATED),
        StandardCountEvaluator.class
    ),

    ISSUE_OPEN_EPIC(
        "issue.open.epic",
        AchievementCategory.ISSUES,
        AchievementRarity.EPIC,
        new LinearAchievementProgress(30),
        ISSUE_OPEN_RARE,
        false,
        Set.of(ActivityEventType.ISSUE_CREATED),
        StandardCountEvaluator.class
    ),

    ISSUE_OPEN_LEGENDARY(
        "issue.open.legendary",
        AchievementCategory.ISSUES,
        AchievementRarity.LEGENDARY,
        new LinearAchievementProgress(50),
        ISSUE_OPEN_EPIC,
        false,
        Set.of(ActivityEventType.ISSUE_CREATED),
        StandardCountEvaluator.class
    ),

    ISSUE_CLOSE_COMMON_1(
        "issue.close.common.1",
        AchievementCategory.ISSUES,
        AchievementRarity.COMMON,
        new LinearAchievementProgress(1),
        null,
        false,
        Set.of(ActivityEventType.ISSUE_CLOSED),
        StandardCountEvaluator.class
    ),

    ISSUE_CLOSE_COMMON_2(
        "issue.close.common.2",
        AchievementCategory.ISSUES,
        AchievementRarity.COMMON,
        new LinearAchievementProgress(5),
        ISSUE_CLOSE_COMMON_1,
        false,
        Set.of(ActivityEventType.ISSUE_CLOSED),
        StandardCountEvaluator.class
    ),

    ISSUE_CLOSE_UNCOMMON(
        "issue.close.uncommon",
        AchievementCategory.ISSUES,
        AchievementRarity.UNCOMMON,
        new LinearAchievementProgress(10),
        ISSUE_CLOSE_COMMON_2,
        false,
        Set.of(ActivityEventType.ISSUE_CLOSED),
        StandardCountEvaluator.class
    ),

    ISSUE_CLOSE_RARE(
        "issue.close.rare",
        AchievementCategory.ISSUES,
        AchievementRarity.RARE,
        new LinearAchievementProgress(15),
        ISSUE_CLOSE_UNCOMMON,
        false,
        Set.of(ActivityEventType.ISSUE_CLOSED),
        StandardCountEvaluator.class
    ),

    ISSUE_CLOSE_EPIC(
        "issue.close.epic",
        AchievementCategory.ISSUES,
        AchievementRarity.EPIC,
        new LinearAchievementProgress(30),
        ISSUE_CLOSE_RARE,
        false,
        Set.of(ActivityEventType.ISSUE_CLOSED),
        StandardCountEvaluator.class
    ),

    ISSUE_CLOSE_LEGENDARY(
        "issue.close.legendary",
        AchievementCategory.ISSUES,
        AchievementRarity.LEGENDARY,
        new LinearAchievementProgress(50),
        ISSUE_CLOSE_EPIC,
        false,
        Set.of(ActivityEventType.ISSUE_CLOSED),
        StandardCountEvaluator.class
    ),

    /* TODO: Those special achievements need extra evaluators

    ISSUE_SPECIAL_HIVE_MIND(
        "issue.special.hive_mind",
        AchievementCategory.ISSUES,
        AchievementRarity.RARE,
        new BinaryAchievementProgress(),
        ISSUE_CLOSE_COMMON_1,
        false,
        Set.of(ActivityEventType.ISSUE_CLOSED),
        AchievementEvaluator.class
    ),

    ISSUE_SPECIAL_ORACLE(
        "issue.special.oracle",
        AchievementCategory.ISSUES,
        AchievementRarity.EPIC,
        new BinaryAchievementProgress(),
        ISSUE_CLOSE_COMMON_1,
        true,
        Set.of(ActivityEventType.ISSUE_CLOSED),
        AchievementEvaluator.class
    ),
    ISSUE_SPECIAL_NECROMANCER(
        "issue.special.necromancer",
        AchievementCategory.ISSUES,
        AchievementRarity.EPIC,
        new BinaryAchievementProgress(),
        ISSUE_CLOSE_COMMON_1,
        true,
        Set.of(ActivityEventType.ISSUE_CLOSED),
        AchievementEvaluator.class
    ),

     */

    // ========================================================================
    // MILESTONES (standalone, sectional and intersectional (tbd: multi parent relationship)
    // ========================================================================

    MILESTONE_FIRST_ACTION(
        "milestone.first_action",
        AchievementCategory.MILESTONES,
        AchievementRarity.COMMON,
        new LinearAchievementProgress(1),
        null,
        false,
        Set.of(ActivityEventType.PULL_REQUEST_OPENED,
            ActivityEventType.PULL_REQUEST_CLOSED,
            ActivityEventType.ISSUE_CREATED,
            ActivityEventType.ISSUE_CLOSED,
            ActivityEventType.REVIEW_APPROVED,
            ActivityEventType.REVIEW_CHANGES_REQUESTED,
            ActivityEventType.REVIEW_COMMENTED),
        StandardCountEvaluator.class
    ),

    MILESTONE_POLYGLOT(
        "milestone.polyglot",
        AchievementCategory.MILESTONES,
        AchievementRarity.UNCOMMON,
        new BinaryAchievementProgress(),
        MILESTONE_FIRST_ACTION,
        false,
        Set.of(),
        AchievementEvaluator.class
    ),

    MILESTONE_NIGHT_OWL(
        "milestone.night_owl",
        AchievementCategory.MILESTONES,
        AchievementRarity.EPIC,
        new BinaryAchievementProgress(),
        MILESTONE_FIRST_ACTION,
        true,
        Set.of(),
        AchievementEvaluator.class
    ),

    MILESTONE_LONG_TIME_RETURN(
        "milestone.long_time_return",
        AchievementCategory.MILESTONES,
        AchievementRarity.EPIC,
        new BinaryAchievementProgress(),
        null, // TODO: should be self-referenced for standalone achievements
        true,
        Set.of(),
        AchievementEvaluator.class
    ),

    MILESTONE_ALL_RARE(
        "milestone.all_rare",
        AchievementCategory.MILESTONES,
        AchievementRarity.EPIC,
        new BinaryAchievementProgress(),
        null, // TODO: should be self-referenced for standalone achievements
        true,
        Set.of(),
        AchievementEvaluator.class
    ),

    MILESTONE_ALL_EPIC(
        "milestone.all_epic",
        AchievementCategory.MILESTONES,
        AchievementRarity.LEGENDARY,
        new BinaryAchievementProgress(),
        MILESTONE_ALL_RARE,
        true,
        Set.of(),
        AchievementEvaluator.class
    ),

    MILESTONE_ALL_LEGENDARY(
        "milestone.all_legendary",
        AchievementCategory.MILESTONES,
        AchievementRarity.MYTHIC,
        new BinaryAchievementProgress(),
        MILESTONE_ALL_EPIC,
        true,
        Set.of(),
        AchievementEvaluator.class
    ),

    ;
    // =========================================================================

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
