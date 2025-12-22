package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.GitHubPullRequestDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.time.Instant;

/**
 * DTO for GitHub pull_request_review webhook events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPullRequestReviewEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("review") GitHubReviewDTO review,
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
     * DTO for the review within the event.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubReviewDTO(
        @JsonProperty("id") Long id,
        @JsonProperty("node_id") String nodeId,
        @JsonProperty("body") String body,
        @JsonProperty("state") String state,
        @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("user") GitHubUserDTO author,
        @JsonProperty("submitted_at") Instant submittedAt
    ) {}
}
