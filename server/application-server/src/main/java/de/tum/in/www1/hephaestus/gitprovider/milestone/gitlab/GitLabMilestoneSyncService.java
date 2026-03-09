package de.tum.in.www1.hephaestus.gitprovider.milestone.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncException;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.gitlab.dto.GitLabMilestoneDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncResult;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;

/**
 * Service for synchronizing GitLab milestones via GraphQL API.
 * <p>
 * Fetches all project and inherited group milestones using the
 * {@code GetProjectMilestones} query with {@code includeAncestors: true}.
 * <p>
 * After a complete sync, stale milestones (present in DB but not on GitLab) are removed.
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabMilestoneSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitLabMilestoneSyncService.class);
    private static final String GET_PROJECT_MILESTONES_DOCUMENT = "GetProjectMilestones";

    private final GitLabGraphQlClientProvider graphQlClientProvider;
    private final GitLabMilestoneProcessor milestoneProcessor;
    private final MilestoneRepository milestoneRepository;
    private final GitLabProperties gitLabProperties;

    public GitLabMilestoneSyncService(
        GitLabGraphQlClientProvider graphQlClientProvider,
        GitLabMilestoneProcessor milestoneProcessor,
        MilestoneRepository milestoneRepository,
        GitLabProperties gitLabProperties
    ) {
        this.graphQlClientProvider = graphQlClientProvider;
        this.milestoneProcessor = milestoneProcessor;
        this.milestoneRepository = milestoneRepository;
        this.gitLabProperties = gitLabProperties;
    }

    /**
     * Synchronizes all milestones for a repository using GraphQL.
     *
     * @param scopeId    the workspace/scope ID for authentication
     * @param repository the repository to sync milestones for
     * @return sync result with status and count
     */
    public SyncResult syncMilestonesForRepository(Long scopeId, Repository repository) {
        String projectPath = repository.getNameWithOwner();
        String safeProjectPath = sanitizeForLog(projectPath);

        log.info("Starting milestone sync: scopeId={}, projectPath={}", scopeId, safeProjectPath);

        int totalSynced = 0;
        Set<Integer> syncedNumbers = new HashSet<>();
        String cursor = null;
        int page = 0;
        boolean rateLimitAborted = false;
        boolean errorAborted = false;

        try {
            do {
                if (page >= GitLabSyncConstants.MAX_PAGINATION_PAGES) {
                    log.warn("Reached max pagination pages: scopeId={}, projectPath={}", scopeId, safeProjectPath);
                    break;
                }

                graphQlClientProvider.acquirePermission();

                try {
                    graphQlClientProvider.waitIfRateLimitLow(scopeId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Milestone sync interrupted: scopeId={}, projectPath={}", scopeId, safeProjectPath);
                    rateLimitAborted = true;
                    break;
                }

                int remaining = graphQlClientProvider.getRateLimitRemaining(scopeId);
                int pageSize = GitLabSyncConstants.adaptPageSize(GitLabSyncConstants.LARGE_PAGE_SIZE, remaining);

                HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);

                ClientGraphQlResponse response = client
                    .documentName(GET_PROJECT_MILESTONES_DOCUMENT)
                    .variable("fullPath", projectPath)
                    .variable("first", pageSize)
                    .variable("after", cursor)
                    .execute()
                    .block(gitLabProperties.graphqlTimeout());

                if (response == null || !response.isValid()) {
                    log.warn(
                        "Failed to fetch milestones: scopeId={}, projectPath={}, errors={}",
                        scopeId,
                        safeProjectPath,
                        response != null ? response.getErrors() : "null response"
                    );
                    graphQlClientProvider.recordFailure(new GitLabSyncException("Invalid GraphQL response"));
                    errorAborted = true;
                    break;
                }

                graphQlClientProvider.recordSuccess();

                @SuppressWarnings({ "unchecked", "rawtypes" })
                List<Map<String, Object>> nodes = (List) response
                    .field("project.milestones.nodes")
                    .toEntityList(Map.class);

                if (nodes == null || nodes.isEmpty()) {
                    break;
                }

                ProcessingContext context = ProcessingContext.forSync(scopeId, repository);
                for (Map<String, Object> node : nodes) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typedNode = node;
                    GitLabMilestoneDTO dto = GitLabMilestoneDTO.fromGraphQlNode(typedNode);
                    if (dto != null) {
                        Milestone milestone = milestoneProcessor.process(dto, repository, context);
                        if (milestone != null) {
                            totalSynced++;
                            syncedNumbers.add(milestone.getNumber());
                        }
                    }
                }

                GitLabPageInfo pageInfo = response.field("project.milestones.pageInfo").toEntity(GitLabPageInfo.class);
                cursor = pageInfo != null ? pageInfo.endCursor() : null;
                page++;

                if (pageInfo == null || !pageInfo.hasNextPage()) {
                    break;
                }
            } while (true);
        } catch (Exception e) {
            log.error("Error during milestone sync: scopeId={}, projectPath={}", scopeId, safeProjectPath, e);
            errorAborted = true;
        }

        boolean syncComplete = !rateLimitAborted && !errorAborted && page < GitLabSyncConstants.MAX_PAGINATION_PAGES;

        // Remove stale milestones only if sync completed fully
        if (syncComplete) {
            int removedCount = removeStaleMilestones(
                repository.getId(),
                syncedNumbers,
                ProcessingContext.forSync(scopeId, repository)
            );
            if (removedCount > 0) {
                log.info("Removed stale milestones: removedCount={}, projectPath={}", removedCount, safeProjectPath);
            }
        } else {
            log.warn(
                "Skipped stale milestone removal: reason=incompleteSync, projectPath={}, pagesProcessed={}",
                safeProjectPath,
                page
            );
        }

        log.info(
            "Completed milestone sync: projectPath={}, milestoneCount={}, complete={}, scopeId={}",
            safeProjectPath,
            totalSynced,
            syncComplete,
            scopeId
        );

        if (rateLimitAborted) {
            return SyncResult.abortedRateLimit(totalSynced);
        }
        if (errorAborted) {
            return SyncResult.abortedError(totalSynced);
        }
        return SyncResult.completed(totalSynced);
    }

    private int removeStaleMilestones(Long repositoryId, Set<Integer> syncedNumbers, ProcessingContext context) {
        List<Milestone> existing = milestoneRepository.findAllByRepository_Id(repositoryId);
        int removedCount = 0;
        for (Milestone milestone : existing) {
            if (!syncedNumbers.contains(milestone.getNumber())) {
                milestoneProcessor.delete(milestone.getId(), context);
                removedCount++;
            }
        }
        return removedCount;
    }
}
