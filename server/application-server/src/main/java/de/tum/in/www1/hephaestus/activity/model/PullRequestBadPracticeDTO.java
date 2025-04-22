package de.tum.in.www1.hephaestus.activity.model;

import org.springframework.lang.NonNull;

public record PullRequestBadPracticeDTO(
    @NonNull Long id,
    @NonNull String title,
    @NonNull String description,
    @NonNull PullRequestBadPracticeState state
) {
    public static PullRequestBadPracticeDTO fromPullRequestBadPractice(PullRequestBadPractice badPractice) {
        return new PullRequestBadPracticeDTO(
            badPractice.getId(),
            badPractice.getTitle(),
            badPractice.getDescription(),
            badPractice.getState()
        );
    }
}
