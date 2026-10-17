package de.tum.cit.aet.hephaestus.practices.review.schedule;

public enum ReviewScheduleOutcome {
    OPENED,

    SKIPPED_DISABLED,

    SKIPPED_WORKSPACE_UNAVAILABLE,

    SKIPPED_BATCH_UNDER_WAY,

    SKIPPED_EMPTY_SCOPE,

    SKIPPED_SCOPE_TOO_LARGE,
}
