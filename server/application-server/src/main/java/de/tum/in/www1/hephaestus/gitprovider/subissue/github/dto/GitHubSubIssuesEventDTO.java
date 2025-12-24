package de.tum.in.www1.hephaestus.gitprovider.subissue.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.GitHubIssueDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;

/**
 * DTO for GitHub sub_issues webhook events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubSubIssuesEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("sub_issue") GitHubIssueDTO subIssue,
    @JsonProperty("parent_issue") GitHubIssueDTO parentIssue,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @JsonProperty("sender") GitHubUserDTO sender
)
    implements GitHubWebhookEvent {
    public GitHubEventAction.SubIssue actionType() {
        return GitHubEventAction.SubIssue.fromString(action);
    }

    @Override
    public GitHubRepositoryRefDTO repository() {
        return repository;
    }
}
