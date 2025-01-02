package de.tum.in.www1.hephaestus.activity.model;


import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO;

public record PullRequestBadPracticeRuleDTO(
    Long id,
    String title,
    String description,
    String conditions,
    RepositoryInfoDTO repository,
    boolean active
) {
    public static PullRequestBadPracticeRuleDTO fromPullRequestBadPracticeRule(PullRequestBadPracticeRule rule) {
        return new PullRequestBadPracticeRuleDTO(
            rule.getId(),
            rule.getTitle(),
            rule.getDescription(),
            rule.getConditions(),
            RepositoryInfoDTO.fromRepository(rule.getRepository()),
            rule.isActive()
        );
    }
}
