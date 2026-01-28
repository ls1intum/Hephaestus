package de.tum.in.www1.hephaestus.practices.model;

/**
 * Constants for common pull request label names used in practice detection.
 *
 * <p>These labels indicate PR lifecycle state and are used to determine when
 * to trigger bad practice detection and how to classify PR readiness.
 */
public final class PullRequestLabels {

    /** Label indicating the PR is ready for code review (variant 1). */
    public static final String READY_TO_REVIEW = "ready to review";

    /** Label indicating the PR is ready for code review (variant 2). */
    public static final String READY_FOR_REVIEW = "ready for review";

    /** Label indicating the PR has been approved and is ready to merge. */
    public static final String READY_TO_MERGE = "ready to merge";

    private PullRequestLabels() {
        // Prevent instantiation
    }
}
