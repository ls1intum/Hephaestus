package de.tum.in.www1.hephaestus.activity.model;

public enum PullRequestBadPracticeType {
    EMPTY_DESCRIPTION("Empty Description", "The description of your pull request is empty."),
    EMPTY_DESCRIPTION_SECTION(
        "Empty Description Section",
        "The description of your pull request contains an empty section."
    ),
    UNCHECKED_CHECKBOX("Unchecked Checkbox", "A checkbox in the description of your pull request is unchecked.");

    public final String title;

    public final String description;

    PullRequestBadPracticeType(String title, String description) {
        this.title = title;
        this.description = description;
    }
}
