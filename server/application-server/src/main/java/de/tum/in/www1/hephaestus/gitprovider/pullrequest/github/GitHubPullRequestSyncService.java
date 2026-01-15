package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.DEFAULT_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.GRAPHQL_TIMEOUT;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.MAX_PAGINATION_PAGES;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser.RepositoryOwnerAndName;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPullRequestConnection;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.EmbeddedReviewsDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.PullRequestWithReviews;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.GitHubPullRequestReviewProcessor;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.GitHubPullRequestReviewSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.dto.GitHubPullRequestReviewEventDTO.GitHubReviewDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for synchronizing GitHub pull requests via GraphQL API.
 * <p>
 * This service fetches PRs using typed GraphQL models and delegates persistence
 * to GitHubPullRequestProcessor. Reviews are fetched inline with PRs to avoid
 * N+1 query patterns - only PRs with more than 10 reviews require additional
 * API calls for pagination.
 */
@Service
public class GitHubPullRequestSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubPullRequestSyncService.class);
    private static final String QUERY_DOCUMENT = "GetRepositoryPullRequests";

    private final RepositoryRepository repositoryRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubPullRequestProcessor pullRequestProcessor;
    private final GitHubPullRequestReviewProcessor reviewProcessor;
    private final GitHubPullRequestReviewSyncService reviewSyncService;

    /**
     * Container for PRs that need additional review pagination.
     */
    private record PullRequestWithReviewCursor(PullRequest pullRequest, String reviewCursor) {}

    public GitHubPullRequestSyncService(
        RepositoryRepository repositoryRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubPullRequestProcessor pullRequestProcessor,
        GitHubPullRequestReviewProcessor reviewProcessor,
        GitHubPullRequestReviewSyncService reviewSyncService
    ) {
        this.repositoryRepository = repositoryRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.pullRequestProcessor = pullRequestProcessor;
        this.reviewProcessor = reviewProcessor;
        this.reviewSyncService = reviewSyncService;
    }

    /**
     * Synchronizes all pull requests for a repository.
     * <p>
     * Reviews are fetched inline with PRs (first 10 per PR) to eliminate N+1 queries.
     * Only PRs with more than 10 reviews require additional API calls for pagination.
     *
     * @param scopeId  the scope ID for authentication
     * @param repositoryId the repository ID to sync pull requests for
     * @return number of pull requests synced
     */
    @Transactional
    public int syncForRepository(Long scopeId, Long repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId).orElse(null);
        if (repository == null) {
            log.warn("Repository not found, skipping pull request sync: repoId={}", repositoryId);
            return 0;
        }

        String nameWithOwner = repository.getNameWithOwner();
        String safeNameWithOwner = sanitizeForLog(nameWithOwner);
        Optional<RepositoryOwnerAndName> parsedName = GitHubRepositoryNameParser.parse(nameWithOwner);
        if (parsedName.isEmpty()) {
            log.warn("Invalid repository name format, skipping pull request sync: repoName={}", safeNameWithOwner);
            return 0;
        }
        RepositoryOwnerAndName ownerAndName = parsedName.get();

        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);
        ProcessingContext context = ProcessingContext.forSync(scopeId, repository);

        int totalPRsSynced = 0;
        int totalReviewsSynced = 0;
        List<PullRequestWithReviewCursor> prsNeedingReviewPagination = new ArrayList<>();
        String cursor = null;
        boolean hasMore = true;
        int pageCount = 0;

        while (hasMore) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.warn(
                    "Reached maximum pagination limit for pull requests: repoName={}, limit={}",
                    safeNameWithOwner,
                    MAX_PAGINATION_PAGES
                );
                break;
            }

            try {
                ClientGraphQlResponse response = client
                    .documentName(QUERY_DOCUMENT)
                    .variable("owner", ownerAndName.owner())
                    .variable("name", ownerAndName.name())
                    .variable("first", DEFAULT_PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(GRAPHQL_TIMEOUT);

                if (response == null || !response.isValid()) {
                    log.warn(
                        "Invalid GraphQL response for pull requests: repoName={}, errors={}",
                        safeNameWithOwner,
                        response != null ? response.getErrors() : "null"
                    );
                    break;
                }

                GHPullRequestConnection connection = response
                    .field("repository.pullRequests")
                    .toEntity(GHPullRequestConnection.class);

                if (connection == null || connection.getNodes() == null || connection.getNodes().isEmpty()) {
                    break;
                }

                for (var graphQlPullRequest : connection.getNodes()) {
                    PullRequestWithReviews prWithReviews = PullRequestWithReviews.fromPullRequest(graphQlPullRequest);
                    if (prWithReviews == null || prWithReviews.pullRequest() == null) {
                        continue;
                    }

                    // Process the PR
                    PullRequest entity = pullRequestProcessor.process(prWithReviews.pullRequest(), context);
                    if (entity == null) {
                        continue;
                    }
                    totalPRsSynced++;

                    // Process embedded reviews
                    EmbeddedReviewsDTO embeddedReviews = prWithReviews.embeddedReviews();
                    for (GitHubReviewDTO reviewDTO : embeddedReviews.reviews()) {
                        if (reviewProcessor.process(reviewDTO, entity.getId()) != null) {
                            totalReviewsSynced++;
                        }
                    }

                    // Track PRs that need additional review pagination (with cursor for efficient continuation)
                    if (embeddedReviews.needsPagination()) {
                        prsNeedingReviewPagination.add(
                            new PullRequestWithReviewCursor(entity, embeddedReviews.endCursor())
                        );
                    }
                }

                GHPageInfo pageInfo = connection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
            } catch (Exception e) {
                log.error("Failed to sync pull requests: repoName={}", safeNameWithOwner, e);
                break;
            }
        }

        // Fetch remaining reviews for PRs with >10 reviews (using cursor for efficient continuation)
        if (!prsNeedingReviewPagination.isEmpty()) {
            log.debug(
                "Fetching additional reviews for pull requests with pagination: repoName={}, prCount={}",
                safeNameWithOwner,
                prsNeedingReviewPagination.size()
            );
            for (PullRequestWithReviewCursor prWithCursor : prsNeedingReviewPagination) {
                int additionalReviews = reviewSyncService.syncRemainingReviews(
                    scopeId,
                    prWithCursor.pullRequest(),
                    prWithCursor.reviewCursor()
                );
                totalReviewsSynced += additionalReviews;
            }
        }

        log.info(
            "Completed pull request sync: repoName={}, prCount={}, reviewCount={}, prsWithPagination={}",
            safeNameWithOwner,
            totalPRsSynced,
            totalReviewsSynced,
            prsNeedingReviewPagination.size()
        );
        return totalPRsSynced;
    }
}
