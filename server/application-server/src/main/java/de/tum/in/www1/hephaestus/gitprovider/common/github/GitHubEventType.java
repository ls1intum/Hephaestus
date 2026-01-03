package de.tum.in.www1.hephaestus.gitprovider.common.github;

/**
 * Enum representing GitHub webhook event types.
 * Each value corresponds to the X-GitHub-Event header value.
 */
public enum GitHubEventType {
    // Repository events
    ISSUES("issues"),
    ISSUE_COMMENT("issue_comment"),
    PULL_REQUEST("pull_request"),
    PULL_REQUEST_REVIEW("pull_request_review"),
    PULL_REQUEST_REVIEW_COMMENT("pull_request_review_comment"),
    PULL_REQUEST_REVIEW_THREAD("pull_request_review_thread"),
    LABEL("label"),
    MILESTONE("milestone"),
    MEMBER("member"),

    // Issue hierarchy events
    SUB_ISSUES("sub_issues"),
    ISSUE_DEPENDENCIES("issue_dependencies"),

    // Organization events
    ORGANIZATION("organization"),
    TEAM("team"),
    MEMBERSHIP("membership"),

    // Installation events
    INSTALLATION("installation"),
    INSTALLATION_REPOSITORIES("installation_repositories"),
    INSTALLATION_TARGET("installation_target");

    private final String value;

    GitHubEventType(String value) {
        this.value = value;
    }

    /**
     * Returns the event key string used in NATS subjects.
     */
    public String getValue() {
        return value;
    }

    /**
     * Parses a string event type to the enum value.
     *
     * @param eventType the event type string
     * @return the matching enum value, or null if not found
     */
    public static GitHubEventType fromString(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return null;
        }
        for (GitHubEventType type : values()) {
            if (type.value.equalsIgnoreCase(eventType)) {
                return type;
            }
        }
        return null;
    }
}
