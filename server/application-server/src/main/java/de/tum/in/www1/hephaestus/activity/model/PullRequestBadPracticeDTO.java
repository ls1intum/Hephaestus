package de.tum.in.www1.hephaestus.activity.model;

public record PullRequestBadPracticeDTO(String title, String description, boolean resolved) {
    public static PullRequestBadPracticeDTO fromPullRequestBadPractice(PullRequestBadPractice badPractice) {
        return new PullRequestBadPracticeDTO(
            badPractice.getTitle(),
            badPractice.getDescription(),
            badPractice.isResolved()
        );
    }
}
