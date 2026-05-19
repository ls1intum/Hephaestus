package de.tum.in.www1.hephaestus.gitprovider.pullrequest;

/**
 * The merge state status of a pull request.
 * Indicates whether the PR can be merged based on branch status and CI checks.
 */
public enum MergeStateStatus {
    /** The head ref is out of date with the base ref. */
    BEHIND,

    /** The merge is blocked (e.g., by branch protection rules). */
    BLOCKED,

    /** Mergeable and passing all required checks. */
    CLEAN,

    /** The merge commit cannot be cleanly created due to conflicts. */
    DIRTY,

    /** Pre-receive hooks failed for the merge. */
    HAS_HOOKS,

    /** The state cannot be determined. */
    UNKNOWN,

    /** Mergeable with passing commit status, but failing other checks. */
    UNSTABLE,
}
