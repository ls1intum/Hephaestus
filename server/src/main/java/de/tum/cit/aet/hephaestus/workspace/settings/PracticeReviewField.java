package de.tum.cit.aet.hephaestus.workspace.settings;

/** A resettable practice-review policy field — naming it in a PATCH clears the override back to inherit. */
public enum PracticeReviewField {
    RUN_FOR_ALL_USERS,
    SKIP_DRAFTS,
    DELIVER_TO_MERGED,
    COOLDOWN_MINUTES,
    /** Clears the branch/repository review scope back to unrestricted. */
    REVIEW_SCOPE,
    /**
     * Clears the workspace's default autonomy tier, so practices and areas that hold no opinion of their own
     * fall back to the tier vocabulary's default rather than to a workspace decision.
     */
    DEFAULT_REVIEW_TIER,
}
