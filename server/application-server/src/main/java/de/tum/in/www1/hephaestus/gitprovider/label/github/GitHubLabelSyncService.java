package de.tum.in.www1.hephaestus.gitprovider.label.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.LARGE_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.MAX_PAGINATION_PAGES;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.exception.InstallationNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.ClassificationResult;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser.RepositoryOwnerAndName;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncProperties;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHLabelConnection;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for synchronizing GitHub labels via GraphQL API.
 * <p>
 * This service fetches labels via GraphQL and delegates to {@link GitHubLabelProcessor}
 * for persistence, ensuring a single source of truth for label processing logic.
 */
@Service
public class GitHubLabelSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubLabelSyncService.class);
    private static final String GET_LABELS_DOCUMENT = "GetRepositoryLabels";

    private final RepositoryRepository repositoryRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubLabelProcessor labelProcessor;
    private final LabelRepository labelRepository;
    private final GitHubSyncProperties syncProperties;
    private final GitHubExceptionClassifier exceptionClassifier;

    public GitHubLabelSyncService(
        RepositoryRepository repositoryRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubLabelProcessor labelProcessor,
        LabelRepository labelRepository,
        GitHubSyncProperties syncProperties,
        GitHubExceptionClassifier exceptionClassifier
    ) {
        this.repositoryRepository = repositoryRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.labelProcessor = labelProcessor;
        this.labelRepository = labelRepository;
        this.syncProperties = syncProperties;
        this.exceptionClassifier = exceptionClassifier;
    }

    /**
     * Synchronizes all labels for a repository using GraphQL.
     *
     * @param scopeId  the scope ID for authentication
     * @param repositoryId the repository ID to sync labels for
     * @return number of labels synced
     */
    @Transactional
    public int syncLabelsForRepository(Long scopeId, Long repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId).orElse(null);
        if (repository == null) {
            log.debug("Skipped label sync: reason=repositoryNotFound, repoId={}", repositoryId);
            return 0;
        }

        String safeNameWithOwner = sanitizeForLog(repository.getNameWithOwner());
        Optional<RepositoryOwnerAndName> parsedName = GitHubRepositoryNameParser.parse(repository.getNameWithOwner());
        if (parsedName.isEmpty()) {
            log.warn("Skipped label sync: reason=invalidRepoNameFormat, repoName={}", safeNameWithOwner);
            return 0;
        }
        String owner = parsedName.get().owner();
        String name = parsedName.get().name();

        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);
        ProcessingContext context = ProcessingContext.forSync(scopeId, repository);

        try {
            int totalSynced = 0;
            String cursor = null;
            boolean hasNextPage = true;
            Set<String> syncedNodeIds = new HashSet<>();
            int pageCount = 0;

            while (hasNextPage) {
                pageCount++;
                if (pageCount >= MAX_PAGINATION_PAGES) {
                    log.warn(
                        "Reached maximum pagination limit for labels: repoName={}, limit={}",
                        safeNameWithOwner,
                        MAX_PAGINATION_PAGES
                    );
                    break;
                }

                ClientGraphQlResponse graphQlResponse = client
                    .documentName(GET_LABELS_DOCUMENT)
                    .variable("owner", owner)
                    .variable("name", name)
                    .variable("first", LARGE_PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(syncProperties.getGraphqlTimeout());

                if (graphQlResponse == null || !graphQlResponse.isValid()) {
                    log.warn(
                        "Received invalid GraphQL response: repoName={}, errors={}",
                        safeNameWithOwner,
                        graphQlResponse != null ? graphQlResponse.getErrors() : "null"
                    );
                    break;
                }

                // Track rate limit from response
                graphQlClientProvider.trackRateLimit(graphQlResponse);

                // Check if we should pause due to rate limiting
                if (graphQlClientProvider.isRateLimitCritical()) {
                    log.warn("Aborting label sync due to critical rate limit: repoName={}", safeNameWithOwner);
                    break;
                }

                GHLabelConnection response = graphQlResponse
                    .field("repository.labels")
                    .toEntity(GHLabelConnection.class);

                if (response == null || response.getNodes() == null) {
                    break;
                }

                for (var graphQlLabel : response.getNodes()) {
                    GitHubLabelDTO dto = convertToDTO(graphQlLabel);
                    Label label = labelProcessor.process(dto, repository, context);
                    if (label != null) {
                        totalSynced++;
                        syncedNodeIds.add(label.getName());
                    }
                }

                var pageInfo = response.getPageInfo();
                hasNextPage = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
            }

            // Remove stale labels (labels in DB that no longer exist on GitHub)
            int removedCount = removeStaleLabels(repositoryId, syncedNodeIds);

            log.info(
                "Completed label sync: repoName={}, labelCount={}, removedCount={}, scopeId={}",
                safeNameWithOwner,
                totalSynced,
                removedCount,
                scopeId
            );
            return totalSynced;
        } catch (InstallationNotFoundException e) {
            // Re-throw to abort the entire sync operation
            throw e;
        } catch (Exception e) {
            ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
            switch (classification.category()) {
                case RATE_LIMITED -> log.warn(
                    "Rate limited during label sync: repoName={}, scopeId={}, message={}",
                    safeNameWithOwner,
                    scopeId,
                    classification.message()
                );
                case NOT_FOUND -> log.warn(
                    "Resource not found during label sync: repoName={}, scopeId={}, message={}",
                    safeNameWithOwner,
                    scopeId,
                    classification.message()
                );
                case AUTH_ERROR -> {
                    log.error(
                        "Authentication error during label sync: repoName={}, scopeId={}, message={}",
                        safeNameWithOwner,
                        scopeId,
                        classification.message()
                    );
                    throw e;
                }
                case RETRYABLE -> log.warn(
                    "Retryable error during label sync: repoName={}, scopeId={}, message={}",
                    safeNameWithOwner,
                    scopeId,
                    classification.message()
                );
                default -> log.error(
                    "Unexpected error during label sync: repoName={}, scopeId={}, message={}",
                    safeNameWithOwner,
                    scopeId,
                    classification.message(),
                    e
                );
            }
            return 0;
        }
    }

    /**
     * Removes labels from the local database that no longer exist on GitHub.
     *
     * @param repositoryId the repository ID
     * @param syncedNames the set of label names that were synced from GitHub
     * @return number of stale labels removed
     */
    private int removeStaleLabels(Long repositoryId, Set<String> syncedNames) {
        List<Label> existingLabels = labelRepository.findAllByRepository_Id(repositoryId);
        int removedCount = 0;

        for (Label existingLabel : existingLabels) {
            // If label name was not in the sync results, it's stale
            if (!syncedNames.contains(existingLabel.getName())) {
                labelRepository.delete(existingLabel);
                removedCount++;
                log.debug(
                    "Removed stale label: labelName={}, repoId={}",
                    sanitizeForLog(existingLabel.getName()),
                    repositoryId
                );
            }
        }

        return removedCount;
    }

    /**
     * Converts a GraphQL GHLabel to a GitHubLabelDTO.
     * Note: GraphQL doesn't expose databaseId for labels, so id will be null.
     * The processor handles this by using name-based lookup as fallback.
     */
    private GitHubLabelDTO convertToDTO(
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHLabel graphQlLabel
    ) {
        return GitHubLabelDTO.fromLabel(graphQlLabel);
    }
}
