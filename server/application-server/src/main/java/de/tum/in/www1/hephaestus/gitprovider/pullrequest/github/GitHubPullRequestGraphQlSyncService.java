package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Label;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PullRequestConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PullRequestState;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.User;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto.GitHubMilestoneDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.GitHubBranchRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.GitHubPullRequestDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for synchronizing GitHub pull requests via GraphQL API.
 * <p>
 * This service replaces the deprecated hub4j-based GitHubPullRequestSyncService.
 * It fetches PRs via GraphQL and uses GitHubPullRequestProcessor for persistence.
 */
@Service
public class GitHubPullRequestGraphQlSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestGraphQlSyncService.class);
    private static final int GRAPHQL_PAGE_SIZE = 50;
    private static final Duration GRAPHQL_TIMEOUT = Duration.ofSeconds(60);
    private static final String GET_PRS_DOCUMENT = "GetRepositoryPullRequests";

    private final RepositoryRepository repositoryRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubPullRequestProcessor pullRequestProcessor;

    public GitHubPullRequestGraphQlSyncService(
        RepositoryRepository repositoryRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubPullRequestProcessor pullRequestProcessor
    ) {
        this.repositoryRepository = repositoryRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.pullRequestProcessor = pullRequestProcessor;
    }

    /**
     * Synchronizes all pull requests for a repository using GraphQL.
     *
     * @param workspaceId  the workspace ID for authentication
     * @param repositoryId the repository ID to sync pull requests for
     * @return number of pull requests synced
     */
    @Transactional
    public int syncPullRequestsForRepository(Long workspaceId, Long repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId).orElse(null);
        if (repository == null) {
            logger.warn("Repository {} not found, cannot sync pull requests", repositoryId);
            return 0;
        }

        String[] parts = repository.getNameWithOwner().split("/");
        if (parts.length != 2) {
            logger.warn("Invalid repository nameWithOwner: {}", repository.getNameWithOwner());
            return 0;
        }
        String owner = parts[0];
        String name = parts[1];

        HttpGraphQlClient client = graphQlClientProvider.forWorkspace(workspaceId);
        ProcessingContext context = ProcessingContext.forSync(workspaceId, repository);

        try {
            int totalSynced = 0;
            String cursor = null;
            boolean hasNextPage = true;

            while (hasNextPage) {
                PullRequestConnection response = client
                    .documentName(GET_PRS_DOCUMENT)
                    .variable("owner", owner)
                    .variable("name", name)
                    .variable("first", GRAPHQL_PAGE_SIZE)
                    .variable("after", cursor)
                    .retrieve("repository.pullRequests")
                    .toEntity(PullRequestConnection.class)
                    .block(GRAPHQL_TIMEOUT);

                if (response == null || response.getNodes() == null) {
                    break;
                }

                for (var graphQlPr : response.getNodes()) {
                    GitHubPullRequestDTO dto = convertToDTO(graphQlPr);
                    PullRequest pr = pullRequestProcessor.process(dto, context);
                    if (pr != null) {
                        totalSynced++;
                    }
                }

                var pageInfo = response.getPageInfo();
                hasNextPage = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
            }

            logger.info("Synced {} pull requests for repository {}", totalSynced, repository.getNameWithOwner());
            return totalSynced;
        } catch (Exception e) {
            logger.error(
                "Error syncing pull requests for repository {}: {}",
                repository.getNameWithOwner(),
                e.getMessage(),
                e
            );
            return 0;
        }
    }

    private GitHubPullRequestDTO convertToDTO(
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PullRequest graphQlPr
    ) {
        // Convert author
        GitHubUserDTO author = null;
        var graphQlAuthor = graphQlPr.getAuthor();
        if (graphQlAuthor instanceof User graphQlUser) {
            author = new GitHubUserDTO(
                null,
                graphQlUser.getDatabaseId() != null ? graphQlUser.getDatabaseId().longValue() : null,
                graphQlUser.getLogin(),
                graphQlUser.getAvatarUrl() != null ? graphQlUser.getAvatarUrl().toString() : null,
                null,
                graphQlUser.getName(),
                graphQlUser.getEmail()
            );
        }

        // Convert merged by
        GitHubUserDTO mergedBy = null;
        var graphQlMergedBy = graphQlPr.getMergedBy();
        if (graphQlMergedBy instanceof User graphQlUser) {
            mergedBy = new GitHubUserDTO(
                null,
                graphQlUser.getDatabaseId() != null ? graphQlUser.getDatabaseId().longValue() : null,
                graphQlUser.getLogin(),
                graphQlUser.getAvatarUrl() != null ? graphQlUser.getAvatarUrl().toString() : null,
                null,
                null,
                null
            );
        }

        // Convert labels
        List<GitHubLabelDTO> labels = new ArrayList<>();
        if (graphQlPr.getLabels() != null && graphQlPr.getLabels().getNodes() != null) {
            for (Label l : graphQlPr.getLabels().getNodes()) {
                if (l != null) {
                    labels.add(new GitHubLabelDTO(null, l.getId(), l.getName(), l.getDescription(), l.getColor()));
                }
            }
        }

        // Convert milestone
        GitHubMilestoneDTO milestone = null;
        var graphQlMilestone = graphQlPr.getMilestone();
        if (graphQlMilestone != null) {
            milestone = new GitHubMilestoneDTO(
                null,
                graphQlMilestone.getNumber(),
                graphQlMilestone.getTitle(),
                graphQlMilestone.getDescription(),
                convertMilestoneState(graphQlMilestone.getState()),
                graphQlMilestone.getDueOn() != null ? graphQlMilestone.getDueOn().toInstant() : null,
                graphQlMilestone.getUrl() != null ? graphQlMilestone.getUrl().toString() : null
            );
        }

        // Convert assignees
        List<GitHubUserDTO> assignees = new ArrayList<>();
        if (graphQlPr.getAssignees() != null && graphQlPr.getAssignees().getNodes() != null) {
            for (User a : graphQlPr.getAssignees().getNodes()) {
                if (a != null) {
                    assignees.add(
                        new GitHubUserDTO(
                            null,
                            a.getDatabaseId() != null ? a.getDatabaseId().longValue() : null,
                            a.getLogin(),
                            a.getAvatarUrl() != null ? a.getAvatarUrl().toString() : null,
                            null,
                            a.getName(),
                            null
                        )
                    );
                }
            }
        }

        // Convert requested reviewers
        List<GitHubUserDTO> requestedReviewers = new ArrayList<>();
        if (graphQlPr.getReviewRequests() != null && graphQlPr.getReviewRequests().getNodes() != null) {
            for (var rr : graphQlPr.getReviewRequests().getNodes()) {
                if (rr != null && rr.getRequestedReviewer() instanceof User reviewer) {
                    requestedReviewers.add(
                        new GitHubUserDTO(
                            null,
                            reviewer.getDatabaseId() != null ? reviewer.getDatabaseId().longValue() : null,
                            reviewer.getLogin(),
                            reviewer.getAvatarUrl() != null ? reviewer.getAvatarUrl().toString() : null,
                            null,
                            null,
                            null
                        )
                    );
                }
            }
        }

        return new GitHubPullRequestDTO(
            null, // id
            graphQlPr.getDatabaseId() != null ? graphQlPr.getDatabaseId().longValue() : null, // databaseId
            graphQlPr.getId(), // nodeId
            graphQlPr.getNumber(), // number
            graphQlPr.getTitle(), // title
            graphQlPr.getBody(), // body
            convertState(graphQlPr.getState()), // state
            graphQlPr.getUrl() != null ? graphQlPr.getUrl().toString() : null, // htmlUrl
            graphQlPr.getCreatedAt() != null ? graphQlPr.getCreatedAt().toInstant() : null, // createdAt
            graphQlPr.getUpdatedAt() != null ? graphQlPr.getUpdatedAt().toInstant() : null, // updatedAt
            graphQlPr.getClosedAt() != null ? graphQlPr.getClosedAt().toInstant() : null, // closedAt
            graphQlPr.getMergedAt() != null ? graphQlPr.getMergedAt().toInstant() : null, // mergedAt
            Boolean.TRUE.equals(graphQlPr.getIsDraft()), // isDraft
            graphQlPr.getMergedAt() != null, // isMerged
            null, // mergeable - not fetched via GraphQL
            graphQlPr.getAdditions(), // additions
            graphQlPr.getDeletions(), // deletions
            graphQlPr.getChangedFiles(), // changedFiles
            0, // commits - not fetched
            0, // commentsCount - not fetched
            0, // reviewCommentsCount - not fetched
            author, // author
            assignees, // assignees
            requestedReviewers, // requestedReviewers
            labels, // labels
            milestone, // milestone
            createBranchRef(graphQlPr.getHeadRefName(), graphQlPr.getHeadRefOid()), // head
            createBranchRef(graphQlPr.getBaseRefName(), graphQlPr.getBaseRefOid()), // base
            null // repository - in context
        );
    }

    private GitHubBranchRefDTO createBranchRef(String refName, String sha) {
        if (refName == null) {
            return null;
        }
        return new GitHubBranchRefDTO(
            refName,
            sha,
            null // label
        );
    }

    private String convertState(PullRequestState state) {
        if (state == null) {
            return "open";
        }
        return switch (state) {
            case OPEN -> "open";
            case CLOSED -> "closed";
            case MERGED -> "closed"; // Merged PRs are considered closed
        };
    }

    private String convertMilestoneState(
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.MilestoneState state
    ) {
        if (state == null) {
            return "open";
        }
        return switch (state) {
            case OPEN -> "open";
            case CLOSED -> "closed";
        };
    }
}
