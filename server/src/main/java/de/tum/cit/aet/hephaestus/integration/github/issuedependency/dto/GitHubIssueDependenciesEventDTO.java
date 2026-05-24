package de.tum.cit.aet.hephaestus.integration.github.issuedependency.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.integration.github.common.GitHubEventAction;
import de.tum.cit.aet.hephaestus.integration.github.common.GitHubWebhookEvent;
import de.tum.cit.aet.hephaestus.integration.github.issue.dto.GitHubIssueDTO;
import de.tum.cit.aet.hephaestus.integration.github.issuedependency.GitHubIssueDependenciesMessageHandler;
import de.tum.cit.aet.hephaestus.integration.github.repository.dto.GitHubRepositoryRefDTO;
import de.tum.cit.aet.hephaestus.integration.github.user.dto.GitHubUserDTO;

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
