package de.tum.in.www1.hephaestus.gitprovider.issuedependency.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.GitHubIssueDTO;
import de.tum.in.www1.hephaestus.gitprovider.issuedependency.github.GitHubIssueDependenciesMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;

/**
 * DTO for GitHub issue_dependencies webhook events.
 * <p>
 * <b>Note:</b> This DTO is defined based on GitHub's webhook documentation, but the
 * {@code issue_dependencies} event cannot currently be subscribed to via GitHub App
 * settings (as of January 2026). No real webhook payloads have been captured.
 * <p>
 * Structure is based on: https://docs.github.com/en/webhooks/webhook-events-and-payloads#issue_dependencies
 *
 * @see GitHubIssueDependenciesMessageHandler for full documentation on this limitation
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubIssueDependenciesEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("blocked_issue") GitHubIssueDTO blockedIssue,
    @JsonProperty("blocking_issue") GitHubIssueDTO blockingIssue,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @JsonProperty("sender") GitHubUserDTO sender
) implements GitHubWebhookEvent {
    @Override
    public GitHubEventAction.IssueDependency actionType() {
        return GitHubEventAction.IssueDependency.fromString(action);
    }

    @Override
    public GitHubRepositoryRefDTO repository() {
        return repository;
    }
}
