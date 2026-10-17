package de.tum.cit.aet.hephaestus.workspace.settings;

/**
 * A resettable practice-review policy field — naming it in a PATCH clears the override back to
 * inherit.
 */
public enum PracticeReviewField {
    DELIVER_TO_MERGED,
    COOLDOWN_MINUTES,
    /** Clears the branch/repository review scope back to unrestricted. */
    REVIEW_SCOPE,
    /**
     * Clears the workspace's default feedback mode, so practices and groups that hold no opinion of
     * their own fall back to the feedback mode vocabulary's default rather than to a workspace
     * decision.
     */
    DEFAULT_FEEDBACK_MODE,
}
