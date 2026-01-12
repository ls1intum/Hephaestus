package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHMergeStateStatus;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHMergeableState;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPullRequest;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPullRequestReviewDecision;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPullRequestState;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHReviewRequest;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHReviewRequestConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHUser;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHUserConnection;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto.GitHubMilestoneDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.MergeStateStatus;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.ReviewDecision;
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
 * Domain DTO for GitHub pull requests.
 * <p>
 * This is the unified model used by both GraphQL sync and webhook handlers.
 * It can be constructed from any source (GraphQL, REST, webhook payload).
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
    @JsonProperty("merged_by") GitHubUserDTO mergedBy,
    @JsonProperty("merge_commit_sha") String mergeCommitSha,
    @JsonProperty("draft") boolean isDraft,
    @JsonProperty("merged") boolean isMerged,
    @JsonProperty("mergeable") String mergeable,
    @JsonProperty("locked") boolean locked,
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
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    // GraphQL-only fields
    @Nullable ReviewDecision reviewDecision,
    @Nullable MergeStateStatus mergeStateStatus,
    @Nullable Boolean isMergeable,
    boolean maintainerCanModify
) {
    /**
     * Get the database ID, preferring databaseId over id for GraphQL responses.
     */
    public Long getDatabaseId() {
        return databaseId != null ? databaseId : id;
    }

    // ========== STATIC FACTORY METHODS FOR GRAPHQL RESPONSES ==========

    /**
     * Creates a GitHubPullRequestDTO from a GraphQL GHPullRequest model.
     *
     * @param pr the GraphQL GHPullRequest (may be null)
     * @return GitHubPullRequestDTO or null if pr is null
     */
    @Nullable
    public static GitHubPullRequestDTO fromPullRequest(@Nullable GHPullRequest pr) {
        if (pr == null) {
            return null;
        }

        boolean isMerged = pr.getMergedAt() != null;

        // Extract commits count from connection
        int commitsCount = 0;
        if (pr.getCommits() != null) {
            commitsCount = pr.getCommits().getTotalCount();
        }

        return new GitHubPullRequestDTO(
            null,
            toLong(pr.getFullDatabaseId()),
            pr.getId(),
            pr.getNumber(),
            pr.getTitle(),
            pr.getBody(),
            convertState(pr.getState()),
            uriToString(pr.getUrl()),
            toInstant(pr.getCreatedAt()),
            toInstant(pr.getUpdatedAt()),
            toInstant(pr.getClosedAt()),
            toInstant(pr.getMergedAt()),
            GitHubUserDTO.fromActor(pr.getMergedBy()),
            pr.getMergeCommit() != null ? pr.getMergeCommit().getOid() : null,
            pr.getIsDraft(),
            isMerged,
            null, // mergeable
            pr.getLocked(),
            pr.getAdditions(),
            pr.getDeletions(),
            pr.getChangedFiles(),
            commitsCount,
            0, // comments count
            0, // review comments count
            GitHubUserDTO.fromActor(pr.getAuthor()),
            extractAssignees(pr.getAssignees()),
            extractRequestedReviewers(pr.getReviewRequests()),
            GitHubLabelDTO.fromLabelConnection(pr.getLabels()),
            GitHubMilestoneDTO.fromMilestone(pr.getMilestone()),
            new GitHubBranchRefDTO(pr.getHeadRefName(), pr.getHeadRefOid(), null),
            new GitHubBranchRefDTO(pr.getBaseRefName(), pr.getBaseRefOid(), null),
            null,
            // GraphQL-only fields
            convertReviewDecision(pr.getReviewDecision()),
            convertMergeStateStatus(pr.getMergeStateStatus()),
            convertMergeableState(pr.getMergeable()),
            pr.getMaintainerCanModify()
        );
    }

    // ========== CONVERSION HELPERS ==========

    @Nullable
    private static ReviewDecision convertReviewDecision(@Nullable GHPullRequestReviewDecision decision) {
        if (decision == null) {
            return null;
        }
        return switch (decision) {
            case APPROVED -> ReviewDecision.APPROVED;
            case CHANGES_REQUESTED -> ReviewDecision.CHANGES_REQUESTED;
            case REVIEW_REQUIRED -> ReviewDecision.REVIEW_REQUIRED;
        };
    }

    /**
     * Maps GraphQL GHMergeStateStatus to our domain model.
     *
     * <p>Note: The DRAFT status is deprecated by GitHub but still returned in the schema.
     * We map it to BLOCKED since draft PRs are effectively blocked from merging.
     */
    @Nullable
    @SuppressWarnings("deprecation") // DRAFT is deprecated but still in the schema
    private static MergeStateStatus convertMergeStateStatus(
        GHMergeStateStatus status
    ) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case BEHIND -> MergeStateStatus.BEHIND;
            case BLOCKED -> MergeStateStatus.BLOCKED;
            case CLEAN -> MergeStateStatus.CLEAN;
            case DIRTY -> MergeStateStatus.DIRTY;
            case DRAFT -> MergeStateStatus.BLOCKED; // DRAFT is deprecated, map to BLOCKED
            case HAS_HOOKS -> MergeStateStatus.HAS_HOOKS;
            case UNKNOWN -> MergeStateStatus.UNKNOWN;
            case UNSTABLE -> MergeStateStatus.UNSTABLE;
        };
    }

    @Nullable
    private static Boolean convertMergeableState(@Nullable GHMergeableState state) {
        if (state == null) {
            return null;
        }
        return switch (state) {
            case MERGEABLE -> true;
            case CONFLICTING -> false;
            case UNKNOWN -> null;
        };
    }

    @Nullable
    private static Long toLong(@Nullable BigInteger value) {
        if (value == null) {
            return null;
        }
        return value.longValueExact();
    }

    private static String convertState(@Nullable GHPullRequestState state) {
        if (state == null) {
            return "open";
        }
        return state.name().toLowerCase();
    }

    private static List<GitHubUserDTO> extractAssignees(@Nullable GHUserConnection connection) {
        if (connection == null || connection.getNodes() == null) {
            return Collections.emptyList();
        }
        return connection.getNodes().stream().map(GitHubUserDTO::fromUser).filter(Objects::nonNull).toList();
    }

    private static List<GitHubUserDTO> extractRequestedReviewers(@Nullable GHReviewRequestConnection connection) {
        if (connection == null || connection.getNodes() == null) {
            return Collections.emptyList();
        }
        return connection
            .getNodes()
            .stream()
            .map(GHReviewRequest::getRequestedReviewer)
            .filter(reviewer -> reviewer instanceof GHUser)
            .map(reviewer -> GitHubUserDTO.fromUser((GHUser) reviewer))
            .filter(Objects::nonNull)
            .toList();
    }
}
