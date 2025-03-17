package de.tum.in.www1.hephaestus.activity.model;

import lombok.NonNull;

public record PullRequestBadPracticeDTO(@NonNull Long id, String title, String description, boolean resolved, boolean userResolved) {
    public static PullRequestBadPracticeDTO fromPullRequestBadPractice(PullRequestBadPractice badPractice) {
        return new PullRequestBadPracticeDTO(
            badPractice.getId(),
            badPractice.getTitle(),
            badPractice.getDescription(),
            badPractice.isResolved(),
            badPractice.isUserResolved()
        );
    }
}
