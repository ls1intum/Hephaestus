package de.tum.in.www1.hephaestus.gitprovider.label.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.LabelConnection;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for synchronizing GitHub labels via GraphQL API.
 */
@Service
public class GitHubLabelSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubLabelSyncService.class);
    private static final int GRAPHQL_PAGE_SIZE = 100;
    private static final Duration GRAPHQL_TIMEOUT = Duration.ofSeconds(30);
    private static final String GET_LABELS_DOCUMENT = "GetRepositoryLabels";

    private final LabelRepository labelRepository;
    private final RepositoryRepository repositoryRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;

    public GitHubLabelSyncService(
        LabelRepository labelRepository,
        RepositoryRepository repositoryRepository,
        GitHubGraphQlClientProvider graphQlClientProvider
    ) {
        this.labelRepository = labelRepository;
        this.repositoryRepository = repositoryRepository;
        this.graphQlClientProvider = graphQlClientProvider;
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
            logger.warn("Repository {} not found, cannot sync labels", repositoryId);
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
            Set<Long> syncedIds = new HashSet<>();
            int totalSynced = 0;
            String cursor = null;
            boolean hasNextPage = true;

            while (hasNextPage) {
                LabelConnection response = client
                    .documentName(GET_LABELS_DOCUMENT)
                    .variable("owner", owner)
                    .variable("name", name)
                    .variable("first", GRAPHQL_PAGE_SIZE)
                    .variable("after", cursor)
                    .retrieve("repository.labels")
                    .toEntity(LabelConnection.class)
                    .block(GRAPHQL_TIMEOUT);

                if (response == null || response.getNodes() == null) {
                    break;
                }

                for (var graphQlLabel : response.getNodes()) {
                    syncLabel(graphQlLabel, repository);
                    // Labels use node ID (string) but our entity uses numeric ID
                    // We need to parse or handle this - check the ID type
                    if (graphQlLabel.getId() != null) {
                        try {
                            syncedIds.add(Long.parseLong(graphQlLabel.getId()));
                        } catch (NumberFormatException e) {
                            // GraphQL returns node_id, we need database_id
                            // For now, skip tracking - we'll handle deletion differently
                        }
                    }
                    totalSynced++;
                }

                var pageInfo = response.getPageInfo();
                hasNextPage = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
            }

            logger.info("Synced {} labels for repository {}", totalSynced, repository.getNameWithOwner());
            return totalSynced;
        } catch (Exception e) {
            logger.error(
                "Error syncing labels for repository {}: {}",
                repository.getNameWithOwner(),
                e.getMessage(),
                e
            );
            return 0;
        }
    }

    private void syncLabel(
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Label graphQlLabel,
        Repository repository
    ) {
        // GraphQL returns node_id as string, but our entity uses numeric database_id
        // The Label entity uses Long id, so we need the database_id
        // Unfortunately, the GitHub GraphQL schema doesn't expose databaseId for labels
        // We'll use the name as a unique key within the repository for now

        Label label = labelRepository
            .findByRepositoryIdAndName(repository.getId(), graphQlLabel.getName())
            .orElseGet(Label::new);

        label.setName(graphQlLabel.getName());
        label.setDescription(graphQlLabel.getDescription());
        label.setColor(graphQlLabel.getColor());
        label.setRepository(repository);

        labelRepository.save(label);
    }

    /**
     * Processes a label from a DTO (used by webhook handlers).
     *
     * @param dto     the label DTO from webhook payload
     * @param context the processing context
     * @return the synchronized local Label entity
     */
    @Transactional
    public Label processLabel(
        de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO dto,
        de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext context
    ) {
        if (dto == null || context == null) {
            return null;
        }

        var repository = context.repository();
        return labelRepository
            .findById(dto.id())
            .map(label -> {
                // Update existing label
                if (dto.name() != null) label.setName(dto.name());
                if (dto.color() != null) label.setColor(dto.color());
                return labelRepository.save(label);
            })
            .orElseGet(() -> {
                // Create new label
                var label = new Label();
                label.setId(dto.id());
                label.setName(dto.name());
                label.setColor(dto.color());
                label.setDescription(dto.description());
                if (repository != null) {
                    label.setRepository(repository);
                }
                return labelRepository.save(label);
            });
    }

    /**
     * Deletes a label by ID.
     */
    @Transactional
    public void deleteLabel(Long labelId) {
        labelRepository
            .findById(labelId)
            .ifPresent(label -> {
                label.removeAllIssues();
                label.removeAllTeams();
                labelRepository.delete(label);
            });
    }
}
