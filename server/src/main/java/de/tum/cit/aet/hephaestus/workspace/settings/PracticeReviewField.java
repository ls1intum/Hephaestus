package de.tum.cit.aet.hephaestus.workspace.settings;

/** A resettable practice-review policy field — naming it in a PATCH clears the override back to inherit. */
public enum PracticeReviewField {
    RUN_FOR_ALL_USERS,
    SKIP_DRAFTS,
    DELIVER_TO_MERGED,
    COOLDOWN_MINUTES,
}
