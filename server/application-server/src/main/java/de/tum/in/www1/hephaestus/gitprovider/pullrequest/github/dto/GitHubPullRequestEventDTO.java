package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.util.Map;

/**
 * DTO for GitHub pull request webhook event payloads.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPullRequestEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("number") int number,
    @JsonProperty("pull_request") GitHubPullRequestDTO pullRequest,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @JsonProperty("sender") GitHubUserDTO sender,
    @JsonProperty("label") GitHubLabelDTO label,
    @JsonProperty("requested_reviewer") GitHubUserDTO requestedReviewer,
    @JsonProperty("changes") Map<String, Object> changes
) implements GitHubWebhookEvent {
    public GitHubEventAction.PullRequest actionType() {
        return GitHubEventAction.PullRequest.fromString(action);
    }
}
