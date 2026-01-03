package de.tum.in.www1.hephaestus.activity;

/**
 * Type of entity that an activity event targets.
 *
 * <p>Used to identify what kind of entity the {@link ActivityEvent#targetId} refers to.
 * This enables type-safe lookups when reconstructing activity feeds.
 */
public enum ActivityTargetType {
    /**
     * Target is a pull request.
     */
    PULL_REQUEST("pull_request"),

    /**
     * Target is a pull request review.
     */
    REVIEW("review"),

    /**
     * Target is an issue comment (including PR comments that aren't review comments).
     */
    ISSUE_COMMENT("issue_comment"),

    /**
     * Target is a review comment (inline code comment on a PR).
     */
    REVIEW_COMMENT("review_comment"),

    /**
     * Target is an issue (not a pull request).
     */
    ISSUE("issue");

    private final String value;

    ActivityTargetType(String value) {
        this.value = value;
    }

    /**
     * Get the string value stored in the database.
     */
    public String getValue() {
        return value;
    }

    /**
     * Convert from database string value to enum.
     *
     * @param value the database value
     * @return the matching enum
     * @throws IllegalArgumentException if value is null or unknown
     */
    public static ActivityTargetType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Target type value cannot be null");
        }
        for (ActivityTargetType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown target type: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
