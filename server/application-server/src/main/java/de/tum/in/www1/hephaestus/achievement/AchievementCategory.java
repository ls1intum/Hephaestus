package de.tum.in.www1.hephaestus.achievement;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("pull_requests") PULL_REQUESTS("Pull Requests"),

    /**
     * Achievements related to committing code.
     * (Placeholder for future implementation)
     */
    @JsonProperty("commits") COMMITS("Commits"),

    /**
     * Achievements related to reviews, commenting and discussions.
     * (Placeholder for future implementation)
     */
    @JsonProperty("communication") COMMUNICATION("Communication"),

    /**
     * Achievements related to issue management.
     * (Placeholder for future implementation)
     */
    @JsonProperty("issues") ISSUES("Issues"),

    /**
     * Cross-category milestone achievements.
     * Combines progress across multiple categories.
     */
    @JsonProperty("milestones") MILESTONES("Milestones");

    /**
     * -- GETTER --
     *  Human-readable name for UI display.
     */
    private final String displayName;

    AchievementCategory(String displayName) {
        this.displayName = displayName;
    }

}
