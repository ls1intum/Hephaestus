package de.tum.in.www1.hephaestus.activity;

public record PullRequestBadPracticeDTO(String title, String description) {

    public static PullRequestBadPracticeDTO fromPullRequestBadPracticeType(PullRequestBadPracticeType type) {
        return new PullRequestBadPracticeDTO(type.title, type.description);
    }
}
