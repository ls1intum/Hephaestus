package de.tum.in.www1.hephaestus.gitprovider.issuecomment.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlErrorUtils.isNotFoundError;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.DEFAULT_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.JITTER_FACTOR;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.MAX_PAGINATION_PAGES;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.TRANSPORT_INITIAL_BACKOFF;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.TRANSPORT_MAX_BACKOFF;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.TRANSPORT_MAX_RETRIES;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.exception.InstallationNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.ClassificationResult;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlSyncHelper;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser.RepositoryOwnerAndName;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubTransportErrors;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHIssueCommentConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.github.dto.GitHubIssueCommentEventDTO.GitHubCommentDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.FieldAccessException;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Service for synchronizing GitHub issue comments via GraphQL API.
 * <p>
 * Uses typed GraphQL models for type-safe deserialization and delegates
 * persistence to GitHubIssueCommentProcessor.
 * <p>
 * <b>Note:</b> Most comments are fetched inline with issues via GetRepositoryIssues query.
 * This service is primarily used to:
 * <ul>
 *   <li>Fetch remaining comments for issues that have more than 10 (embedded limit)</li>
 *   <li>Handle standalone comment sync when issues aren't involved</li>
 * </ul>
 *
 * @see de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueSyncService
 */
@Service
public class GitHubIssueCommentSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubIssueCommentSyncService.class);
    private static final String QUERY_DOCUMENT = "GetIssueComments";

    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubIssueCommentProcessor commentProcessor;
    private final GitHubSyncProperties syncProperties;
    private final GitHubExceptionClassifier exceptionClassifier;
    private final GitHubGraphQlSyncHelper graphQlSyncHelper;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    public GitHubIssueCommentSyncService(
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubIssueCommentProcessor commentProcessor,
        GitHubSyncProperties syncProperties,
        GitHubExceptionClassifier exceptionClassifier,
        GitHubGraphQlSyncHelper graphQlSyncHelper
    ) {
        this.graphQlClientProvider = graphQlClientProvider;
        this.commentProcessor = commentProcessor;
        this.syncProperties = syncProperties;
        this.exceptionClassifier = exceptionClassifier;
        this.graphQlSyncHelper = graphQlSyncHelper;
    }

    /**
     * Synchronizes all comments for a single issue.
     *
     * @param scopeId the scope ID for authentication
     * @param issue the issue to sync comments for
     * @return number of comments synced
     */
    @Transactional
    public int syncForIssue(Long scopeId, Issue issue) {
        if (issue == null || issue.getRepository() == null) {
            log.warn(
                "Skipped comment sync: reason=issueOrRepositoryNull, issueId={}",
                issue != null ? issue.getId() : "null"
            );
            return 0;
        }

        Repository repository = issue.getRepository();
        String nameWithOwner = repository.getNameWithOwner();
        String safeNameWithOwner = sanitizeForLog(nameWithOwner);
        Optional<RepositoryOwnerAndName> parsedName = GitHubRepositoryNameParser.parse(nameWithOwner);
        if (parsedName.isEmpty()) {
            log.warn("Skipped comment sync: reason=invalidRepoNameFormat, repoName={}", safeNameWithOwner);
            return 0;
        }
        RepositoryOwnerAndName ownerAndName = parsedName.get();

        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);
        ProcessingContext context = ProcessingContext.forSync(scopeId, repository);

        int totalSynced = 0;
        String cursor = null;
        boolean hasMore = true;
        int pageCount = 0;
        int retryAttempt = 0;

        while (hasMore) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.warn(
                    "Reached maximum pagination limit for comment sync: repoName={}, issueNumber={}, limit={}",
                    safeNameWithOwner,
                    issue.getNumber(),
                    MAX_PAGINATION_PAGES
                );
                break;
            }

            try {
                final String currentCursor = cursor;
                final int currentPage = pageCount;
                ClientGraphQlResponse response = Mono.defer(() ->
                    client
                        .documentName(QUERY_DOCUMENT)
                        .variable("owner", ownerAndName.owner())
                        .variable("name", ownerAndName.name())
                        .variable("number", issue.getNumber())
                        .variable("first", DEFAULT_PAGE_SIZE)
                        .variable("after", currentCursor)
                        .execute()
                )
                    .retryWhen(
                        Retry.backoff(TRANSPORT_MAX_RETRIES, TRANSPORT_INITIAL_BACKOFF)
                            .maxBackoff(TRANSPORT_MAX_BACKOFF)
                            .jitter(JITTER_FACTOR)
                            .filter(GitHubTransportErrors::isTransportError)
                            .doBeforeRetry(signal ->
                                log.warn(
                                    "Retrying comment sync after transport error: repoName={}, issueNumber={}, page={}, attempt={}, error={}",
                                    safeNameWithOwner,
                                    issue.getNumber(),
                                    currentPage,
                                    signal.totalRetries() + 1,
                                    signal.failure().getMessage()
                                )
                            )
                    )
                    .block(syncProperties.graphqlTimeout());

                if (response == null || !response.isValid()) {
                    // Check if this is a NOT_FOUND error (issue deleted from GitHub)
                    if (isNotFoundError(response, "repository.issue")) {
                        log.debug(
                            "Skipped comment sync: reason=issueDeletedFromGitHub, repoName={}, issueNumber={}",
                            safeNameWithOwner,
                            issue.getNumber()
                        );
                        return 0;
                    }
                    ClassificationResult classification = graphQlSyncHelper.classifyGraphQlErrors(response);
                    if (classification != null) {
                        if (
                            graphQlSyncHelper.handleGraphQlClassification(
                                classification,
                                retryAttempt,
                                MAX_RETRY_ATTEMPTS,
                                "comment sync",
                                "issueNumber",
                                issue.getNumber(),
                                log
                            )
                        ) {
                            retryAttempt++;
                            continue;
                        }
                        break;
                    }
                    log.warn(
                        "Invalid GraphQL response for comment sync: repoName={}, issueNumber={}",
                        safeNameWithOwner,
                        issue.getNumber()
                    );
                    break;
                }

                // Track rate limit from response
                graphQlClientProvider.trackRateLimit(scopeId, response);

                // Check if we should pause due to rate limiting
                if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                    if (
                        !graphQlSyncHelper.waitForRateLimitIfNeeded(
                            scopeId,
                            "comment sync",
                            "issueNumber",
                            issue.getNumber(),
                            log
                        )
                    ) {
                        break;
                    }
                }

                GHIssueCommentConnection connection = response
                    .field("repository.issue.comments")
                    .toEntity(GHIssueCommentConnection.class);

                if (connection == null || connection.getNodes() == null || connection.getNodes().isEmpty()) {
                    break;
                }

                for (var graphQlComment : connection.getNodes()) {
                    GitHubCommentDTO dto = GitHubCommentDTO.fromIssueComment(graphQlComment);
                    if (dto != null) {
                        IssueComment entity = commentProcessor.process(dto, issue.getNumber(), context);
                        if (entity != null) {
                            totalSynced++;
                        }
                    }
                }

                GHPageInfo pageInfo = connection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
                retryAttempt = 0;
            } catch (InstallationNotFoundException e) {
                // Re-throw to abort the entire sync operation
                throw e;
            } catch (FieldAccessException e) {
                // Check if this is a NOT_FOUND error (issue deleted from GitHub)
                if (isNotFoundError(e.getResponse(), "repository.issue")) {
                    // Log at DEBUG - deleted issues are expected during sync, not actionable
                    log.debug(
                        "Skipped comment sync: reason=issueDeletedFromGitHub, repoName={}, issueNumber={}",
                        safeNameWithOwner,
                        issue.getNumber()
                    );
                    return 0;
                }
                log.error(
                    "Failed to sync comments: repoName={}, issueNumber={}",
                    safeNameWithOwner,
                    issue.getNumber(),
                    e
                );
                break;
            } catch (Exception e) {
                ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
                if (
                    !graphQlSyncHelper.handleGraphQlClassification(
                        classification,
                        retryAttempt,
                        MAX_RETRY_ATTEMPTS,
                        "comment sync",
                        "issueNumber",
                        issue.getNumber(),
                        log
                    )
                ) {
                    break;
                }
                retryAttempt++;
            }
        }

        log.debug(
            "Completed comment sync for issue: repoName={}, issueNumber={}, commentCount={}",
            safeNameWithOwner,
            issue.getNumber(),
            totalSynced
        );
        return totalSynced;
    }

    /**
     * Synchronizes remaining comments for an issue, starting from the given cursor.
     * <p>
     * This method is called by GitHubIssueSyncService when an issue has more than 10 comments
     * (the embedded limit in GetRepositoryIssues query). It continues pagination from where
     * the embedded comments left off, avoiding re-fetching already synced comments.
     *
     * @param scopeId the scope ID for authentication
     * @param issue the issue to fetch remaining comments for
     * @param startCursor the pagination cursor to start from (from embedded comments)
     * @return number of additional comments synced
     */
    @Transactional
    public int syncRemainingComments(Long scopeId, Issue issue, String startCursor) {
        if (issue == null || issue.getRepository() == null) {
            log.warn(
                "Skipped remaining comment sync: reason=issueOrRepositoryNull, issueId={}",
                issue != null ? issue.getId() : "null"
            );
            return 0;
        }

        Repository repository = issue.getRepository();
        String nameWithOwner = repository.getNameWithOwner();
        String safeNameWithOwner = sanitizeForLog(nameWithOwner);
        Optional<RepositoryOwnerAndName> parsedName = GitHubRepositoryNameParser.parse(nameWithOwner);
        if (parsedName.isEmpty()) {
            log.warn("Skipped remaining comment sync: reason=invalidRepoNameFormat, repoName={}", safeNameWithOwner);
            return 0;
        }
        RepositoryOwnerAndName ownerAndName = parsedName.get();

        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);
        ProcessingContext context = ProcessingContext.forSync(scopeId, repository);

        int totalSynced = 0;
        String cursor = startCursor;
        boolean hasMore = true;
        int pageCount = 0;
        int retryAttempt = 0;

        log.debug(
            "Starting remaining comment sync: repoName={}, issueNumber={}, startCursor={}",
            safeNameWithOwner,
            issue.getNumber(),
            startCursor != null ? startCursor.substring(0, Math.min(20, startCursor.length())) + "..." : "null"
        );

        while (hasMore) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.warn(
                    "Reached maximum pagination limit for remaining comment sync: repoName={}, issueNumber={}, limit={}",
                    safeNameWithOwner,
                    issue.getNumber(),
                    MAX_PAGINATION_PAGES
                );
                break;
            }

            try {
                final String currentCursor = cursor;
                final int currentPage = pageCount;
                ClientGraphQlResponse response = Mono.defer(() ->
                    client
                        .documentName(QUERY_DOCUMENT)
                        .variable("owner", ownerAndName.owner())
                        .variable("name", ownerAndName.name())
                        .variable("number", issue.getNumber())
                        .variable("first", DEFAULT_PAGE_SIZE)
                        .variable("after", currentCursor)
                        .execute()
                )
                    .retryWhen(
                        Retry.backoff(TRANSPORT_MAX_RETRIES, TRANSPORT_INITIAL_BACKOFF)
                            .maxBackoff(TRANSPORT_MAX_BACKOFF)
                            .jitter(JITTER_FACTOR)
                            .filter(GitHubTransportErrors::isTransportError)
                            .doBeforeRetry(signal ->
                                log.warn(
                                    "Retrying remaining comment sync after transport error: repoName={}, issueNumber={}, page={}, attempt={}, error={}",
                                    safeNameWithOwner,
                                    issue.getNumber(),
                                    currentPage,
                                    signal.totalRetries() + 1,
                                    signal.failure().getMessage()
                                )
                            )
                    )
                    .block(syncProperties.graphqlTimeout());

                if (response == null || !response.isValid()) {
                    if (isNotFoundError(response, "repository.issue")) {
                        log.debug(
                            "Skipped remaining comment sync: reason=issueDeletedFromGitHub, repoName={}, issueNumber={}",
                            safeNameWithOwner,
                            issue.getNumber()
                        );
                        return 0;
                    }
                    ClassificationResult classification = graphQlSyncHelper.classifyGraphQlErrors(response);
                    if (classification != null) {
                        if (
                            graphQlSyncHelper.handleGraphQlClassification(
                                classification,
                                retryAttempt,
                                MAX_RETRY_ATTEMPTS,
                                "remaining comment sync",
                                "issueNumber",
                                issue.getNumber(),
                                log
                            )
                        ) {
                            retryAttempt++;
                            continue;
                        }
                        break;
                    }
                    log.warn(
                        "Invalid GraphQL response for remaining comment sync: repoName={}, issueNumber={}",
                        safeNameWithOwner,
                        issue.getNumber()
                    );
                    break;
                }

                // Track rate limit from response
                graphQlClientProvider.trackRateLimit(scopeId, response);

                // Check if we should pause due to rate limiting
                if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                    if (
                        !graphQlSyncHelper.waitForRateLimitIfNeeded(
                            scopeId,
                            "remaining comment sync",
                            "issueNumber",
                            issue.getNumber(),
                            log
                        )
                    ) {
                        break;
                    }
                }

                GHIssueCommentConnection connection = response
                    .field("repository.issue.comments")
                    .toEntity(GHIssueCommentConnection.class);

                if (connection == null || connection.getNodes() == null || connection.getNodes().isEmpty()) {
                    break;
                }

                for (var graphQlComment : connection.getNodes()) {
                    GitHubCommentDTO dto = GitHubCommentDTO.fromIssueComment(graphQlComment);
                    if (dto != null) {
                        IssueComment entity = commentProcessor.process(dto, issue.getNumber(), context);
                        if (entity != null) {
                            totalSynced++;
                        }
                    }
                }

                GHPageInfo pageInfo = connection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
                retryAttempt = 0;
            } catch (InstallationNotFoundException e) {
                throw e;
            } catch (FieldAccessException e) {
                if (isNotFoundError(e.getResponse(), "repository.issue")) {
                    log.debug(
                        "Skipped remaining comment sync: reason=issueDeletedFromGitHub, repoName={}, issueNumber={}",
                        safeNameWithOwner,
                        issue.getNumber()
                    );
                    return 0;
                }
                log.error(
                    "Failed to sync remaining comments: repoName={}, issueNumber={}",
                    safeNameWithOwner,
                    issue.getNumber(),
                    e
                );
                break;
            } catch (Exception e) {
                ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
                if (
                    !graphQlSyncHelper.handleGraphQlClassification(
                        classification,
                        retryAttempt,
                        MAX_RETRY_ATTEMPTS,
                        "remaining comment sync",
                        "issueNumber",
                        issue.getNumber(),
                        log
                    )
                ) {
                    break;
                }
                retryAttempt++;
            }
        }

        log.debug(
            "Completed remaining comment sync: repoName={}, issueNumber={}, additionalComments={}",
            safeNameWithOwner,
            issue.getNumber(),
            totalSynced
        );
        return totalSynced;
    }
}
