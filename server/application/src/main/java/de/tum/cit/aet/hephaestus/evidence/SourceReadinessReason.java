package de.tum.cit.aet.hephaestus.evidence;

public enum SourceReadinessReason {
    SOURCE_NOT_AVAILABLE,
    SOURCE_INCOMPLETE,
    /** Captured successfully, but holds nothing, and the practice cannot be judged from nothing. */
    SOURCE_EMPTY,
}
