package de.tum.in.www1.hephaestus.activity.model;

/**
 * Lifecycle state of a pull request.
 *
 * <p>Tracks the PR through its development workflow from draft to completion.
 */
public enum PullRequestLifecycleState {
    /** PR is a work-in-progress draft, not ready for review */
    DRAFT("Draft"),
    /** PR is open but may not be ready for review */
    OPEN("Open"),
    /** PR is open and explicitly marked ready for code review */
    READY_FOR_REVIEW("Ready for review"),
    /** PR has approvals and is waiting to be merged */
    READY_TO_MERGE("Ready to merge"),
    /** PR was merged into target branch */
    MERGED("Merged"),
    /** PR was closed without merging */
    CLOSED("Closed");

    private final String state;

    PullRequestLifecycleState(String state) {
        this.state = state;
    }

    /** Returns the display value of this state. */
    public String getState() {
        return state;
    }
}
