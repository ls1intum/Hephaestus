package de.tum.in.www1.hephaestus.activity.model;

import lombok.Getter;

@Getter
public enum BadPracticeType {

    UncheckedCheckbox(
        "Unchecked Checkbox",
        "This pull request contains an unchecked checkbox.",
        "The pull request contains an unchecked checkbox. An unchecked checkbox looks like this: '- [ ]'."
    ),
    EmptySection(
        "Empty Section",
        "This pull request contains an empty section.",
        "The pull request contains an empty section. An empty section is a section that does not contain any content. An empty section is directly followed by another section of the same or bigger title size."
    );

    private final String title;
    private final String description;
    private final String llmPrompt;

    BadPracticeType(String title, String description, String llmPrompt) {
        this.title = title;
        this.description = description;
        this.llmPrompt = llmPrompt;
    }
}
