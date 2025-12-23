package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Actor;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.CommentAuthorAssociation;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.DiffSide;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PullRequestReviewCommentConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PullRequestReviewThreadConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.User;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github.dto.GitHubPullRequestReviewCommentEventDTO.GitHubReviewCommentDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for synchronizing GitHub pull request review comments via GraphQL API.
 * <p>
 * This service fetches pull request review comments via GraphQL (through review threads)
 * and uses GitHubPullRequestReviewCommentProcessor for persistence. It supports syncing
 * comments for a single PR or all PRs in a repository.
 * <p>
 * <b>Threading Model:</b> GraphQL provides explicit thread structure via reviewThreads,
 * which is more accurate than the synthetic threading from webhooks. This service
 * creates/updates threads based on GraphQL data and associates comments with them.
 */
@Service
public class GitHubPullRequestReviewCommentSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewCommentSyncService.class);
    private static final int GRAPHQL_PAGE_SIZE = 50;
    private static final Duration GRAPHQL_TIMEOUT = Duration.ofSeconds(60);
    private static final String GET_PR_REVIEW_COMMENTS_DOCUMENT = "GetPullRequestReviewComments";

    private final RepositoryRepository repositoryRepository;
    private final PullRequestRepository pullRequestRepository;
    private final PullRequestReviewThreadRepository threadRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubPullRequestReviewCommentProcessor commentProcessor;

    public GitHubPullRequestReviewCommentSyncService(
        RepositoryRepository repositoryRepository,
        PullRequestRepository pullRequestRepository,
        PullRequestReviewThreadRepository threadRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubPullRequestReviewCommentProcessor commentProcessor
    ) {
        this.repositoryRepository = repositoryRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.threadRepository = threadRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.commentProcessor = commentProcessor;
    }

    /**
     * Synchronizes all review comments for all pull requests in a repository using GraphQL.
     *
     * @param workspaceId  the workspace ID for authentication
     * @param repositoryId the repository ID to sync comments for
     * @return number of comments synced
     */
    @Transactional
    public int syncCommentsForRepository(Long workspaceId, Long repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId).orElse(null);
        if (repository == null) {
            logger.warn("Repository {} not found, cannot sync review comments", repositoryId);
            return 0;
        }

        List<PullRequest> pullRequests = pullRequestRepository.findAllByRepository_Id(repositoryId);
        if (pullRequests.isEmpty()) {
            logger.info(
                "No pull requests found for repository {}, skipping review comment sync",
                repository.getNameWithOwner()
            );
            return 0;
        }

        int totalSynced = 0;
        for (PullRequest pullRequest : pullRequests) {
            int synced = syncCommentsForPullRequest(workspaceId, pullRequest);
            totalSynced += synced;
        }

        logger.info(
            "Synced {} review comments for {} pull requests in repository {}",
            totalSynced,
            pullRequests.size(),
            repository.getNameWithOwner()
        );
        return totalSynced;
    }

    /**
     * Synchronizes all review comments for a single pull request using GraphQL.
     *
     * @param workspaceId the workspace ID for authentication
     * @param pullRequest the pull request to sync comments for
     * @return number of comments synced
     */
    @Transactional
    public int syncCommentsForPullRequest(Long workspaceId, PullRequest pullRequest) {
        if (pullRequest == null) {
            logger.warn("Pull request is null, cannot sync review comments");
            return 0;
        }

        Repository repository = pullRequest.getRepository();
        if (repository == null) {
            logger.warn("Pull request {} has no repository, cannot sync review comments", pullRequest.getId());
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

        try {
            int totalSynced = 0;
            String cursor = null;
            boolean hasNextPage = true;

            while (hasNextPage) {
                PullRequestReviewThreadConnection response = client
                    .documentName(GET_PR_REVIEW_COMMENTS_DOCUMENT)
                    .variable("owner", owner)
                    .variable("name", name)
                    .variable("number", pullRequest.getNumber())
                    .variable("first", GRAPHQL_PAGE_SIZE)
                    .variable("after", cursor)
                    .retrieve("repository.pullRequest.reviewThreads")
                    .toEntity(PullRequestReviewThreadConnection.class)
                    .block(GRAPHQL_TIMEOUT);

                if (response == null || response.getNodes() == null) {
                    break;
                }

                for (var graphQlThread : response.getNodes()) {
                    int synced = processThread(graphQlThread, pullRequest);
                    totalSynced += synced;
                }

                var pageInfo = response.getPageInfo();
                hasNextPage = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
            }

            logger.debug(
                "Synced {} review comments for PR #{} in repository {}",
                totalSynced,
                pullRequest.getNumber(),
                repository.getNameWithOwner()
            );
            return totalSynced;
        } catch (Exception e) {
            logger.error(
                "Error syncing review comments for PR #{} in repository {}: {}",
                pullRequest.getNumber(),
                repository.getNameWithOwner(),
                e.getMessage(),
                e
            );
            return 0;
        }
    }

    /**
     * Processes a GraphQL review thread and its comments.
     *
     * @param graphQlThread the GraphQL review thread
     * @param pullRequest   the pull request the thread belongs to
     * @return number of comments synced from this thread
     */
    private int processThread(
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PullRequestReviewThread graphQlThread,
        PullRequest pullRequest
    ) {
        if (graphQlThread == null) {
            return 0;
        }

        PullRequestReviewCommentConnection commentsConnection = graphQlThread.getComments();
        if (commentsConnection == null || commentsConnection.getNodes() == null) {
            return 0;
        }

        var graphQlComments = commentsConnection.getNodes();
        if (graphQlComments.isEmpty()) {
            return 0;
        }

        // Get the first comment to determine the thread ID (use its databaseId as thread ID)
        var firstComment = graphQlComments.get(0);
        Long threadId = extractDatabaseId(firstComment);
        if (threadId == null) {
            logger.warn("First comment in thread has no databaseId, skipping thread: nodeId={}", graphQlThread.getId());
            return 0;
        }

        // Create or update the thread
        PullRequestReviewThread thread = getOrCreateThread(threadId, graphQlThread, pullRequest);

        int synced = 0;
        for (var graphQlComment : graphQlComments) {
            GitHubReviewCommentDTO dto = convertToDTO(graphQlComment, thread);
            if (dto != null) {
                PullRequestReviewComment comment = commentProcessor.processCreated(dto, pullRequest.getId());
                if (comment != null) {
                    synced++;
                }
            }
        }

        return synced;
    }

    /**
     * Gets or creates a thread based on GraphQL data.
     */
    private PullRequestReviewThread getOrCreateThread(
        Long threadId,
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PullRequestReviewThread graphQlThread,
        PullRequest pullRequest
    ) {
        return threadRepository
            .findById(threadId)
            .orElseGet(() -> {
                PullRequestReviewThread thread = new PullRequestReviewThread();
                thread.setId(threadId);
                thread.setNodeId(graphQlThread.getId());
                thread.setPullRequest(pullRequest);
                thread.setPath(graphQlThread.getPath());
                thread.setLine(graphQlThread.getLine());
                thread.setStartLine(graphQlThread.getStartLine());
                thread.setSide(mapDiffSide(graphQlThread.getDiffSide()));
                thread.setStartSide(mapDiffSide(graphQlThread.getStartDiffSide()));
                thread.setOutdated(graphQlThread.getIsOutdated());
                thread.setCollapsed(graphQlThread.getIsCollapsed());

                // Map resolved state
                if (graphQlThread.getIsResolved()) {
                    thread.setState(PullRequestReviewThread.State.RESOLVED);
                } else {
                    thread.setState(PullRequestReviewThread.State.UNRESOLVED);
                }

                return threadRepository.save(thread);
            });
    }

    /**
     * Extracts the database ID from a GraphQL comment.
     * Uses fullDatabaseId if available, otherwise falls back to deprecated databaseId.
     */
    private Long extractDatabaseId(
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PullRequestReviewComment graphQlComment
    ) {
        if (graphQlComment == null) {
            return null;
        }

        // Prefer fullDatabaseId (BigInteger) over deprecated databaseId (Integer)
        BigInteger fullDbId = graphQlComment.getFullDatabaseId();
        if (fullDbId != null) {
            return fullDbId.longValue();
        }

        Integer dbId = graphQlComment.getDatabaseId();
        if (dbId != null) {
            return dbId.longValue();
        }

        return null;
    }

    /**
     * Converts a GraphQL PullRequestReviewComment to a GitHubReviewCommentDTO.
     *
     * @param graphQlComment the GraphQL review comment
     * @param thread         the thread this comment belongs to
     * @return the DTO for processing, or null if databaseId is missing
     */
    private GitHubReviewCommentDTO convertToDTO(
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PullRequestReviewComment graphQlComment,
        PullRequestReviewThread thread
    ) {
        if (graphQlComment == null) {
            return null;
        }

        Long databaseId = extractDatabaseId(graphQlComment);
        if (databaseId == null) {
            logger.warn("Comment has no databaseId, skipping: nodeId={}", graphQlComment.getId());
            return null;
        }

        // Convert author
        GitHubUserDTO author = null;
        Actor graphQlAuthor = graphQlComment.getAuthor();
        if (graphQlAuthor instanceof User graphQlUser) {
            author = new GitHubUserDTO(
                null, // id (node_id)
                graphQlUser.getDatabaseId() != null ? graphQlUser.getDatabaseId().longValue() : null, // databaseId
                graphQlUser.getLogin(), // login
                graphQlUser.getAvatarUrl() != null ? graphQlUser.getAvatarUrl().toString() : null, // avatarUrl
                null, // htmlUrl
                graphQlUser.getName(), // name
                graphQlUser.getEmail() // email
            );
        }

        // Convert author association
        String authorAssociation = convertAuthorAssociation(graphQlComment.getAuthorAssociation());

        // Get review ID if present
        Long reviewId = null;
        if (graphQlComment.getPullRequestReview() != null) {
            Integer reviewDbId = graphQlComment.getPullRequestReview().getDatabaseId();
            if (reviewDbId != null) {
                reviewId = reviewDbId.longValue();
            }
        }

        // Get reply-to ID if present
        Long inReplyToId = null;
        if (graphQlComment.getReplyTo() != null) {
            Integer replyToDbId = graphQlComment.getReplyTo().getDatabaseId();
            if (replyToDbId != null) {
                inReplyToId = replyToDbId.longValue();
            }
        }

        // Map side from DiffSide enum
        String side = mapDiffSideToString(graphQlComment.getSubjectType() != null ? null : null);
        // GraphQL comment doesn't have direct side field, use thread's side
        if (thread != null && thread.getSide() != null) {
            side = thread.getSide().name();
        }

        return new GitHubReviewCommentDTO(
            databaseId, // id
            graphQlComment.getId(), // nodeId
            graphQlComment.getDiffHunk(), // diffHunk
            graphQlComment.getPath(), // path
            graphQlComment.getBody(), // body
            null, // htmlUrl - not directly available in GraphQL, would need resourcePath
            author, // author
            graphQlComment.getCreatedAt() != null ? graphQlComment.getCreatedAt().toInstant() : null, // createdAt
            graphQlComment.getUpdatedAt() != null ? graphQlComment.getUpdatedAt().toInstant() : null, // updatedAt
            reviewId, // reviewId
            null, // commitId - would need to fetch from commit.oid
            null, // originalCommitId
            authorAssociation, // authorAssociation
            graphQlComment.getLine(), // line
            graphQlComment.getOriginalLine(), // originalLine
            graphQlComment.getStartLine(), // startLine
            graphQlComment.getOriginalStartLine(), // originalStartLine
            side, // side
            null, // startSide
            graphQlComment.getPosition(), // position
            graphQlComment.getOriginalPosition(), // originalPosition
            inReplyToId // inReplyToId
        );
    }

    /**
     * Converts a GraphQL CommentAuthorAssociation to its string representation.
     *
     * @param association the GraphQL author association
     * @return the string representation
     */
    private String convertAuthorAssociation(CommentAuthorAssociation association) {
        if (association == null) {
            return "NONE";
        }
        return association.name();
    }

    /**
     * Maps a GraphQL DiffSide to the entity Side enum.
     */
    private PullRequestReviewComment.Side mapDiffSide(DiffSide diffSide) {
        if (diffSide == null) {
            return null;
        }
        return switch (diffSide) {
            case LEFT -> PullRequestReviewComment.Side.LEFT;
            case RIGHT -> PullRequestReviewComment.Side.RIGHT;
        };
    }

    /**
     * Maps a DiffSide to string representation.
     */
    private String mapDiffSideToString(DiffSide diffSide) {
        if (diffSide == null) {
            return null;
        }
        return diffSide.name();
    }
}
