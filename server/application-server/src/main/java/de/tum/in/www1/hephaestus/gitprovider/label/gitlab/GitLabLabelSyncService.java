package de.tum.in.www1.hephaestus.gitprovider.label.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncException;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.gitlab.dto.GitLabLabelDTO;
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
 * Service for synchronizing GitLab labels via GraphQL API.
 * <p>
 * Fetches all project and inherited group labels using the
 * {@code GetProjectLabels} query with {@code includeAncestorGroups: true}.
 * <p>
 * After a complete sync, stale labels (present in DB but not on GitLab) are removed.
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabLabelSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitLabLabelSyncService.class);
    private static final String GET_PROJECT_LABELS_DOCUMENT = "GetProjectLabels";

    private final GitLabGraphQlClientProvider graphQlClientProvider;
    private final GitLabLabelProcessor labelProcessor;
    private final LabelRepository labelRepository;
    private final GitLabProperties gitLabProperties;

    public GitLabLabelSyncService(
        GitLabGraphQlClientProvider graphQlClientProvider,
        GitLabLabelProcessor labelProcessor,
        LabelRepository labelRepository,
        GitLabProperties gitLabProperties
    ) {
        this.graphQlClientProvider = graphQlClientProvider;
        this.labelProcessor = labelProcessor;
        this.labelRepository = labelRepository;
        this.gitLabProperties = gitLabProperties;
    }

    /**
     * Synchronizes all labels for a repository using GraphQL.
     *
     * @param scopeId    the workspace/scope ID for authentication
     * @param repository the repository to sync labels for
     * @return sync result with status and count
     */
    public SyncResult syncLabelsForRepository(Long scopeId, Repository repository) {
        String projectPath = repository.getNameWithOwner();
        String safeProjectPath = sanitizeForLog(projectPath);

        log.info("Starting label sync: scopeId={}, projectPath={}", scopeId, safeProjectPath);

        int totalSynced = 0;
        Set<String> syncedNames = new HashSet<>();
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
                    log.warn("Label sync interrupted: scopeId={}, projectPath={}", scopeId, safeProjectPath);
                    rateLimitAborted = true;
                    break;
                }

                int remaining = graphQlClientProvider.getRateLimitRemaining(scopeId);
                int pageSize = GitLabSyncConstants.adaptPageSize(GitLabSyncConstants.LARGE_PAGE_SIZE, remaining);

                HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);

                ClientGraphQlResponse response = client
                    .documentName(GET_PROJECT_LABELS_DOCUMENT)
                    .variable("fullPath", projectPath)
                    .variable("first", pageSize)
                    .variable("after", cursor)
                    .execute()
                    .block(gitLabProperties.graphqlTimeout());

                if (response == null || !response.isValid()) {
                    log.warn(
                        "Failed to fetch labels: scopeId={}, projectPath={}, errors={}",
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
                List<Map<String, Object>> nodes = (List) response.field("project.labels.nodes").toEntityList(Map.class);

                if (nodes == null || nodes.isEmpty()) {
                    break;
                }

                ProcessingContext context = ProcessingContext.forSync(scopeId, repository);
                for (Map<String, Object> node : nodes) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typedNode = node;
                    GitLabLabelDTO dto = GitLabLabelDTO.fromGraphQlNode(typedNode);
                    if (dto != null) {
                        Label label = labelProcessor.process(dto, repository, context);
                        if (label != null) {
                            totalSynced++;
                            syncedNames.add(label.getName());
                        }
                    }
                }

                GitLabPageInfo pageInfo = response.field("project.labels.pageInfo").toEntity(GitLabPageInfo.class);
                cursor = pageInfo != null ? pageInfo.endCursor() : null;
                page++;

                if (pageInfo == null || !pageInfo.hasNextPage()) {
                    break;
                }
            } while (true);
        } catch (Exception e) {
            log.error("Error during label sync: scopeId={}, projectPath={}", scopeId, safeProjectPath, e);
            errorAborted = true;
        }

        boolean syncComplete = !rateLimitAborted && !errorAborted && page < GitLabSyncConstants.MAX_PAGINATION_PAGES;

        // Remove stale labels only if sync completed fully
        if (syncComplete) {
            int removedCount = removeStaleLabels(
                repository.getId(),
                syncedNames,
                ProcessingContext.forSync(scopeId, repository)
            );
            if (removedCount > 0) {
                log.info("Removed stale labels: removedCount={}, projectPath={}", removedCount, safeProjectPath);
            }
        } else {
            log.warn(
                "Skipped stale label removal: reason=incompleteSync, projectPath={}, pagesProcessed={}",
                safeProjectPath,
                page
            );
        }

        log.info(
            "Completed label sync: projectPath={}, labelCount={}, complete={}, scopeId={}",
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

    private int removeStaleLabels(Long repositoryId, Set<String> syncedNames, ProcessingContext context) {
        List<Label> existingLabels = labelRepository.findAllByRepository_Id(repositoryId);
        int removedCount = 0;
        for (Label existingLabel : existingLabels) {
            if (!syncedNames.contains(existingLabel.getName())) {
                labelProcessor.delete(existingLabel.getId(), context);
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
}
