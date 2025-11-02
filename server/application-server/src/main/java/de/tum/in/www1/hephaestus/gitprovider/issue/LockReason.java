package de.tum.in.www1.hephaestus.gitprovider.issue;

/**
 * Represents the reason an issue or pull request was locked.
 * Matches GitHub's active_lock_reason field.
 */
public enum LockReason {
    /** The issue is off-topic. */
    OFF_TOPIC,
    /** The conversation is too heated. */
    TOO_HEATED,
    /** The issue has been resolved. */
    RESOLVED,
    /** The issue is spam. */
    SPAM,
}
