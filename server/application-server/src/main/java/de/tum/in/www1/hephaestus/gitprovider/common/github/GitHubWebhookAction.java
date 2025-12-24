package de.tum.in.www1.hephaestus.gitprovider.common.github;

/**
 * Enumeration of GitHub webhook action types.
 * <p>
 * Provides compile-time type safety for webhook action handling,
 * replacing hardcoded string comparisons throughout the codebase.
 */
public enum GitHubWebhookAction {
    // Common lifecycle actions
    OPENED,
    CLOSED,
    REOPENED,
    EDITED,
    DELETED,
    CREATED,

    // Label actions
    LABELED,
    UNLABELED,

    // Assignment actions
    ASSIGNED,
    UNASSIGNED,

    // Milestone actions
    MILESTONED,
    DEMILESTONED,

    // Pull request specific
    READY_FOR_REVIEW,
    CONVERTED_TO_DRAFT,
    SYNCHRONIZE,
    REVIEW_REQUESTED,
    REVIEW_REQUEST_REMOVED,
    AUTO_MERGE_ENABLED,
    AUTO_MERGE_DISABLED,
    ENQUEUED,
    DEQUEUED,

    // Issue specific
    PINNED,
    UNPINNED,
    LOCKED,
    UNLOCKED,
    TRANSFERRED,
    TYPED,

    // Organization/team specific
    MEMBER_ADDED,
    MEMBER_REMOVED,
    RENAMED,

    // Installation specific
    ADDED,
    REMOVED,
    SUSPEND,
    UNSUSPEND,

    // Review thread specific
    RESOLVED,
    UNRESOLVED,

    // Review specific
    DISMISSED,
    SUBMITTED,

    // Sub-issues specific
    SUB_ISSUE_ADDED,
    SUB_ISSUE_REMOVED,
    PARENT_ISSUE_ADDED,
    PARENT_ISSUE_REMOVED,

    // Fallback for unknown actions
    UNKNOWN;

    /**
     * Parse a webhook action string to enum value.
     *
     * @param action the action string from webhook payload (may be null)
     * @return the corresponding enum value, or UNKNOWN if not recognized
     */
    public static GitHubWebhookAction fromString(String action) {
        if (action == null || action.isBlank()) {
            return UNKNOWN;
        }
        return switch (action.toLowerCase()) {
            case "opened" -> OPENED;
            case "closed" -> CLOSED;
            case "reopened" -> REOPENED;
            case "edited" -> EDITED;
            case "deleted" -> DELETED;
            case "created" -> CREATED;
            case "labeled" -> LABELED;
            case "unlabeled" -> UNLABELED;
            case "assigned" -> ASSIGNED;
            case "unassigned" -> UNASSIGNED;
            case "milestoned" -> MILESTONED;
            case "demilestoned" -> DEMILESTONED;
            case "ready_for_review" -> READY_FOR_REVIEW;
            case "converted_to_draft" -> CONVERTED_TO_DRAFT;
            case "synchronize" -> SYNCHRONIZE;
            case "review_requested" -> REVIEW_REQUESTED;
            case "review_request_removed" -> REVIEW_REQUEST_REMOVED;
            case "auto_merge_enabled" -> AUTO_MERGE_ENABLED;
            case "auto_merge_disabled" -> AUTO_MERGE_DISABLED;
            case "enqueued" -> ENQUEUED;
            case "dequeued" -> DEQUEUED;
            case "pinned" -> PINNED;
            case "unpinned" -> UNPINNED;
            case "locked" -> LOCKED;
            case "unlocked" -> UNLOCKED;
            case "transferred" -> TRANSFERRED;
            case "typed" -> TYPED;
            case "member_added" -> MEMBER_ADDED;
            case "member_removed" -> MEMBER_REMOVED;
            case "renamed" -> RENAMED;
            case "added" -> ADDED;
            case "removed" -> REMOVED;
            case "suspend" -> SUSPEND;
            case "unsuspend" -> UNSUSPEND;
            case "resolved" -> RESOLVED;
            case "unresolved" -> UNRESOLVED;
            case "dismissed" -> DISMISSED;
            case "submitted" -> SUBMITTED;
            case "sub_issue_added" -> SUB_ISSUE_ADDED;
            case "sub_issue_removed" -> SUB_ISSUE_REMOVED;
            case "parent_issue_added" -> PARENT_ISSUE_ADDED;
            case "parent_issue_removed" -> PARENT_ISSUE_REMOVED;
            default -> UNKNOWN;
        };
    }

    /**
     * Check if the given action string matches this enum value.
     *
     * @param action the action string to check
     * @return true if this enum matches the action
     */
    public boolean matches(String action) {
        return this == fromString(action);
    }
}
