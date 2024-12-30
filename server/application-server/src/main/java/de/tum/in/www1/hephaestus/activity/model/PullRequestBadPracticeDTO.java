package de.tum.in.www1.hephaestus.activity.model;

public record PullRequestBadPracticeDTO(String title, String description) {
    public static PullRequestBadPracticeDTO fromPullRequestBadPracticeType(PullRequestBadPracticeType type) {
        return new PullRequestBadPracticeDTO(type.title, type.description);
    }

    public static PullRequestBadPracticeDTO fromPullRequestBadPractice(PullRequestBadPractice badPractice) {
        return new PullRequestBadPracticeDTO(badPractice.getType().title, badPractice.getType().description);
    }
}
