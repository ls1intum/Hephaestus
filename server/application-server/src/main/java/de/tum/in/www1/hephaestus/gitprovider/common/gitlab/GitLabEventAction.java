package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

/**
 * Enum representing GitLab webhook event actions.
 * <p>
 * Unlike GitHub's sealed interface hierarchy (one enum per event type), GitLab uses
 * a flat action vocabulary shared across event types. The action is found in the
 * {@code object_attributes.action} field of the webhook payload.
 * <p>
 * This is intentionally a flat enum rather than a per-event-type hierarchy because
 * GitLab reuses the same action names across different event types (e.g., "open"
 * applies to both merge requests and issues).
 */
public enum GitLabEventAction {
    OPEN("open"),
    CLOSE("close"),
    REOPEN("reopen"),
    MERGE("merge"),
    UPDATE("update"),
    APPROVED("approved"),
    UNAPPROVED("unapproved"),
    UNKNOWN("unknown");

    private final String value;

    GitLabEventAction(String value) {
        this.value = value;
    }

    /**
     * Returns the raw action string from the webhook payload.
     */
    public String getValue() {
        return value;
    }

    /**
     * Parses a string action to the enum value.
     *
     * @param action the action string from {@code object_attributes.action}
     * @return the matching enum value, or {@link #UNKNOWN} if not found
     */
    public static GitLabEventAction fromString(String action) {
        if (action == null || action.isBlank()) {
            return UNKNOWN;
        }
        for (GitLabEventAction a : values()) {
            if (a.value.equalsIgnoreCase(action)) {
                return a;
            }
        }
        return UNKNOWN;
    }
}
