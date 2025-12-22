package de.tum.in.www1.hephaestus.gitprovider.issuecomment.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.GitHubIssueDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.time.Instant;

/**
 * DTO for GitHub issue_comment webhook events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubIssueCommentEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("comment") GitHubCommentDTO comment,
    @JsonProperty("issue") GitHubIssueDTO issue,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @JsonProperty("sender") GitHubUserDTO sender
)
    implements GitHubWebhookEvent {
    @Override
    public GitHubRepositoryRefDTO repository() {
        return repository;
    }

    /**
     * DTO for the comment within the event.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubCommentDTO(
        @JsonProperty("id") Long id,
        @JsonProperty("node_id") String nodeId,
        @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("body") String body,
        @JsonProperty("user") GitHubUserDTO author,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt
    ) {}
}
