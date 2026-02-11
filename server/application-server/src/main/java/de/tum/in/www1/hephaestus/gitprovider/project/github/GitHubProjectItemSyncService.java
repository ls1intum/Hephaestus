package de.tum.in.www1.hephaestus.gitprovider.project.github;

import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.DEFAULT_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.JITTER_FACTOR;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.MAX_PAGINATION_PAGES;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.TRANSPORT_INITIAL_BACKOFF;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.TRANSPORT_MAX_BACKOFF;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.TRANSPORT_MAX_RETRIES;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.exception.InstallationNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.ClassificationResult;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubTransportErrors;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2Item;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemConnection;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.EmbeddedProjectItemsDTO;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.EmbeddedProjectItemsDTO.EmbeddedProjectItem;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.EmbeddedProjectItemsDTO.EmbeddedProjectReference;
import de.tum.in.www1.hephaestus.gitprovider.project.Project;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectItemDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Service for synchronizing GitHub project items from the issue/PR side.
 * <p>
 * This service processes embedded project items that are fetched inline with issues/PRs
 * and handles follow-up pagination for issues/PRs in many projects.
 * <p>
 * <h2>Architecture</h2>
 * Project items are synced FROM the issue/PR side rather than from the project side.
 * This design enables:
 * <ul>
 *   <li>Historical backfill: Items naturally backfilled with their parent issues/PRs</li>
 *   <li>Efficient sync: `filterBy.since` on issues propagates to item updates</li>
 *   <li>Proper pagination: Uses issue's `projectItems(first:, after:)` connection</li>
 * </ul>
 * <p>
 * <b>Note:</b> Draft Issues have no parent Issue and must still be synced from the project side.
 *
 * @see GitHubProjectSyncService#syncProjectItems(Long, Project)
 */
@Service
public class GitHubProjectItemSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubProjectItemSyncService.class);
    private static final String ISSUE_PROJECT_ITEMS_QUERY = "GetIssueProjectItems";
    private static final String PR_PROJECT_ITEMS_QUERY = "GetPullRequestProjectItems";

    private final ProjectRepository projectRepository;
    private final GitHubProjectItemProcessor projectItemProcessor;
    private final GitHubProjectItemFieldValueSyncService fieldValueSyncService;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubSyncProperties syncProperties;
    private final GitHubExceptionClassifier exceptionClassifier;
    private final TransactionTemplate transactionTemplate;

    public GitHubProjectItemSyncService(
        ProjectRepository projectRepository,
        GitHubProjectItemProcessor projectItemProcessor,
        GitHubProjectItemFieldValueSyncService fieldValueSyncService,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubSyncProperties syncProperties,
        GitHubExceptionClassifier exceptionClassifier,
        TransactionTemplate transactionTemplate
    ) {
        this.projectRepository = projectRepository;
        this.projectItemProcessor = projectItemProcessor;
        this.fieldValueSyncService = fieldValueSyncService;
        this.graphQlClientProvider = graphQlClientProvider;
        this.syncProperties = syncProperties;
        this.exceptionClassifier = exceptionClassifier;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Processes embedded project items from an issue/PR sync.
     * <p>
     * For each embedded item, looks up the project by node ID and creates the item link.
     * Items referencing projects not yet synced are skipped with a debug log.
     * <p>
     * The {@code parentIssueId} is critical: embedded project items are fetched inline with
     * their parent issue/PR, so the GraphQL query does NOT include the {@code content} block
     * (which would redundantly return the parent's own ID). Instead, we propagate the parent's
     * database ID so the processor can set both {@code issue_id} and {@code content_database_id}.
     *
     * @param embeddedItems the embedded project items from the issue query
     * @param context the processing context
     * @param parentIssueId the database ID of the parent issue/PR that owns these project items
     * @return number of items successfully processed
     */
    @Transactional
    public int processEmbeddedItems(
        EmbeddedProjectItemsDTO embeddedItems,
        ProcessingContext context,
        Long parentIssueId
    ) {
        if (embeddedItems == null || embeddedItems.items().isEmpty()) {
            return 0;
        }

        int processed = 0;
        for (EmbeddedProjectItem embeddedItem : embeddedItems.items()) {
            if (processEmbeddedItem(embeddedItem, context, parentIssueId)) {
                processed++;
            }
        }

        return processed;
    }

    /**
     * Synchronizes remaining project items for an issue/PR, starting from the given cursor.
     * <p>
     * Called when an issue/PR has more than 5 project items (the embedded limit).
     * Continues pagination from where the embedded items left off.
     *
     * @param scopeId the scope ID for authentication
     * @param issueNodeId the GitHub GraphQL node ID of the issue/PR
     * @param isPullRequest true if this is a pull request, false for a regular issue
     * @param repository the repository containing the issue/PR
     * @param startCursor the pagination cursor to start from
     * @param parentIssueId the database ID of the parent issue/PR that owns these project items
     * @return number of additional items synced
     */
    public int syncRemainingProjectItems(
        Long scopeId,
        String issueNodeId,
        boolean isPullRequest,
        Repository repository,
        String startCursor,
        Long parentIssueId
    ) {
        if (issueNodeId == null || issueNodeId.isBlank()) {
            log.warn("Skipped project item pagination: reason=missingNodeId");
            return 0;
        }

        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);

        int totalSynced = 0;
        String cursor = startCursor;
        boolean hasMore = true;
        int pageCount = 0;

        // Determine query based on issue type (PR vs Issue)
        String queryDocument = isPullRequest ? PR_PROJECT_ITEMS_QUERY : ISSUE_PROJECT_ITEMS_QUERY;
        String variableName = isPullRequest ? "pullRequestId" : "issueId";

        log.debug(
            "Starting project item pagination: nodeId={}, isPR={}, startCursor={}...",
            issueNodeId,
            isPullRequest,
            startCursor != null ? startCursor.substring(0, Math.min(20, startCursor.length())) : "null"
        );

        while (hasMore) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.warn(
                    "Reached maximum pagination limit for project items: nodeId={}, limit={}",
                    issueNodeId,
                    MAX_PAGINATION_PAGES
                );
                break;
            }

            try {
                final String currentCursor = cursor;
                final int currentPage = pageCount;

                ClientGraphQlResponse response = Mono.defer(() ->
                    client
                        .documentName(queryDocument)
                        .variable(variableName, issueNodeId)
                        .variable("first", DEFAULT_PAGE_SIZE)
                        .variable("after", currentCursor)
                        .execute()
                )
                    .retryWhen(
                        Retry.backoff(TRANSPORT_MAX_RETRIES, TRANSPORT_INITIAL_BACKOFF)
                            .maxBackoff(TRANSPORT_MAX_BACKOFF)
                            .jitter(JITTER_FACTOR)
                            .filter(GitHubTransportErrors::isTransportError)
                            .doBeforeRetry(signal ->
                                log.warn(
                                    "Retrying project item pagination after transport error: nodeId={}, page={}, attempt={}, error={}",
                                    issueNodeId,
                                    currentPage,
                                    signal.totalRetries() + 1,
                                    signal.failure().getMessage()
                                )
                            )
                    )
                    .block(syncProperties.graphqlTimeout());

                if (response == null || !response.isValid()) {
                    log.warn("Invalid GraphQL response for project item pagination: nodeId={}", issueNodeId);
                    break;
                }

                graphQlClientProvider.trackRateLimit(scopeId, response);

                if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                    log.warn("Aborting project item pagination due to rate limit: nodeId={}", issueNodeId);
                    break;
                }

                GHProjectV2ItemConnection connection = response
                    .field("node.projectItems")
                    .toEntity(GHProjectV2ItemConnection.class);

                if (connection == null || connection.getNodes() == null || connection.getNodes().isEmpty()) {
                    break;
                }

                // Process this page of items in its own transaction
                Integer pageSynced = transactionTemplate.execute(status -> {
                    ProcessingContext context = ProcessingContext.forSync(scopeId, repository);
                    int synced = 0;
                    for (GHProjectV2Item graphQlItem : connection.getNodes()) {
                        EmbeddedProjectItem embeddedItem = EmbeddedProjectItem.fromProjectV2Item(graphQlItem);
                        if (embeddedItem != null && processEmbeddedItem(embeddedItem, context, parentIssueId)) {
                            synced++;
                        }
                    }
                    return synced;
                });

                if (pageSynced != null) {
                    totalSynced += pageSynced;
                }

                var pageInfo = connection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
            } catch (InstallationNotFoundException e) {
                throw e;
            } catch (Exception e) {
                ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
                log.warn(
                    "Error during project item pagination: nodeId={}, category={}, message={}",
                    issueNodeId,
                    classification.category(),
                    classification.message()
                );
                break;
            }
        }

        log.debug("Completed project item pagination: nodeId={}, additionalItems={}", issueNodeId, totalSynced);
        return totalSynced;
    }

    /**
     * Processes a single embedded project item.
     * <p>
     * If the item's DTO has no {@code issueId} set (because the GraphQL query omitted
     * the {@code content} block), the {@code parentIssueId} is injected as both
     * {@code issueId} and {@code contentDatabaseId} via
     * {@link GitHubProjectItemDTO#withIssueId(Long)}.
     *
     * @param embeddedItem the embedded item with project reference
     * @param context the processing context
     * @param parentIssueId the database ID of the parent issue/PR
     * @return true if the item was successfully processed
     */
    private boolean processEmbeddedItem(
        EmbeddedProjectItem embeddedItem,
        ProcessingContext context,
        Long parentIssueId
    ) {
        if (embeddedItem == null || embeddedItem.item() == null) {
            return false;
        }

        Project project = resolveProject(embeddedItem.project());
        if (project == null) {
            log.debug(
                "Skipped project item: reason=projectNotSynced, projectNodeId={}, itemNodeId={}",
                embeddedItem.project() != null ? embeddedItem.project().nodeId() : "null",
                embeddedItem.item().nodeId()
            );
            return false;
        }

        try {
            GitHubProjectItemDTO itemDto = embeddedItem.item().withIssueId(parentIssueId);
            var result = projectItemProcessor.process(itemDto, project, context);
            if (result == null) {
                return false;
            }

            // Process inline field values for this item
            fieldValueSyncService.processFieldValues(
                result.getId(),
                itemDto.fieldValues(),
                itemDto.fieldValuesTruncated(),
                itemDto.fieldValuesEndCursor()
            );

            return true;
        } catch (Exception e) {
            log.warn(
                "Failed to process embedded project item: projectId={}, itemNodeId={}, error={}",
                project.getId(),
                embeddedItem.item().nodeId(),
                e.getMessage(),
                e
            );
            return false;
        }
    }

    /**
     * Resolves a Project entity from the embedded project reference.
     * <p>
     * Projects should be synced before issues/PRs in the sync order, so the project
     * should already exist. If it doesn't, we return null and the item is skipped.
     *
     * @param projectRef the embedded project reference (may be null)
     * @return the Project entity if found, null otherwise
     */
    @Nullable
    private Project resolveProject(@Nullable EmbeddedProjectReference projectRef) {
        if (projectRef == null || projectRef.nodeId() == null) {
            return null;
        }

        return projectRepository.findByNodeId(projectRef.nodeId()).orElse(null);
    }
}
