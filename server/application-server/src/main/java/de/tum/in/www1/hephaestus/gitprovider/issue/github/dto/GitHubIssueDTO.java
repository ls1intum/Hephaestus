package de.tum.in.www1.hephaestus.gitprovider.issue.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHIssue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHIssueState;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHIssueStateReason;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHUserConnection;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto.GitHubMilestoneDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import static de.tum.in.www1.hephaestus.gitprovider.common.DateTimeUtils.toInstant;
import static de.tum.in.www1.hephaestus.gitprovider.common.DateTimeUtils.uriToString;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.lang.Nullable;

/**
 * Domain DTO for GitHub issues.
 * <p>
 * This is the unified model used by both GraphQL sync and webhook handlers.
 * It can be constructed from any source (GraphQL, REST, webhook payload).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubIssueDTO(
    @JsonProperty("id") Long id,
    @JsonProperty("database_id") Long databaseId,
    @JsonProperty("node_id") String nodeId,
    @JsonProperty("number") int number,
    @JsonProperty("title") String title,
    @JsonProperty("body") String body,
    @JsonProperty("state") String state,
    @JsonProperty("state_reason") String stateReason,
    @JsonProperty("html_url") String htmlUrl,
    @JsonProperty("comments") int commentsCount,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt,
    @JsonProperty("closed_at") Instant closedAt,
    @JsonProperty("locked") boolean locked,
    @JsonProperty("user") GitHubUserDTO author,
    @JsonProperty("assignees") List<GitHubUserDTO> assignees,
    @JsonProperty("labels") List<GitHubLabelDTO> labels,
    @JsonProperty("milestone") GitHubMilestoneDTO milestone,
    @JsonProperty("type") GitHubIssueTypeDTO issueType,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository
) {
    /**
     * Get the database ID, preferring databaseId over id for GraphQL responses.
     */
    public Long getDatabaseId() {
        return databaseId != null ? databaseId : id;
    }

    // ========== STATIC FACTORY METHODS FOR GRAPHQL RESPONSES ==========

    /**
     * Creates a GitHubIssueDTO from a GraphQL GHIssue model.
     *
     * @param issue the GraphQL GHIssue (may be null)
     * @return GitHubIssueDTO or null if issue is null
     */
    @Nullable
    public static GitHubIssueDTO fromIssue(@Nullable GHIssue issue) {
        if (issue == null) {
            return null;
        }

        return new GitHubIssueDTO(
            null,
            toLong(issue.getFullDatabaseId()),
            issue.getId(),
            issue.getNumber(),
            issue.getTitle(),
            issue.getBody(),
            convertState(issue.getState()),
            convertStateReason(issue.getStateReason()),
            uriToString(issue.getUrl()),
            issue.getComments() != null ? issue.getComments().getTotalCount() : 0,
            toInstant(issue.getCreatedAt()),
            toInstant(issue.getUpdatedAt()),
            toInstant(issue.getClosedAt()),
            Boolean.TRUE.equals(issue.getLocked()),
            GitHubUserDTO.fromActor(issue.getAuthor()),
            extractAssignees(issue.getAssignees()),
            GitHubLabelDTO.fromLabelConnection(issue.getLabels()),
            GitHubMilestoneDTO.fromMilestone(issue.getMilestone()),
            GitHubIssueTypeDTO.fromIssueType(issue.getIssueType()),
            null
        );
    }

    // ========== CONVERSION HELPERS ==========

    @Nullable
    private static Long toLong(@Nullable BigInteger value) {
        if (value == null) {
            return null;
        }
        return value.longValueExact();
    }

    private static String convertState(@Nullable GHIssueState state) {
        if (state == null) {
            return "open";
        }
        return state.name().toLowerCase();
    }

    @Nullable
    private static String convertStateReason(@Nullable GHIssueStateReason stateReason) {
        if (stateReason == null) {
            return null;
        }
        return stateReason.name().toLowerCase();
    }

    private static List<GitHubUserDTO> extractAssignees(@Nullable GHUserConnection connection) {
        if (connection == null || connection.getNodes() == null) {
            return Collections.emptyList();
        }
        return connection.getNodes().stream().map(GitHubUserDTO::fromUser).filter(Objects::nonNull).toList();
    }
}
