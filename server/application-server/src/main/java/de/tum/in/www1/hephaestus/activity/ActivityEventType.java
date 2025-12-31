package de.tum.in.www1.hephaestus.activity;

/**
 * Activity event types - ONLY types that are actually emitted.
 *
 * <p>Each type here has a corresponding handler in ActivityEventListener
 * that listens to a domain event and persists to the ledger.
 *
 * <p>Maps to DX Core 4 dimensions:
 * <ul>
 *   <li><strong>Speed</strong>: PULL_REQUEST_OPENED → PULL_REQUEST_MERGED (lead time)</li>
 *   <li><strong>Effectiveness</strong>: REVIEW_* (review cycle time)</li>
 *   <li><strong>Quality</strong>: via payload attributes</li>
 * </ul>
 */
public enum ActivityEventType {
    // ========================================================================
    // Pull Request Lifecycle (Speed metrics)
    // ========================================================================
    /** Pull request created - starts the lead time clock */
    PULL_REQUEST_OPENED("pull_request.opened"),
    /** Pull request merged - ends the lead time clock */
    PULL_REQUEST_MERGED("pull_request.merged"),
    /** Pull request closed without merge */
    PULL_REQUEST_CLOSED("pull_request.closed"),

    // ========================================================================
    // Review Lifecycle (Effectiveness metrics)
    // ========================================================================
    /** Review approved - ends "time to approval" clock */
    REVIEW_APPROVED("review.approved"),
    /** Review requested changes */
    REVIEW_CHANGES_REQUESTED("review.changes_requested"),
    /** Review with comments only (no approval/rejection) */
    REVIEW_COMMENTED("review.commented"),

    // ========================================================================
    // Comments (Collaboration quality - for leaderboard scoring)
    // ========================================================================
    /** Comment created on issue or pull request (general discussion) */
    COMMENT_CREATED("comment.created"),
    /** Inline code review comment created (higher-value feedback) */
    REVIEW_COMMENT_CREATED("review_comment.created");

    private final String value;

    ActivityEventType(String value) {
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
    public static ActivityEventType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Event type value cannot be null");
        }
        for (ActivityEventType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown event type: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
