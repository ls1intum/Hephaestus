package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.GitHubPullRequestDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;

/**
 * DTO for GitHub pull_request_review_thread webhook events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPullRequestReviewThreadEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("thread") GitHubThreadDTO thread,
    @JsonProperty("pull_request") GitHubPullRequestDTO pullRequest,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @JsonProperty("sender") GitHubUserDTO sender
)
    implements GitHubWebhookEvent {
    @Override
    public GitHubRepositoryRefDTO repository() {
        return repository;
    }

    /**
     * DTO for the thread within the event.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubThreadDTO(
        @JsonProperty("id") Long id,
        @JsonProperty("node_id") String nodeId,
        @JsonProperty("diff_side") String diffSide,
        @JsonProperty("line") Integer line,
        @JsonProperty("start_line") Integer startLine,
        @JsonProperty("path") String path
    ) {}
}
