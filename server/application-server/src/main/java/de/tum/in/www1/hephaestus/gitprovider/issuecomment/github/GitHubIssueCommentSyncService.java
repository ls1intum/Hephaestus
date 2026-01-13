package de.tum.in.www1.hephaestus.gitprovider.issuecomment.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlErrorUtils.isNotFoundError;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.DEFAULT_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.GRAPHQL_TIMEOUT;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.MAX_PAGINATION_PAGES;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser.RepositoryOwnerAndName;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHIssueCommentConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.github.dto.GitHubIssueCommentEventDTO.GitHubCommentDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
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
 * Service for synchronizing GitHub issue comments via GraphQL API.
 * <p>
 * Uses typed GraphQL models for type-safe deserialization and delegates
 * persistence to GitHubIssueCommentProcessor.
 */
@Service
public class GitHubIssueCommentSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubIssueCommentSyncService.class);
    private static final String QUERY_DOCUMENT = "GetIssueComments";

    private final RepositoryRepository repositoryRepository;
    private final IssueRepository issueRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubIssueCommentProcessor commentProcessor;

    public GitHubIssueCommentSyncService(
        RepositoryRepository repositoryRepository,
        IssueRepository issueRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubIssueCommentProcessor commentProcessor
    ) {
        this.repositoryRepository = repositoryRepository;
        this.issueRepository = issueRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.commentProcessor = commentProcessor;
    }

    /**
     * Synchronizes all comments for all issues in a repository.
     * <p>
     * Uses streaming to avoid loading all issues into memory at once.
     *
     * @param workspaceId  the workspace ID for authentication
     * @param repositoryId the repository ID to sync comments for
     * @return number of comments synced
     */
    @Transactional
    public int syncForRepository(Long workspaceId, Long repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId).orElse(null);
        if (repository == null) {
            log.warn("Repository {} not found, skipping comment sync", repositoryId);
            return 0;
        }

        AtomicInteger totalSynced = new AtomicInteger(0);
        AtomicInteger issueCount = new AtomicInteger(0);

        try (Stream<Issue> issueStream = issueRepository.streamAllByRepository_Id(repositoryId)) {
            issueStream.forEach(issue -> {
                totalSynced.addAndGet(syncForIssue(workspaceId, issue));
                issueCount.incrementAndGet();
            });
        }

        String safeNameWithOwner = sanitizeForLog(repository.getNameWithOwner());
        if (issueCount.get() == 0) {
            log.info("No issues found for {}, skipping comment sync", safeNameWithOwner);
            return 0;
        }

        log.info("Synced {} comments for {} issues in {}", totalSynced.get(), issueCount.get(), safeNameWithOwner);
        return totalSynced.get();
    }

    /**
     * Synchronizes all comments for a single issue.
     */
    @Transactional
    public int syncForIssue(Long workspaceId, Issue issue) {
        if (issue == null || issue.getRepository() == null) {
            log.warn("Issue or repository is null, skipping comment sync");
            return 0;
        }

        Repository repository = issue.getRepository();
        String nameWithOwner = repository.getNameWithOwner();
        String safeNameWithOwner = sanitizeForLog(nameWithOwner);
        Optional<RepositoryOwnerAndName> parsedName = GitHubRepositoryNameParser.parse(nameWithOwner);
        if (parsedName.isEmpty()) {
            log.warn("Invalid repository name format: {}", safeNameWithOwner);
            return 0;
        }
        RepositoryOwnerAndName ownerAndName = parsedName.get();

        HttpGraphQlClient client = graphQlClientProvider.forWorkspace(workspaceId);
        ProcessingContext context = ProcessingContext.forSync(workspaceId, repository);

        int totalSynced = 0;
        String cursor = null;
        boolean hasMore = true;
        int pageCount = 0;

        while (hasMore) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.warn(
                    "Reached maximum pagination limit ({}) for issue #{} in {}, stopping",
                    MAX_PAGINATION_PAGES,
                    issue.getNumber(),
                    safeNameWithOwner
                );
                break;
            }

            try {
                ClientGraphQlResponse response = client
                    .documentName(QUERY_DOCUMENT)
                    .variable("owner", ownerAndName.owner())
                    .variable("name", ownerAndName.name())
                    .variable("number", issue.getNumber())
                    .variable("first", DEFAULT_PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(GRAPHQL_TIMEOUT);

                if (response == null || !response.isValid()) {
                    // Check if this is a NOT_FOUND error (issue deleted from GitHub)
                    if (isNotFoundError(response, "repository.issue")) {
                        log.debug(
                            "Issue #{} in {} no longer exists on GitHub, skipping comment sync",
                            issue.getNumber(),
                            safeNameWithOwner
                        );
                        return 0;
                    }
                    log.warn("Invalid GraphQL response: {}", response != null ? response.getErrors() : "null");
                    break;
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
                        IssueComment entity = commentProcessor.process(dto, issue.getId(), context);
                        if (entity != null) {
                            totalSynced++;
                        }
                    }
                }

                GHPageInfo pageInfo = connection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
            } catch (FieldAccessException e) {
                // Check if this is a NOT_FOUND error (issue deleted from GitHub)
                if (isNotFoundError(e.getResponse(), "repository.issue")) {
                    // Log at DEBUG - deleted issues are expected during sync, not actionable
                    log.debug(
                        "Issue #{} in {} no longer exists on GitHub, skipping comment sync",
                        issue.getNumber(),
                        safeNameWithOwner
                    );
                    return 0;
                }
                log.error(
                    "Error syncing comments for issue #{} in {}: {}",
                    issue.getNumber(),
                    safeNameWithOwner,
                    e.getMessage(),
                    e
                );
                break;
            } catch (Exception e) {
                log.error(
                    "Error syncing comments for issue #{} in {}: {}",
                    issue.getNumber(),
                    safeNameWithOwner,
                    e.getMessage(),
                    e
                );
                break;
            }
        }

        log.debug("Synced {} comments for issue #{} in {}", totalSynced, issue.getNumber(), safeNameWithOwner);
        return totalSynced;
    }
}
