package de.tum.in.www1.hephaestus.gitprovider.milestone.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.*;

import de.tum.in.www1.hephaestus.gitprovider.common.exception.InstallationNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser.RepositoryOwnerAndName;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHMilestone;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHMilestoneConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHMilestoneState;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto.GitHubMilestoneDTO;
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
 * Service for synchronizing GitHub milestones via GraphQL API.
 * <p>
 * This service fetches milestones via GraphQL and delegates to {@link GitHubMilestoneProcessor}
 * for persistence, ensuring a single source of truth for milestone processing logic.
 */
@Service
public class GitHubMilestoneSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubMilestoneSyncService.class);
    private static final String GET_MILESTONES_DOCUMENT = "GetRepositoryMilestones";

    private final MilestoneRepository milestoneRepository;
    private final RepositoryRepository repositoryRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubMilestoneProcessor milestoneProcessor;

    public GitHubMilestoneSyncService(
        MilestoneRepository milestoneRepository,
        RepositoryRepository repositoryRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubMilestoneProcessor milestoneProcessor
    ) {
        this.milestoneRepository = milestoneRepository;
        this.repositoryRepository = repositoryRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.milestoneProcessor = milestoneProcessor;
    }

    /**
     * Synchronizes all milestones for a repository using GraphQL.
     *
     * @param scopeId  the scope ID for authentication
     * @param repositoryId the repository ID to sync milestones for
     * @return number of milestones synced
     */
    @Transactional
    public int syncMilestonesForRepository(Long scopeId, Long repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId).orElse(null);
        if (repository == null) {
            log.debug("Skipped milestone sync: reason=repositoryNotFound, repoId={}", repositoryId);
            return 0;
        }

        String safeNameWithOwner = sanitizeForLog(repository.getNameWithOwner());
        Optional<RepositoryOwnerAndName> parsedName = GitHubRepositoryNameParser.parse(repository.getNameWithOwner());
        if (parsedName.isEmpty()) {
            log.warn("Skipped milestone sync: reason=invalidRepoNameFormat, repoName={}", safeNameWithOwner);
            return 0;
        }
        String owner = parsedName.get().owner();
        String name = parsedName.get().name();

        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);
        ProcessingContext context = ProcessingContext.forSync(scopeId, repository);

        try {
            Set<Integer> syncedNumbers = new HashSet<>();
            int totalSynced = 0;
            String cursor = null;
            boolean hasNextPage = true;
            int pageCount = 0;

            while (hasNextPage) {
                pageCount++;
                if (pageCount >= MAX_PAGINATION_PAGES) {
                    log.warn(
                        "Reached maximum pagination limit for milestones: repoName={}, limit={}",
                        safeNameWithOwner,
                        MAX_PAGINATION_PAGES
                    );
                    break;
                }

                GHMilestoneConnection response = client
                    .documentName(GET_MILESTONES_DOCUMENT)
                    .variable("owner", owner)
                    .variable("name", name)
                    .variable("first", LARGE_PAGE_SIZE)
                    .variable("after", cursor)
                    .retrieve("repository.milestones")
                    .toEntity(GHMilestoneConnection.class)
                    .block(GRAPHQL_TIMEOUT);

                if (response == null || response.getNodes() == null) {
                    break;
                }

                for (var graphQlMilestone : response.getNodes()) {
                    GitHubMilestoneDTO dto = convertToDTO(graphQlMilestone);
                    Milestone milestone = milestoneProcessor.process(dto, repository, null, context);
                    if (milestone != null) {
                        syncedNumbers.add(milestone.getNumber());
                        totalSynced++;
                    }
                }

                var pageInfo = response.getPageInfo();
                hasNextPage = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
            }

            // Remove milestones that no longer exist
            removeDeletedMilestones(repository.getId(), syncedNumbers, context);

            log.info("Completed milestone sync: repoName={}, milestoneCount={}, scopeId={}", safeNameWithOwner, totalSynced, scopeId);
            return totalSynced;
        } catch (InstallationNotFoundException e) {
            // Re-throw to abort the entire sync operation
            throw e;
        } catch (Exception e) {
            log.error("Failed to sync milestones: repoName={}, scopeId={}", safeNameWithOwner, scopeId, e);
            return 0;
        }
    }

    private void removeDeletedMilestones(Long repositoryId, Set<Integer> syncedNumbers, ProcessingContext context) {
        List<Milestone> existing = milestoneRepository.findAllByRepository_Id(repositoryId);
        int removed = 0;
        for (Milestone milestone : existing) {
            if (!syncedNumbers.contains(milestone.getNumber())) {
                milestoneProcessor.delete(milestone.getId(), context);
                removed++;
            }
        }
        if (removed > 0) {
            log.info("Removed stale milestones: removedCount={}, repoId={}", removed, repositoryId);
        }
    }

    /**
     * Converts a GraphQL GHMilestone to a GitHubMilestoneDTO.
     * Note: GraphQL doesn't expose databaseId for milestones, so id will be null.
     * The processor handles this by using number-based lookup as fallback.
     */
    private GitHubMilestoneDTO convertToDTO(GHMilestone graphQlMilestone) {
        return new GitHubMilestoneDTO(
            null, // id - GraphQL doesn't expose databaseId for milestones
            graphQlMilestone.getNumber(),
            graphQlMilestone.getTitle(),
            graphQlMilestone.getDescription(),
            convertState(graphQlMilestone.getState()).name().toLowerCase(),
            graphQlMilestone.getDueOn() != null ? graphQlMilestone.getDueOn().toInstant() : null,
            graphQlMilestone.getUrl() != null ? graphQlMilestone.getUrl().toString() : null,
            graphQlMilestone.getOpenIssueCount(),
            graphQlMilestone.getClosedIssueCount()
        );
    }

    private Milestone.State convertState(GHMilestoneState graphQlState) {
        if (graphQlState == null) {
            return Milestone.State.OPEN;
        }
        return switch (graphQlState) {
            case CLOSED -> Milestone.State.CLOSED;
            case OPEN -> Milestone.State.OPEN;
        };
    }
}
