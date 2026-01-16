package de.tum.in.www1.hephaestus.gitprovider.issue.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.DEFAULT_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.GRAPHQL_TIMEOUT;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.MAX_PAGINATION_PAGES;

import de.tum.in.www1.hephaestus.gitprovider.common.exception.InstallationNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser.RepositoryOwnerAndName;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHIssueConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.GitHubIssueDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for synchronizing GitHub issues via GraphQL API.
 * <p>
 * Uses typed GraphQL models for type-safe deserialization and delegates
 * persistence to GitHubIssueProcessor.
 */
@Service
public class GitHubIssueSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubIssueSyncService.class);
    private static final String QUERY_DOCUMENT = "GetRepositoryIssues";

    private final RepositoryRepository repositoryRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubIssueProcessor issueProcessor;

    public GitHubIssueSyncService(
        RepositoryRepository repositoryRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubIssueProcessor issueProcessor
    ) {
        this.repositoryRepository = repositoryRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.issueProcessor = issueProcessor;
    }

    /**
     * Synchronizes all issues for a repository.
     *
     * @param scopeId  the scope ID for authentication
     * @param repositoryId the repository ID to sync issues for
     * @return number of issues synced
     */
    @Transactional
    public int syncForRepository(Long scopeId, Long repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId).orElse(null);
        if (repository == null) {
            log.debug("Skipped issue sync: reason=repositoryNotFound, repoId={}", repositoryId);
            return 0;
        }

        String nameWithOwner = repository.getNameWithOwner();
        String safeNameWithOwner = sanitizeForLog(nameWithOwner);
        Optional<RepositoryOwnerAndName> parsedName = GitHubRepositoryNameParser.parse(nameWithOwner);
        if (parsedName.isEmpty()) {
            log.warn("Skipped issue sync: reason=invalidRepoNameFormat, repoName={}", safeNameWithOwner);
            return 0;
        }
        RepositoryOwnerAndName ownerAndName = parsedName.get();

        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);
        ProcessingContext context = ProcessingContext.forSync(scopeId, repository);

        int totalSynced = 0;
        String cursor = null;
        boolean hasMore = true;
        int pageCount = 0;

        while (hasMore) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.warn(
                    "Reached maximum pagination limit for issue sync: repoName={}, limit={}",
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
                        "Received invalid GraphQL response: repoName={}, errors={}",
                        safeNameWithOwner,
                        response != null ? response.getErrors() : "null"
                    );
                    break;
                }

                GHIssueConnection connection = response.field("repository.issues").toEntity(GHIssueConnection.class);

                if (connection == null || connection.getNodes() == null || connection.getNodes().isEmpty()) {
                    break;
                }

                for (var graphQlIssue : connection.getNodes()) {
                    GitHubIssueDTO dto = GitHubIssueDTO.fromIssue(graphQlIssue);
                    if (dto != null) {
                        Issue entity = issueProcessor.process(dto, context);
                        if (entity != null) {
                            totalSynced++;
                        }
                    }
                }

                GHPageInfo pageInfo = connection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
            } catch (InstallationNotFoundException e) {
                // Re-throw to abort the entire sync operation
                throw e;
            } catch (Exception e) {
                log.error("Failed to sync issues: repoName={}", safeNameWithOwner, e);
                break;
            }
        }

        log.info("Completed issue sync: repoName={}, issueCount={}, scopeId={}", safeNameWithOwner, totalSynced, scopeId);
        return totalSynced;
    }
}
