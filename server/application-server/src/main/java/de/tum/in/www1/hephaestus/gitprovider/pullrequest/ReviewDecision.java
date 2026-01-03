package de.tum.in.www1.hephaestus.gitprovider.pullrequest;

/**
 * The review decision state for a pull request.
 * Indicates whether the PR has been approved or requires changes.
 */
public enum ReviewDecision {
    /** The PR has been approved by required reviewers. */
    APPROVED,

    /** Changes have been requested by a reviewer. */
    CHANGES_REQUESTED,

    /** A review is required before the PR can be merged. */
    REVIEW_REQUIRED,
}
