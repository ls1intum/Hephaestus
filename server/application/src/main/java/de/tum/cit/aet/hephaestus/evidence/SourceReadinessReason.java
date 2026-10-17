package de.tum.cit.aet.hephaestus.evidence;

public enum SourceReadinessReason {
    SOURCE_NOT_AVAILABLE,
    REPOSITORY_FETCH_FAILED,
    REVIEW_VERSION_UNAVAILABLE,
    SOURCE_INCOMPLETE,
    /** Captured successfully, but holds nothing, and the practice cannot be judged from nothing. */
    SOURCE_EMPTY,
}
