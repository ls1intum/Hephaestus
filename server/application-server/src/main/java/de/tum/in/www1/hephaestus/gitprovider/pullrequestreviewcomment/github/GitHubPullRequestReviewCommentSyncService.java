package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlErrorUtils.isNotFoundError;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.*;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser.RepositoryOwnerAndName;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHActor;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHCommentAuthorAssociation;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHDiffSide;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPullRequestReviewCommentConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPullRequestReviewThreadConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHUser;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github.dto.GitHubPullRequestReviewCommentEventDTO.GitHubReviewCommentDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserProcessor;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.FieldAccessException;
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
@SuppressWarnings("deprecation")
@Service
public class GitHubPullRequestReviewCommentSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubPullRequestReviewCommentSyncService.class);
    private static final String GET_PR_REVIEW_COMMENTS_DOCUMENT = "GetPullRequestReviewComments";
    private static final String GET_THREAD_COMMENTS_DOCUMENT = "GetThreadComments";

    private final RepositoryRepository repositoryRepository;
    private final PullRequestRepository pullRequestRepository;
    private final PullRequestReviewThreadRepository threadRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubPullRequestReviewCommentProcessor commentProcessor;
    private final GitHubUserProcessor userProcessor;
    // Store client reference for nested pagination
    private HttpGraphQlClient currentClient;

    public GitHubPullRequestReviewCommentSyncService(
        RepositoryRepository repositoryRepository,
        PullRequestRepository pullRequestRepository,
        PullRequestReviewThreadRepository threadRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubPullRequestReviewCommentProcessor commentProcessor,
        GitHubUserProcessor userProcessor
    ) {
        this.repositoryRepository = repositoryRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.threadRepository = threadRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.commentProcessor = commentProcessor;
        this.userProcessor = userProcessor;
    }

    /**
     * Synchronizes all review comments for all pull requests in a repository using GraphQL.
     * <p>
     * Uses streaming to avoid loading all pull requests into memory at once.
     *
     * @param scopeId  the scope ID for authentication
     * @param repositoryId the repository ID to sync comments for
     * @return number of comments synced
     */
    @Transactional
    public int syncCommentsForRepository(Long scopeId, Long repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId).orElse(null);
        if (repository == null) {
            log.warn("Skipped review comment sync: reason=repositoryNotFound, repoId={}", repositoryId);
            return 0;
        }

        String safeRepoName = sanitizeForLog(repository.getNameWithOwner());
        AtomicInteger totalSynced = new AtomicInteger(0);
        AtomicInteger prCount = new AtomicInteger(0);

        try (Stream<PullRequest> prStream = pullRequestRepository.streamAllByRepository_Id(repositoryId)) {
            prStream.forEach(pullRequest -> {
                int synced = syncCommentsForPullRequest(scopeId, pullRequest);
                totalSynced.addAndGet(synced);
                prCount.incrementAndGet();
            });
        }

        if (prCount.get() == 0) {
            log.debug("Skipped review comment sync: reason=noPullRequestsFound, repoName={}", safeRepoName);
            return 0;
        }

        log.info(
            "Completed review comment sync: repoName={}, commentCount={}, prCount={}",
            safeRepoName,
            totalSynced.get(),
            prCount.get()
        );
        return totalSynced.get();
    }

    /**
     * Synchronizes all review comments for a single pull request using GraphQL.
     *
     * @param scopeId the scope ID for authentication
     * @param pullRequest the pull request to sync comments for
     * @return number of comments synced
     */
    @Transactional
    public int syncCommentsForPullRequest(Long scopeId, PullRequest pullRequest) {
        if (pullRequest == null) {
            log.warn("Skipped review comment sync: reason=prIsNull");
            return 0;
        }

        Repository repository = pullRequest.getRepository();
        if (repository == null) {
            log.warn("Skipped review comment sync: reason=prHasNoRepository, prId={}", pullRequest.getId());
            return 0;
        }

        String safeNameWithOwner = sanitizeForLog(repository.getNameWithOwner());
        Optional<RepositoryOwnerAndName> parsedName = GitHubRepositoryNameParser.parse(repository.getNameWithOwner());
        if (parsedName.isEmpty()) {
            log.warn("Skipped review comment sync: reason=invalidRepoNameFormat, repoName={}", safeNameWithOwner);
            return 0;
        }
        String owner = parsedName.get().owner();
        String name = parsedName.get().name();

        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);
        this.currentClient = client; // Store for nested pagination

        try {
            int totalSynced = 0;
            String cursor = null;
            boolean hasNextPage = true;
            int pageCount = 0;

            while (hasNextPage) {
                pageCount++;
                if (pageCount >= MAX_PAGINATION_PAGES) {
                    log.warn(
                        "Reached maximum pagination limit for review comments: repoName={}, prNumber={}, limit={}",
                        safeNameWithOwner,
                        pullRequest.getNumber(),
                        MAX_PAGINATION_PAGES
                    );
                    break;
                }

                GHPullRequestReviewThreadConnection response = client
                    .documentName(GET_PR_REVIEW_COMMENTS_DOCUMENT)
                    .variable("owner", owner)
                    .variable("name", name)
                    .variable("number", pullRequest.getNumber())
                    .variable("first", DEFAULT_PAGE_SIZE)
                    .variable("after", cursor)
                    .retrieve("repository.pullRequest.reviewThreads")
                    .toEntity(GHPullRequestReviewThreadConnection.class)
                    .block(EXTENDED_GRAPHQL_TIMEOUT);

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

            log.debug(
                "Synced review comments for pull request: repoName={}, prNumber={}, commentCount={}",
                safeNameWithOwner,
                pullRequest.getNumber(),
                totalSynced
            );
            return totalSynced;
        } catch (FieldAccessException e) {
            // Check if this is a NOT_FOUND error (PR deleted from GitHub)
            if (isNotFoundError(e.getResponse(), "repository.pullRequest")) {
                log.debug(
                    "Skipped review comment sync: reason=prDeletedFromGitHub, repoName={}, prNumber={}",
                    safeNameWithOwner,
                    pullRequest.getNumber()
                );
                return 0;
            }
            log.error(
                "Failed to sync review comments: repoName={}, prNumber={}",
                safeNameWithOwner,
                pullRequest.getNumber(),
                e
            );
            return 0;
        } catch (Exception e) {
            log.error(
                "Failed to sync review comments: repoName={}, prNumber={}",
                safeNameWithOwner,
                pullRequest.getNumber(),
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
    private int processThread(GHPullRequestReviewThread graphQlThread, PullRequest pullRequest) {
        if (graphQlThread == null) {
            return 0;
        }

        GHPullRequestReviewCommentConnection commentsConnection = graphQlThread.getComments();
        if (commentsConnection == null || commentsConnection.getNodes() == null) {
            return 0;
        }

        // Handle nested pagination for thread comments
        var commentsPageInfo = commentsConnection.getPageInfo();
        if (commentsPageInfo != null && Boolean.TRUE.equals(commentsPageInfo.getHasNextPage())) {
            // Fetch all remaining comments using pagination
            fetchAllRemainingThreadComments(graphQlThread, commentsPageInfo.getEndCursor());
        }

        var graphQlComments = commentsConnection.getNodes();
        if (graphQlComments.isEmpty()) {
            return 0;
        }

        // Get the first comment to determine the thread ID (use its databaseId as thread ID)
        var firstComment = graphQlComments.get(0);
        Long threadId = extractDatabaseId(firstComment);
        if (threadId == null) {
            log.warn("Skipped thread with missing databaseId on first comment: threadId={}", graphQlThread.getId());
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
     * Fetches all remaining comments for a thread when the initial query hit the pagination limit.
     * This method modifies the graphQlThread's comments list in place by adding all fetched comments.
     *
     * @param graphQlThread the thread to fetch remaining comments for
     * @param startCursor   the cursor to start fetching from
     */
    private void fetchAllRemainingThreadComments(GHPullRequestReviewThread graphQlThread, String startCursor) {
        if (currentClient == null) {
            log.warn(
                "No client available for nested pagination, skipping remaining comments: threadId={}",
                graphQlThread.getId()
            );
            return;
        }

        String threadId = graphQlThread.getId();
        GHPullRequestReviewCommentConnection existingComments = graphQlThread.getComments();
        if (existingComments == null || existingComments.getNodes() == null) {
            return;
        }

        // Create a mutable list to collect all comments
        List<GHPullRequestReviewComment> allComments = new ArrayList<>(existingComments.getNodes());

        String cursor = startCursor;
        boolean hasMore = true;
        int fetchedPages = 0;

        while (hasMore) {
            fetchedPages++;
            if (fetchedPages > MAX_PAGINATION_PAGES) {
                log.warn(
                    "Reached maximum pagination limit for thread comments: threadId={}, limit={}",
                    threadId,
                    MAX_PAGINATION_PAGES
                );
                break;
            }

            try {
                ClientGraphQlResponse response = currentClient
                    .documentName(GET_THREAD_COMMENTS_DOCUMENT)
                    .variable("threadId", threadId)
                    .variable("first", LARGE_PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(GRAPHQL_TIMEOUT);

                if (response == null || !response.isValid()) {
                    log.warn(
                        "Invalid GraphQL response for thread comments: threadId={}, errors={}",
                        threadId,
                        response != null ? response.getErrors() : "null"
                    );
                    break;
                }

                GHPullRequestReviewCommentConnection commentsConnection = response
                    .field("node.comments")
                    .toEntity(GHPullRequestReviewCommentConnection.class);

                if (commentsConnection == null || commentsConnection.getNodes() == null) {
                    break;
                }

                allComments.addAll(commentsConnection.getNodes());

                GHPageInfo pageInfo = commentsConnection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
            } catch (Exception e) {
                log.error("Failed to fetch thread comments: threadId={}", threadId, e);
                break;
            }
        }

        // Update the thread's comments with the complete list
        existingComments.setNodes(allComments);

        if (fetchedPages > 0) {
            log.debug(
                "Fetched additional thread comments: threadId={}, pageCount={}, totalComments={}",
                threadId,
                fetchedPages,
                allComments.size()
            );
        }
    }

    /**
     * Gets or creates a thread based on GraphQL data.
     */
    private PullRequestReviewThread getOrCreateThread(
        Long threadId,
        GHPullRequestReviewThread graphQlThread,
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

                // Map resolved state and resolvedBy user
                if (graphQlThread.getIsResolved()) {
                    thread.setState(PullRequestReviewThread.State.RESOLVED);
                    // Set resolvedBy user if available
                    GHUser graphQlResolvedBy = graphQlThread.getResolvedBy();
                    if (graphQlResolvedBy != null) {
                        GitHubUserDTO resolvedByDto = convertGraphQlUserToDto(graphQlResolvedBy);
                        de.tum.in.www1.hephaestus.gitprovider.user.User resolvedBy = userProcessor.ensureExists(
                            resolvedByDto
                        );
                        thread.setResolvedBy(resolvedBy);
                    }
                } else {
                    thread.setState(PullRequestReviewThread.State.UNRESOLVED);
                }

                return threadRepository.save(thread);
            });
    }

    /**
     * Converts a GraphQL GHUser to a GitHubUserDTO.
     */
    private GitHubUserDTO convertGraphQlUserToDto(GHUser graphQlUser) {
        if (graphQlUser == null) {
            return null;
        }
        return new GitHubUserDTO(
            null, // nodeId
            graphQlUser.getDatabaseId() != null ? graphQlUser.getDatabaseId().longValue() : null,
            graphQlUser.getLogin(),
            graphQlUser.getAvatarUrl() != null ? graphQlUser.getAvatarUrl().toString() : null,
            null, // htmlUrl
            graphQlUser.getName(),
            graphQlUser.getEmail()
        );
    }

    /**
     * Extracts the database ID from a GraphQL comment.
     * Uses fullDatabaseId if available, otherwise falls back to deprecated databaseId.
     */
    private Long extractDatabaseId(GHPullRequestReviewComment graphQlComment) {
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
     * Converts a GraphQL GHPullRequestReviewComment to a GitHubReviewCommentDTO.
     *
     * @param graphQlComment the GraphQL review comment
     * @param thread         the thread this comment belongs to
     * @return the DTO for processing, or null if databaseId is missing
     */
    private GitHubReviewCommentDTO convertToDTO(
        GHPullRequestReviewComment graphQlComment,
        PullRequestReviewThread thread
    ) {
        if (graphQlComment == null) {
            return null;
        }

        Long databaseId = extractDatabaseId(graphQlComment);
        if (databaseId == null) {
            log.warn("Skipped comment with missing databaseId: nodeId={}", graphQlComment.getId());
            return null;
        }

        // Convert author
        GitHubUserDTO author = null;
        GHActor graphQlAuthor = graphQlComment.getAuthor();
        if (graphQlAuthor instanceof GHUser graphQlUser) {
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

        // Get review ID if present - prefer fullDatabaseId over deprecated databaseId
        Long reviewId = null;
        if (graphQlComment.getPullRequestReview() != null) {
            BigInteger reviewFullDbId = graphQlComment.getPullRequestReview().getFullDatabaseId();
            if (reviewFullDbId != null) {
                reviewId = reviewFullDbId.longValue();
            } else {
                Integer reviewDbId = graphQlComment.getPullRequestReview().getDatabaseId();
                if (reviewDbId != null) {
                    reviewId = reviewDbId.longValue();
                }
            }
        }

        // Get reply-to ID if present - prefer fullDatabaseId over deprecated databaseId
        Long inReplyToId = null;
        if (graphQlComment.getReplyTo() != null) {
            BigInteger replyToFullDbId = graphQlComment.getReplyTo().getFullDatabaseId();
            if (replyToFullDbId != null) {
                inReplyToId = replyToFullDbId.longValue();
            } else {
                Integer replyToDbId = graphQlComment.getReplyTo().getDatabaseId();
                if (replyToDbId != null) {
                    inReplyToId = replyToDbId.longValue();
                }
            }
        }

        // GraphQL comment doesn't have direct side field, use thread's side
        String side = (thread != null && thread.getSide() != null) ? thread.getSide().name() : null;

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
            inReplyToId // inReplyToId
        );
    }

    /**
     * Converts a GraphQL GHCommentAuthorAssociation to its string representation.
     *
     * @param association the GraphQL author association
     * @return the string representation
     */
    private String convertAuthorAssociation(GHCommentAuthorAssociation association) {
        if (association == null) {
            return "NONE";
        }
        return association.name();
    }

    /**
     * Maps a GraphQL GHDiffSide to the entity Side enum.
     */
    private PullRequestReviewComment.Side mapDiffSide(GHDiffSide diffSide) {
        if (diffSide == null) {
            return null;
        }
        return switch (diffSide) {
            case LEFT -> PullRequestReviewComment.Side.LEFT;
            case RIGHT -> PullRequestReviewComment.Side.RIGHT;
        };
    }
}
