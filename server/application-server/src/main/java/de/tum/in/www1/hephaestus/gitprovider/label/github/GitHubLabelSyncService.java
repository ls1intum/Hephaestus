package de.tum.in.www1.hephaestus.gitprovider.label.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.*;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser.RepositoryOwnerAndName;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.LabelConnection;
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

    public GitHubLabelSyncService(
        RepositoryRepository repositoryRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubLabelProcessor labelProcessor,
        LabelRepository labelRepository
    ) {
        this.repositoryRepository = repositoryRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.labelProcessor = labelProcessor;
        this.labelRepository = labelRepository;
    }

    /**
     * Synchronizes all labels for a repository using GraphQL.
     *
     * @param workspaceId  the workspace ID for authentication
     * @param repositoryId the repository ID to sync labels for
     * @return number of labels synced
     */
    @Transactional
    public int syncLabelsForRepository(Long workspaceId, Long repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId).orElse(null);
        if (repository == null) {
            log.warn("Repository {} not found, cannot sync labels", repositoryId);
            return 0;
        }

        String safeNameWithOwner = sanitizeForLog(repository.getNameWithOwner());
        Optional<RepositoryOwnerAndName> parsedName = GitHubRepositoryNameParser.parse(repository.getNameWithOwner());
        if (parsedName.isEmpty()) {
            log.warn("Invalid repository name format: {}", safeNameWithOwner);
            return 0;
        }
        String owner = parsedName.get().owner();
        String name = parsedName.get().name();

        HttpGraphQlClient client = graphQlClientProvider.forWorkspace(workspaceId);
        ProcessingContext context = ProcessingContext.forSync(workspaceId, repository);

        try {
            int totalSynced = 0;
            String cursor = null;
            boolean hasNextPage = true;
            Set<String> syncedNodeIds = new HashSet<>();
            int pageCount = 0;

            while (hasNextPage) {
                pageCount++;
                if (pageCount > MAX_PAGINATION_PAGES) {
                    log.warn(
                        "Reached maximum pagination limit ({}) for repository {}, stopping",
                        MAX_PAGINATION_PAGES,
                        safeNameWithOwner
                    );
                    break;
                }

                LabelConnection response = client
                    .documentName(GET_LABELS_DOCUMENT)
                    .variable("owner", owner)
                    .variable("name", name)
                    .variable("first", LARGE_PAGE_SIZE)
                    .variable("after", cursor)
                    .retrieve("repository.labels")
                    .toEntity(LabelConnection.class)
                    .block(GRAPHQL_TIMEOUT);

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
                "Synced {} labels for repository {} (removed {} stale)",
                totalSynced,
                safeNameWithOwner,
                removedCount
            );
            return totalSynced;
        } catch (Exception e) {
            log.error("Error syncing labels for repository {}: {}", safeNameWithOwner, e.getMessage(), e);
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
                log.debug("Removed stale label: {}", existingLabel.getName());
            }
        }

        return removedCount;
    }

    /**
     * Converts a GraphQL Label to a GitHubLabelDTO.
     * Note: GraphQL doesn't expose databaseId for labels, so id will be null.
     * The processor handles this by using name-based lookup as fallback.
     */
    private GitHubLabelDTO convertToDTO(de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Label graphQlLabel) {
        return GitHubLabelDTO.fromLabel(graphQlLabel);
    }
}
