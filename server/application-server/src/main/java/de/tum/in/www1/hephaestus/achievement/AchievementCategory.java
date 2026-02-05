package de.tum.in.www1.hephaestus.achievement;

import lombok.Getter;

/**
 * Categories of achievements for UI grouping and filtering.
 *
 * <p>Each category represents a distinct area of contribution that users
 * can earn achievements in.
 */
@Getter
public enum AchievementCategory {
    /**
     * Achievements related to creating and merging pull requests.
     */
    PULL_REQUESTS("Pull Requests"),

    /**
     * Achievements related to committing code.
     * (Placeholder for future implementation)
     */
    COMMITS("Commits"),

    /**
     * Achievements related to code review activity.
     * (Placeholder for future implementation)
     */
    REVIEWS("Reviews"),

    /**
     * Achievements related to commenting and discussions.
     * (Placeholder for future implementation)
     */
    COMMENTS("Comments"),

    /**
     * Achievements related to issue management.
     * (Placeholder for future implementation)
     */
    ISSUES("Issues"),

    /**
     * Cross-category milestone achievements.
     * Combines progress across multiple categories.
     */
    CROSS_CATEGORY("Milestones");

    /**
     * -- GETTER --
     *  Human-readable name for UI display.
     */
    private final String displayName;

    AchievementCategory(String displayName) {
        this.displayName = displayName;
    }

}
