package de.tum.in.www1.hephaestus.activity.model;

import lombok.Getter;

@Getter
public enum PullRequestLifecycleState {
    DRAFT("Draft"),
    OPEN("Open"),
    READY_FOR_REVIEW("Ready for review"),
    READY_TO_MERGE("Ready to merge"),
    MERGED("Merged"),
    CLOSED("Closed");

    private final String state;

    PullRequestLifecycleState(String state) {
        this.state = state;
    }
}
