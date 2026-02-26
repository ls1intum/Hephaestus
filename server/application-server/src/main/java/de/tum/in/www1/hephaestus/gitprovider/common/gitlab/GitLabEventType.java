package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

/**
 * Enum representing GitLab webhook event types.
 * Each value corresponds to the {@code object_kind} field in the GitLab webhook payload.
 * <p>
 * GitLab determines the event type from the payload body ({@code object_kind}),
 * unlike GitHub which uses the {@code X-GitHub-Event} HTTP header.
 *
 * @see <a href="https://docs.gitlab.com/ee/user/project/integrations/webhook_events.html">
 *      GitLab Webhook Events Reference</a>
 */
public enum GitLabEventType {
    MERGE_REQUEST("merge_request"),
    ISSUE("issue"),
    NOTE("note"),
    PUSH("push"),
    PIPELINE("pipeline"),
    TAG_PUSH("tag_push");

    private final String value;

    GitLabEventType(String value) {
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
     * @param eventType the event type string (from {@code object_kind} field)
     * @return the matching enum value, or null if not found
     */
    public static GitLabEventType fromString(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return null;
        }
        for (GitLabEventType type : values()) {
            if (type.value.equalsIgnoreCase(eventType)) {
                return type;
            }
        }
        return null;
    }
}
