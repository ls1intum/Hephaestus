package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto.GitHubMilestoneDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.time.Instant;
import java.util.List;

/**
 * Domain DTO for GitHub pull requests.
 * <p>
 * This is the unified model used by both GraphQL sync and webhook handlers.
 * It's independent of hub4j and can be constructed from any source.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPullRequestDTO(
    @JsonProperty("id") Long id,
    @JsonProperty("database_id") Long databaseId,
    @JsonProperty("node_id") String nodeId,
    @JsonProperty("number") int number,
    @JsonProperty("title") String title,
    @JsonProperty("body") String body,
    @JsonProperty("state") String state,
    @JsonProperty("html_url") String htmlUrl,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt,
    @JsonProperty("closed_at") Instant closedAt,
    @JsonProperty("merged_at") Instant mergedAt,
    @JsonProperty("draft") boolean isDraft,
    @JsonProperty("merged") boolean isMerged,
    @JsonProperty("mergeable") String mergeable,
    @JsonProperty("additions") int additions,
    @JsonProperty("deletions") int deletions,
    @JsonProperty("changed_files") int changedFiles,
    @JsonProperty("commits") int commits,
    @JsonProperty("comments") int commentsCount,
    @JsonProperty("review_comments") int reviewCommentsCount,
    @JsonProperty("user") GitHubUserDTO author,
    @JsonProperty("assignees") List<GitHubUserDTO> assignees,
    @JsonProperty("requested_reviewers") List<GitHubUserDTO> requestedReviewers,
    @JsonProperty("labels") List<GitHubLabelDTO> labels,
    @JsonProperty("milestone") GitHubMilestoneDTO milestone,
    @JsonProperty("head") GitHubBranchRefDTO head,
    @JsonProperty("base") GitHubBranchRefDTO base,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository
) {
    /**
     * Get the database ID, preferring databaseId over id for GraphQL responses.
     */
    public Long getDatabaseId() {
        return databaseId != null ? databaseId : id;
    }
}
