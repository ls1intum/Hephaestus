package de.tum.in.www1.hephaestus.gitprovider.issuedependency.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.GitHubIssueDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;

/**
 * DTO for GitHub issue_dependencies webhook events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubIssueDependenciesEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("blocked_issue") GitHubIssueDTO blockedIssue,
    @JsonProperty("blocking_issue") GitHubIssueDTO blockingIssue,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @JsonProperty("sender") GitHubUserDTO sender
)
    implements GitHubWebhookEvent {
    @Override
    public GitHubRepositoryRefDTO repository() {
        return repository;
    }
}
