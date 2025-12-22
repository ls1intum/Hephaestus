package de.tum.in.www1.hephaestus.gitprovider.milestone.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.MilestoneConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.MilestoneState;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
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
 * Service for synchronizing GitHub milestones via GraphQL API.
 */
@Service
public class GitHubMilestoneSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubMilestoneSyncService.class);
    private static final int GRAPHQL_PAGE_SIZE = 100;
    private static final Duration GRAPHQL_TIMEOUT = Duration.ofSeconds(30);
    private static final String GET_MILESTONES_DOCUMENT = "GetRepositoryMilestones";

    private final MilestoneRepository milestoneRepository;
    private final RepositoryRepository repositoryRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;

    public GitHubMilestoneSyncService(
        MilestoneRepository milestoneRepository,
        RepositoryRepository repositoryRepository,
        GitHubGraphQlClientProvider graphQlClientProvider
    ) {
        this.milestoneRepository = milestoneRepository;
        this.repositoryRepository = repositoryRepository;
        this.graphQlClientProvider = graphQlClientProvider;
    }

    /**
     * Synchronizes all milestones for a repository using GraphQL.
     *
     * @param workspaceId  the workspace ID for authentication
     * @param repositoryId the repository ID to sync milestones for
     * @return number of milestones synced
     */
    @Transactional
    public int syncMilestonesForRepository(Long workspaceId, Long repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId).orElse(null);
        if (repository == null) {
            logger.warn("Repository {} not found, cannot sync milestones", repositoryId);
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
            Set<Integer> syncedNumbers = new HashSet<>();
            int totalSynced = 0;
            String cursor = null;
            boolean hasNextPage = true;

            while (hasNextPage) {
                MilestoneConnection response = client
                    .documentName(GET_MILESTONES_DOCUMENT)
                    .variable("owner", owner)
                    .variable("name", name)
                    .variable("first", GRAPHQL_PAGE_SIZE)
                    .variable("after", cursor)
                    .retrieve("repository.milestones")
                    .toEntity(MilestoneConnection.class)
                    .block(GRAPHQL_TIMEOUT);

                if (response == null || response.getNodes() == null) {
                    break;
                }

                for (var graphQlMilestone : response.getNodes()) {
                    syncMilestone(graphQlMilestone, repository);
                    syncedNumbers.add(graphQlMilestone.getNumber());
                    totalSynced++;
                }

                var pageInfo = response.getPageInfo();
                hasNextPage = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
            }

            // Remove milestones that no longer exist
            removeDeletedMilestones(repository.getId(), syncedNumbers);

            logger.info("Synced {} milestones for repository {}", totalSynced, repository.getNameWithOwner());
            return totalSynced;
        } catch (Exception e) {
            logger.error(
                "Error syncing milestones for repository {}: {}",
                repository.getNameWithOwner(),
                e.getMessage(),
                e
            );
            return 0;
        }
    }

    private void removeDeletedMilestones(Long repositoryId, Set<Integer> syncedNumbers) {
        List<Milestone> existing = milestoneRepository.findAllByRepository_Id(repositoryId);
        int removed = 0;
        for (Milestone milestone : existing) {
            if (!syncedNumbers.contains(milestone.getNumber())) {
                milestoneRepository.delete(milestone);
                removed++;
            }
        }
        if (removed > 0) {
            logger.info("Deleted {} stale milestones for repositoryId={}", removed, repositoryId);
        }
    }

    private void syncMilestone(
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Milestone graphQlMilestone,
        Repository repository
    ) {
        // Find by number within repository since GraphQL doesn't give us database_id for milestones
        Milestone milestone = milestoneRepository
            .findByNumberAndRepositoryId(graphQlMilestone.getNumber(), repository.getId())
            .orElseGet(Milestone::new);

        milestone.setNumber(graphQlMilestone.getNumber());
        milestone.setTitle(graphQlMilestone.getTitle());
        milestone.setDescription(graphQlMilestone.getDescription());
        milestone.setState(convertState(graphQlMilestone.getState()));
        if (graphQlMilestone.getUrl() != null) {
            milestone.setHtmlUrl(graphQlMilestone.getUrl().toString());
        }
        milestone.setRepository(repository);

        if (graphQlMilestone.getDueOn() != null) {
            milestone.setDueOn(graphQlMilestone.getDueOn().toInstant());
        }
        if (graphQlMilestone.getClosedAt() != null) {
            milestone.setClosedAt(graphQlMilestone.getClosedAt().toInstant());
        }
        if (graphQlMilestone.getCreatedAt() != null) {
            milestone.setCreatedAt(graphQlMilestone.getCreatedAt().toInstant());
        }
        if (graphQlMilestone.getUpdatedAt() != null) {
            milestone.setUpdatedAt(graphQlMilestone.getUpdatedAt().toInstant());
        }

        milestoneRepository.save(milestone);
    }

    private Milestone.State convertState(MilestoneState graphQlState) {
        if (graphQlState == null) {
            return Milestone.State.OPEN;
        }
        return switch (graphQlState) {
            case CLOSED -> Milestone.State.CLOSED;
            case OPEN -> Milestone.State.OPEN;
        };
    }

    /**
     * Deletes a milestone by ID.
     */
    @Transactional
    public void deleteMilestone(Long milestoneId) {
        milestoneRepository.deleteById(milestoneId);
    }
}
