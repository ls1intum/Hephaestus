package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

/**
 * Enum representing GitLab webhook event types.
 * Each value corresponds to the {@code object_kind} field in the GitLab webhook payload.
 * <p>
 * GitLab determines the event type from the payload body ({@code object_kind}),
 * unlike GitHub which uses the {@code X-GitHub-Event} HTTP header.
 * <p>
 * This is an intentional subset of all GitLab webhook event types, covering only those
 * needed by current or planned handlers. Unknown event types are gracefully acknowledged
 * by {@link de.tum.in.www1.hephaestus.gitprovider.sync.NatsConsumerService} without
 * retrying. Additional types (e.g., {@code wiki_page}, {@code deployment}, {@code build},
 * {@code release}) can be added as handlers are implemented.
 *
 * @see <a href="https://docs.gitlab.com/ee/user/project/integrations/webhook_events.html">
 *      GitLab Webhook Events Reference</a>
 */
public enum GitLabEventType {
    // Core resource events
    MERGE_REQUEST("merge_request"),
    ISSUE("issue"),
    NOTE("note"),

    // Git events
    PUSH("push"),
    TAG_PUSH("tag_push"),

    // CI/CD events
    PIPELINE("pipeline");

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
