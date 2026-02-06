package de.tum.in.www1.hephaestus.gitprovider.project.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.FIELD_VALUES_PAGINATION_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.LARGE_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.MAX_PAGINATION_PAGES;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.PROJECT_FIELD_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.PROJECT_ITEM_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.STATUS_UPDATE_PAGE_SIZE;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.exception.InstallationNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.github.ExponentialBackoff;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.Category;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.ClassificationResult;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.BackfillStateProvider;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2Connection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2FieldConfiguration;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2FieldConfigurationConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2Item;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldValueConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2StatusUpdate;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2StatusUpdateConnection;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.Project;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectField;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectFieldRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectFieldValueRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectItem;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectItemRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectStatusUpdateRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectDTO;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectFieldDTO;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectFieldValueDTO;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectItemDTO;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectStatusUpdateDTO;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncResult;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncSchedulerProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.GraphQlTransportException;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Service for synchronizing GitHub Projects V2 via GraphQL API.
 * <p>
 * Features: per-page transactions, cursor persistence for resumability, transport error retry,
 * decoupled phases (project list → items), client-side incremental sync, cost-optimized page sizes.
 * <p>
 * <h2>Architecture Note: Issue/PR Items vs Draft Issues</h2>
 * <p>
 * Project items linking to Issues and Pull Requests are synced <b>from the issue/PR side</b>
 * (embedded in issue/PR GraphQL queries), NOT from the project side. This enables:
 * <ul>
 *   <li>Historical backfill: Items naturally backfilled with their parent issues/PRs</li>
 *   <li>Efficient sync: {@code filterBy.since} on issues propagates to item updates</li>
 *   <li>Proper pagination: Uses issue's {@code projectItems(first:, after:)} connection</li>
 * </ul>
 * <p>
 * <b>Draft Issues</b> have no parent Issue/PR, so they must still be synced from the project side.
 * The {@link #syncDraftIssues} method handles this case.
 * <p>
 * <h2>Supported Owner Types</h2>
 * <p>
 * This sync service currently only supports <b>ORGANIZATION</b> owned projects.
 * <p>
 * <table border="1">
 *   <tr><th>Owner Type</th><th>Sync Support</th><th>Notes</th></tr>
 *   <tr>
 *     <td><b>ORGANIZATION</b></td>
 *     <td>Full</td>
 *     <td>Primary use case. Use {@link #syncProjectsForOrganization}.</td>
 *   </tr>
 *   <tr>
 *     <td><b>REPOSITORY</b></td>
 *     <td>Not implemented</td>
 *     <td>Would require a new syncProjectsForRepository method with appropriate GraphQL query.</td>
 *   </tr>
 *   <tr>
 *     <td><b>USER</b></td>
 *     <td>Not implemented</td>
 *     <td>Would require a new syncProjectsForUser method. User projects aren't linked to workspaces.</td>
 *   </tr>
 * </table>
 * <p>
 * <b>To add REPOSITORY support:</b> Create a new GraphQL query {@code GetRepositoryProjects} and
 * implement {@code syncProjectsForRepository(Long scopeId, String repositoryFullName)} following
 * the same pattern as organization sync.
 * <p>
 * <h2>Phase Tracking</h2>
 * <p>
 * The {@link #syncDraftIssues} method tracks three phases:
 * <ol>
 *   <li><b>Fields:</b> Project field definitions (custom fields like Status, Priority)</li>
 *   <li><b>Status Updates:</b> Project health status updates (ON_TRACK, AT_RISK, OFF_TRACK)</li>
 *   <li><b>Draft Issues:</b> Project items that are draft issues (no parent issue)</li>
 * </ol>
 * <p>
 * Each phase has independent cursor persistence for resumability. If one phase fails (e.g., rate limit),
 * the returned {@link SyncResult} includes phase-level success/failure information for monitoring.
 *
 * @see de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants
 * @see de.tum.in.www1.hephaestus.gitprovider.project.Project.OwnerType
 * @see de.tum.in.www1.hephaestus.gitprovider.project.github.GitHubProjectItemSyncService
 */
@Service
public class GitHubProjectSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubProjectSyncService.class);
    private static final String GET_ORGANIZATION_PROJECTS_DOCUMENT = "GetOrganizationProjects";
    private static final String GET_PROJECT_WITH_FIELDS_DOCUMENT = "GetProjectWithFields";
    private static final String GET_PROJECT_ITEMS_DOCUMENT = "GetProjectItems";
    private static final String GET_PROJECT_ITEM_FIELD_VALUES_DOCUMENT = "GetProjectItemFieldValues";
    private static final String GET_PROJECT_STATUS_UPDATES_DOCUMENT = "GetProjectStatusUpdates";

    /** Maximum number of retry attempts for transient failures. */
    private static final int MAX_RETRY_ATTEMPTS = 3;

    /**
     * Retry configuration for transport-level errors during body streaming.
     * <p>
     * CRITICAL: WebClient ExchangeFilterFunction retries DO NOT cover body streaming errors.
     * PrematureCloseException occurs AFTER HTTP headers are received, during body consumption.
     * We must retry at this level using Mono.defer() to wrap the entire execute() call.
     */
    private static final int TRANSPORT_MAX_RETRIES = 3;
    private static final Duration TRANSPORT_INITIAL_BACKOFF = Duration.ofSeconds(2);
    private static final Duration TRANSPORT_MAX_BACKOFF = Duration.ofSeconds(15);
    private static final double JITTER_FACTOR = 0.5;

    private final ProjectRepository projectRepository;
    private final ProjectItemRepository projectItemRepository;
    private final ProjectFieldRepository projectFieldRepository;
    private final ProjectFieldValueRepository projectFieldValueRepository;
    private final ProjectStatusUpdateRepository statusUpdateRepository;
    private final OrganizationRepository organizationRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubProjectProcessor projectProcessor;
    private final GitHubProjectItemProcessor itemProcessor;
    private final GitHubProjectStatusUpdateProcessor statusUpdateProcessor;
    private final BackfillStateProvider backfillStateProvider;
    private final TransactionTemplate transactionTemplate;
    private final GitHubSyncProperties syncProperties;
    private final SyncSchedulerProperties syncSchedulerProperties;
    private final GitHubExceptionClassifier exceptionClassifier;

    public GitHubProjectSyncService(
        ProjectRepository projectRepository,
        ProjectItemRepository projectItemRepository,
        ProjectFieldRepository projectFieldRepository,
        ProjectFieldValueRepository projectFieldValueRepository,
        ProjectStatusUpdateRepository statusUpdateRepository,
        OrganizationRepository organizationRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubProjectProcessor projectProcessor,
        GitHubProjectItemProcessor itemProcessor,
        GitHubProjectStatusUpdateProcessor statusUpdateProcessor,
        BackfillStateProvider backfillStateProvider,
        TransactionTemplate transactionTemplate,
        GitHubSyncProperties syncProperties,
        SyncSchedulerProperties syncSchedulerProperties,
        GitHubExceptionClassifier exceptionClassifier
    ) {
        this.projectRepository = projectRepository;
        this.projectItemRepository = projectItemRepository;
        this.projectFieldRepository = projectFieldRepository;
        this.projectFieldValueRepository = projectFieldValueRepository;
        this.statusUpdateRepository = statusUpdateRepository;
        this.organizationRepository = organizationRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.projectProcessor = projectProcessor;
        this.itemProcessor = itemProcessor;
        this.statusUpdateProcessor = statusUpdateProcessor;
        this.backfillStateProvider = backfillStateProvider;
        this.transactionTemplate = transactionTemplate;
        this.syncProperties = syncProperties;
        this.syncSchedulerProperties = syncSchedulerProperties;
        this.exceptionClassifier = exceptionClassifier;
    }

    /**
     * Synchronizes all projects for an organization using GraphQL.
     * <p>
     * This method syncs only the project list (metadata). Draft Issue sync should be
     * called separately via {@link #syncDraftIssues} for each project.
     * <p>
     * Note: This method intentionally does NOT use @Transactional to avoid long-running
     * transactions. Each page of projects is processed in its own transaction.
     *
     * @param scopeId           the scope ID for authentication
     * @param organizationLogin the GitHub organization login to sync projects for
     * @return sync result containing status and count of projects synced
     */
    public SyncResult syncProjectsForOrganization(Long scopeId, String organizationLogin) {
        if (organizationLogin == null || organizationLogin.isBlank()) {
            log.warn("Skipped project sync: reason=missingOrgLogin, scopeId={}", scopeId);
            return SyncResult.completed(0);
        }
        String safeOrgLogin = sanitizeForLog(organizationLogin);

        // Resolve organization outside transaction to avoid holding locks
        Organization organization = transactionTemplate.execute(status ->
            organizationRepository.findByLoginIgnoreCase(organizationLogin).orElse(null)
        );

        if (organization == null) {
            log.warn("Skipped project sync: reason=organizationNotFound, orgLogin={}", safeOrgLogin);
            return SyncResult.completed(0);
        }

        Long organizationId = organization.getId();
        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);
        Duration timeout = syncProperties.graphqlTimeout();

        Set<Long> syncedProjectIds = new HashSet<>();
        int totalSynced = 0;
        int totalSkipped = 0;
        String cursor = null;
        boolean hasMore = true;
        int pageCount = 0;
        int retryAttempt = 0;
        SyncResult.Status abortReason = null;

        while (hasMore) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.warn(
                    "Reached maximum pagination limit for projects: orgLogin={}, limit={}",
                    safeOrgLogin,
                    MAX_PAGINATION_PAGES
                );
                break;
            }

            try {
                // Use Mono.defer() to wrap the entire execute() call for retry support
                final String currentCursor = cursor;
                final int currentPage = pageCount;

                ClientGraphQlResponse graphQlResponse = Mono.defer(() ->
                    client
                        .documentName(GET_ORGANIZATION_PROJECTS_DOCUMENT)
                        .variable("login", organizationLogin)
                        .variable("first", LARGE_PAGE_SIZE)
                        .variable("after", currentCursor)
                        .execute()
                )
                    .retryWhen(
                        Retry.backoff(TRANSPORT_MAX_RETRIES, TRANSPORT_INITIAL_BACKOFF)
                            .maxBackoff(TRANSPORT_MAX_BACKOFF)
                            .jitter(JITTER_FACTOR)
                            .filter(this::isTransportError)
                            .doBeforeRetry(signal ->
                                log.warn(
                                    "Retrying project sync after transport error: orgLogin={}, page={}, attempt={}, error={}",
                                    safeOrgLogin,
                                    currentPage,
                                    signal.totalRetries() + 1,
                                    signal.failure().getMessage()
                                )
                            )
                    )
                    .block(timeout);

                if (graphQlResponse == null || !graphQlResponse.isValid()) {
                    log.warn(
                        "Received invalid GraphQL response: orgLogin={}, errors={}",
                        safeOrgLogin,
                        graphQlResponse != null ? graphQlResponse.getErrors() : "null"
                    );
                    abortReason = SyncResult.Status.ABORTED_ERROR;
                    break;
                }

                // Track rate limit from response
                graphQlClientProvider.trackRateLimit(scopeId, graphQlResponse);

                // Check if we should pause due to rate limiting
                if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                    log.warn("Aborting project sync due to critical rate limit: orgLogin={}", safeOrgLogin);
                    abortReason = SyncResult.Status.ABORTED_RATE_LIMIT;
                    break;
                }

                GHProjectV2Connection response = graphQlResponse
                    .field("organization.projectsV2")
                    .toEntity(GHProjectV2Connection.class);

                if (response == null || response.getNodes() == null || response.getNodes().isEmpty()) {
                    break;
                }

                // Process this page of projects in its own transaction
                // Respect cooldown: only UPDATE projects that are new or past cooldown
                // Respect filter: only sync projects in allowed-projects list (empty = all)
                final Long orgId = organizationId;
                final String orgLogin = organizationLogin;
                final Instant cooldownThreshold = Instant.now().minusSeconds(
                    syncSchedulerProperties.cooldownMinutes() * 60L
                );
                final var filters = syncSchedulerProperties.filters();
                PageResult pageResult = transactionTemplate.execute(status -> {
                    ProcessingContext context = ProcessingContext.forSync(scopeId, null);
                    int projectsProcessed = 0;
                    int projectsSkipped = 0;
                    int projectsFiltered = 0;

                    for (var graphQlProject : response.getNodes()) {
                        GitHubProjectDTO dto = GitHubProjectDTO.fromProjectV2(graphQlProject);
                        if (dto == null) {
                            continue;
                        }

                        // Check filter: skip projects not in allowed-projects list
                        if (!filters.isProjectAllowed(orgLogin, dto.number())) {
                            projectsFiltered++;
                            continue;
                        }

                        // Check if project exists and was recently synced
                        Long databaseId = dto.getDatabaseId();
                        if (databaseId != null) {
                            Project existing = projectRepository.findById(databaseId).orElse(null);
                            if (existing != null) {
                                // Track for stale removal regardless of cooldown
                                syncedProjectIds.add(existing.getId());

                                // Skip if within cooldown
                                if (
                                    existing.getLastSyncAt() != null &&
                                    existing.getLastSyncAt().isAfter(cooldownThreshold)
                                ) {
                                    projectsSkipped++;
                                    continue;
                                }
                            }
                        }

                        // Process project (new or past cooldown)
                        Project project = projectProcessor.process(dto, Project.OwnerType.ORGANIZATION, orgId, context);

                        if (project != null) {
                            project.setLastSyncAt(Instant.now());
                            project = projectRepository.save(project);
                            syncedProjectIds.add(project.getId());
                            projectsProcessed++;
                        }
                    }

                    if (projectsSkipped > 0 || projectsFiltered > 0) {
                        log.debug(
                            "Project sync page: orgId={}, processed={}, skippedCooldown={}, filteredOut={}",
                            orgId,
                            projectsProcessed,
                            projectsSkipped,
                            projectsFiltered
                        );
                    }

                    return new PageResult(projectsProcessed, projectsSkipped);
                });

                if (pageResult != null) {
                    totalSynced += pageResult.count;
                    totalSkipped += pageResult.skipped;
                }

                var pageInfo = response.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;

                // Reset retry counter after successful page
                retryAttempt = 0;

                // Apply pagination throttle to prevent 502/504 errors under load
                if (hasMore && !syncProperties.paginationThrottle().isZero()) {
                    try {
                        Thread.sleep(syncProperties.paginationThrottle().toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.debug("Project sync interrupted during throttle: orgLogin={}", safeOrgLogin);
                        abortReason = SyncResult.Status.ABORTED_ERROR;
                        break;
                    }
                }
            } catch (InstallationNotFoundException e) {
                throw e;
            } catch (Exception e) {
                ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
                Category category = classification.category();

                switch (category) {
                    case RETRYABLE -> {
                        if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                            retryAttempt++;
                            log.warn(
                                "Retrying project sync after transient error: orgLogin={}, attempt={}, error={}",
                                safeOrgLogin,
                                retryAttempt,
                                classification.message()
                            );
                            try {
                                ExponentialBackoff.sleep(retryAttempt);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                log.warn("Project sync interrupted during backoff: orgLogin={}", safeOrgLogin);
                                abortReason = SyncResult.Status.ABORTED_ERROR;
                                break;
                            }
                            continue;
                        }
                        log.error(
                            "Failed to sync projects after {} retries: orgLogin={}, error={}",
                            MAX_RETRY_ATTEMPTS,
                            safeOrgLogin,
                            classification.message()
                        );
                        abortReason = SyncResult.Status.ABORTED_ERROR;
                        break;
                    }
                    case RATE_LIMITED -> {
                        if (retryAttempt < MAX_RETRY_ATTEMPTS && classification.suggestedWait() != null) {
                            retryAttempt++;
                            long waitMs = Math.min(
                                classification.suggestedWait().toMillis(),
                                300_000 // Cap at 5 minutes
                            );
                            log.warn(
                                "Rate limited during project sync, waiting: orgLogin={}, waitMs={}, attempt={}",
                                safeOrgLogin,
                                waitMs,
                                retryAttempt
                            );
                            try {
                                Thread.sleep(waitMs);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                log.warn("Project sync interrupted during rate limit wait: orgLogin={}", safeOrgLogin);
                                abortReason = SyncResult.Status.ABORTED_RATE_LIMIT;
                                break;
                            }
                            continue;
                        }
                        log.error(
                            "Aborting project sync due to rate limiting: orgLogin={}, error={}",
                            safeOrgLogin,
                            classification.message()
                        );
                        abortReason = SyncResult.Status.ABORTED_RATE_LIMIT;
                        break;
                    }
                    case NOT_FOUND -> {
                        log.warn(
                            "Resource not found during project sync, skipping: orgLogin={}, error={}",
                            safeOrgLogin,
                            classification.message()
                        );
                        abortReason = SyncResult.Status.ABORTED_ERROR;
                        break;
                    }
                    case AUTH_ERROR -> {
                        log.error(
                            "Authentication error during project sync: orgLogin={}, error={}",
                            safeOrgLogin,
                            classification.message()
                        );
                        throw e;
                    }
                    case CLIENT_ERROR -> {
                        log.error(
                            "Aborting project sync due to client error: orgLogin={}, error={}",
                            safeOrgLogin,
                            classification.message()
                        );
                        abortReason = SyncResult.Status.ABORTED_ERROR;
                        break;
                    }
                    default -> {
                        log.error(
                            "Aborting project sync due to error: orgLogin={}, category={}, error={}",
                            safeOrgLogin,
                            category,
                            classification.message(),
                            e
                        );
                        abortReason = SyncResult.Status.ABORTED_ERROR;
                        break;
                    }
                }
                break;
            }
        }

        // Only remove stale projects if sync completed without abort
        boolean syncCompletedNormally = !hasMore && abortReason == null;
        if (syncCompletedNormally && !syncedProjectIds.isEmpty()) {
            removeDeletedProjects(organizationId, syncedProjectIds);
        } else if (!syncCompletedNormally && abortReason != null) {
            log.warn(
                "Skipped stale project removal: reason=incompleteSync, orgLogin={}, pagesProcessed={}",
                safeOrgLogin,
                pageCount
            );
        }

        SyncResult.Status finalStatus = abortReason != null ? abortReason : SyncResult.Status.COMPLETED;

        log.info(
            "Completed project list sync: orgLogin={}, synced={}, skippedCooldown={}, status={}, scopeId={}",
            safeOrgLogin,
            totalSynced,
            totalSkipped,
            finalStatus,
            scopeId
        );

        return new SyncResult(finalStatus, totalSynced);
    }

    /**
     * Synchronizes Draft Issues for a single project with cursor persistence for resumability.
     * <p>
     * <b>Important:</b> This method only syncs <b>Draft Issues</b> from the project side.
     * Items that link to Issues or Pull Requests are synced from the issue/PR side
     * (via embedded projectItems in issue/PR GraphQL queries) for better efficiency
     * and historical backfill support.
     * <p>
     * Uses the project's {@code itemSyncCursor} field for resumable pagination.
     * Each page is processed in its own transaction to avoid long-running locks.
     * On successful completion, clears the cursor and updates {@code itemsSyncedAt}.
     * <p>
     * <b>Incremental Sync (Client-Side):</b>
     * Since GitHub's API doesn't support server-side filtering by updatedAt for project items,
     * we implement client-side incremental sync:
     * <ul>
     *   <li>All items are fetched (full pagination required)</li>
     *   <li>Items where updatedAt &lt; lastSyncTimestamp are skipped during processing</li>
     *   <li>This saves database writes but not API calls</li>
     *   <li>All Draft Issue node IDs are still tracked for stale item removal</li>
     * </ul>
     *
     * @param scopeId the scope ID for authentication
     * @param project the project to sync Draft Issues for
     * @return sync result containing status and count of Draft Issues synced
     * @see GitHubProjectItemSyncService for Issue/PR item sync from the issue/PR side
     */
    public SyncResult syncDraftIssues(Long scopeId, Project project) {
        if (project == null || project.getNodeId() == null) {
            log.debug("Skipped project item sync: reason=invalidProject");
            return SyncResult.completed(0);
        }

        String projectNodeId = project.getNodeId();
        Long projectId = project.getId();
        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);
        Duration timeout = syncProperties.graphqlTimeout();

        // Phase 1: Sync fields (separate phase with independent cursor)
        boolean fieldsSynced = syncProjectFields(client, project, scopeId);

        // Phase 2: Sync status updates (separate phase with independent cursor)
        boolean statusUpdatesSynced = syncProjectStatusUpdates(client, project, scopeId);

        // Resume from cursor if present (via SPI for consistency)
        String cursor = backfillStateProvider.getProjectItemSyncCursor(projectId).orElse(null);
        boolean resuming = cursor != null;

        // Determine incremental sync threshold (client-side filtering)
        // Items with updatedAt >= incrementalSyncThreshold will be processed
        // Items with updatedAt < incrementalSyncThreshold will be skipped (but still tracked for stale removal)
        Instant incrementalSyncThreshold = null;
        boolean isIncrementalSync = false;
        if (syncProperties.incrementalSyncEnabled() && !resuming) {
            // Only use incremental sync if:
            // 1. Incremental sync is enabled in config
            // 2. We're not resuming from a cursor (resuming means we're mid-sync, need full processing)
            // 3. We have a previous sync timestamp
            Instant lastSyncedAt = backfillStateProvider.getProjectItemsSyncedAt(projectId).orElse(null);
            if (lastSyncedAt != null) {
                // Apply safety buffer to handle clock skew
                incrementalSyncThreshold = lastSyncedAt.minus(syncProperties.incrementalSyncBuffer());
                isIncrementalSync = true;
                log.info(
                    "Starting incremental project item sync: projectId={}, threshold={}, buffer={}",
                    projectId,
                    incrementalSyncThreshold,
                    syncProperties.incrementalSyncBuffer()
                );
            } else {
                log.info("Starting full project item sync (first sync): projectId={}", projectId);
            }
        }

        if (resuming) {
            log.info(
                "Resuming project item sync from checkpoint: projectId={}, cursor={}",
                projectId,
                cursor.substring(0, Math.min(20, cursor.length())) + "..."
            );
        }

        List<String> syncedItemNodeIds = new ArrayList<>();
        // Track items needing follow-up field value pagination (items with >20 field values)
        List<ItemWithFieldValueCursor> itemsNeedingFieldValuePagination = new ArrayList<>();
        int totalSynced = 0;
        int totalSkipped = 0; // Items skipped due to incremental sync
        boolean hasMore = true;
        int pageCount = 0;
        int retryAttempt = 0;
        SyncResult.Status abortReason = null;
        final Instant syncThreshold = incrementalSyncThreshold; // Final for lambda capture
        final boolean incrementalSync = isIncrementalSync;

        while (hasMore) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.warn(
                    "Reached maximum pagination limit for project items: projectId={}, limit={}",
                    projectId,
                    MAX_PAGINATION_PAGES
                );
                abortReason = SyncResult.Status.ABORTED_ERROR;
                break;
            }

            try {
                final String currentCursor = cursor;
                final int currentPage = pageCount;

                // Uses PROJECT_ITEM_PAGE_SIZE (50) instead of LARGE_PAGE_SIZE (100)
                // due to nested field values that multiply API cost.
                // See GitHubSyncConstants for detailed cost calculation.
                ClientGraphQlResponse graphQlResponse = Mono.defer(() ->
                    client
                        .documentName(GET_PROJECT_ITEMS_DOCUMENT)
                        .variable("nodeId", projectNodeId)
                        .variable("first", PROJECT_ITEM_PAGE_SIZE)
                        .variable("after", currentCursor)
                        .execute()
                )
                    .retryWhen(
                        Retry.backoff(TRANSPORT_MAX_RETRIES, TRANSPORT_INITIAL_BACKOFF)
                            .maxBackoff(TRANSPORT_MAX_BACKOFF)
                            .jitter(JITTER_FACTOR)
                            .filter(this::isTransportError)
                            .doBeforeRetry(signal ->
                                log.warn(
                                    "Retrying project item sync after transport error: projectId={}, page={}, attempt={}, error={}",
                                    projectId,
                                    currentPage,
                                    signal.totalRetries() + 1,
                                    signal.failure().getMessage()
                                )
                            )
                    )
                    .block(timeout);

                if (graphQlResponse == null || !graphQlResponse.isValid()) {
                    log.warn(
                        "Received invalid GraphQL response for project items: projectId={}, errors={}",
                        projectId,
                        graphQlResponse != null ? graphQlResponse.getErrors() : "null"
                    );
                    abortReason = SyncResult.Status.ABORTED_ERROR;
                    break;
                }

                graphQlClientProvider.trackRateLimit(scopeId, graphQlResponse);

                if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                    log.warn("Aborting project items sync due to critical rate limit: projectId={}", projectId);
                    abortReason = SyncResult.Status.ABORTED_RATE_LIMIT;
                    break;
                }

                GHProjectV2ItemConnection itemsConnection = graphQlResponse
                    .field("node.items")
                    .toEntity(GHProjectV2ItemConnection.class);

                if (
                    itemsConnection == null ||
                    itemsConnection.getNodes() == null ||
                    itemsConnection.getNodes().isEmpty()
                ) {
                    hasMore = false;
                    break;
                }

                // Process this page of items in its own transaction
                // Only Draft Issues are processed - Issue/PR items are synced from the issue/PR side
                // For incremental sync, items unchanged since last sync are tracked but not processed
                final Long projId = projectId;
                ItemPageResult pageResult = transactionTemplate.execute(status -> {
                    Project proj = projectRepository.findById(projId).orElse(null);
                    if (proj == null) {
                        return new ItemPageResult(0, 0);
                    }

                    ProcessingContext context = ProcessingContext.forSync(scopeId, null);
                    int itemsProcessed = 0;
                    int itemsSkipped = 0;

                    for (GHProjectV2Item graphQlItem : itemsConnection.getNodes()) {
                        GitHubProjectItemDTO itemDto = GitHubProjectItemDTO.fromProjectV2Item(graphQlItem);
                        if (itemDto == null || itemDto.nodeId() == null) {
                            continue;
                        }

                        // Only process Draft Issues from the project side.
                        // Issue and Pull Request items are synced from the issue/PR side
                        // (embedded in issue/PR GraphQL queries) for better efficiency.
                        ProjectItem.ContentType contentType = itemDto.getContentTypeEnum();
                        if (contentType != ProjectItem.ContentType.DRAFT_ISSUE) {
                            // Skip Issue/PR items - they're synced from the issue/PR side
                            continue;
                        }

                        // Track the node ID for stale Draft Issue removal
                        syncedItemNodeIds.add(itemDto.nodeId());

                        // Client-side incremental sync: skip items that haven't changed
                        // This saves database writes while still tracking all items for deletion detection
                        if (incrementalSync && syncThreshold != null && itemDto.updatedAt() != null) {
                            if (itemDto.updatedAt().isBefore(syncThreshold)) {
                                itemsSkipped++;
                                continue; // Skip processing - item unchanged since last sync
                            }
                        }

                        // Sync uses null actorId - attribution only happens via webhooks
                        ProjectItem processedItem = itemProcessor.process(itemDto, proj, context, null);
                        if (processedItem != null) {
                            itemsProcessed++;

                            // Process inline field values for this item
                            processFieldValues(processedItem.getId(), itemDto.fieldValues());

                            // Track items needing follow-up pagination for field values
                            // This follows the IssueWithCommentCursor pattern from GitHubIssueSyncService
                            if (itemDto.fieldValuesTruncated() && itemDto.fieldValuesEndCursor() != null) {
                                itemsNeedingFieldValuePagination.add(
                                    new ItemWithFieldValueCursor(
                                        processedItem.getId(),
                                        itemDto.nodeId(),
                                        itemDto.fieldValuesEndCursor()
                                    )
                                );
                                log.debug(
                                    "Draft Issue queued for field value pagination: itemNodeId={}, projectId={}, fetched={}, total={}",
                                    itemDto.nodeId(),
                                    projId,
                                    itemDto.fieldValues() != null ? itemDto.fieldValues().size() : 0,
                                    itemDto.fieldValuesTotalCount()
                                );
                            }
                        }
                    }

                    return new ItemPageResult(itemsProcessed, itemsSkipped);
                });

                if (pageResult != null) {
                    totalSynced += pageResult.processedCount;
                    totalSkipped += pageResult.skippedCount;
                }

                var pageInfo = itemsConnection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;

                // Persist cursor checkpoint after each successful page (REQUIRES_NEW transaction)
                if (cursor != null && hasMore) {
                    persistItemSyncCursor(projectId, cursor);
                }

                retryAttempt = 0;

                // Apply pagination throttle to prevent 502/504 errors under load
                if (hasMore && !syncProperties.paginationThrottle().isZero()) {
                    try {
                        Thread.sleep(syncProperties.paginationThrottle().toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.debug("Project item sync interrupted during throttle: projectId={}", projectId);
                        abortReason = SyncResult.Status.ABORTED_ERROR;
                        break;
                    }
                }
            } catch (InstallationNotFoundException e) {
                throw e;
            } catch (Exception e) {
                ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
                Category category = classification.category();

                switch (category) {
                    case RETRYABLE -> {
                        if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                            retryAttempt++;
                            log.warn(
                                "Retrying project item sync after transient error: projectId={}, attempt={}, error={}",
                                projectId,
                                retryAttempt,
                                classification.message()
                            );
                            try {
                                ExponentialBackoff.sleep(retryAttempt);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                abortReason = SyncResult.Status.ABORTED_ERROR;
                                break;
                            }
                            continue;
                        }
                        log.error(
                            "Failed to sync project items after {} retries: projectId={}, error={}",
                            MAX_RETRY_ATTEMPTS,
                            projectId,
                            classification.message()
                        );
                        abortReason = SyncResult.Status.ABORTED_ERROR;
                        break;
                    }
                    case RATE_LIMITED -> {
                        if (retryAttempt < MAX_RETRY_ATTEMPTS && classification.suggestedWait() != null) {
                            retryAttempt++;
                            long waitMs = Math.min(classification.suggestedWait().toMillis(), 300_000);
                            log.warn(
                                "Rate limited during project item sync, waiting: projectId={}, waitMs={}",
                                projectId,
                                waitMs
                            );
                            try {
                                Thread.sleep(waitMs);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                abortReason = SyncResult.Status.ABORTED_RATE_LIMIT;
                                break;
                            }
                            continue;
                        }
                        log.error("Aborting project item sync due to rate limiting: projectId={}", projectId);
                        abortReason = SyncResult.Status.ABORTED_RATE_LIMIT;
                        break;
                    }
                    case NOT_FOUND -> {
                        log.warn(
                            "Resource not found during project item sync, skipping: projectId={}, error={}",
                            projectId,
                            classification.message()
                        );
                        abortReason = SyncResult.Status.ABORTED_ERROR;
                        break;
                    }
                    case AUTH_ERROR -> throw e;
                    case CLIENT_ERROR -> {
                        log.error(
                            "Aborting project item sync due to client error: projectId={}, error={}",
                            projectId,
                            classification.message()
                        );
                        abortReason = SyncResult.Status.ABORTED_ERROR;
                        break;
                    }
                    default -> {
                        log.error(
                            "Aborting project item sync due to error: projectId={}, category={}, error={}",
                            projectId,
                            category,
                            classification.message(),
                            e
                        );
                        abortReason = SyncResult.Status.ABORTED_ERROR;
                        break;
                    }
                }
                break;
            }
        }

        // Process remaining field values for Draft Issues that had truncated inline data
        // This follows the nested pagination pattern from GitHubIssueSyncService (comments)
        // and GitHubPullRequestSyncService (review threads)
        if (!itemsNeedingFieldValuePagination.isEmpty()) {
            log.info(
                "Processing remaining field values for {} Draft Issues: projectId={}",
                itemsNeedingFieldValuePagination.size(),
                projectId
            );
            for (ItemWithFieldValueCursor itemWithCursor : itemsNeedingFieldValuePagination) {
                // Abort if we hit rate limit during follow-up pagination
                if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                    log.warn("Aborting field value pagination due to critical rate limit: projectId={}", projectId);
                    abortReason = SyncResult.Status.ABORTED_RATE_LIMIT;
                    break;
                }
                syncRemainingFieldValues(scopeId, projectId, itemWithCursor, client, timeout);
            }
        }

        // Handle completion
        boolean syncCompletedNormally = !hasMore && abortReason == null;
        if (syncCompletedNormally) {
            // Clear cursor and update sync timestamp
            updateItemsSyncCompleted(projectId, Instant.now());

            // Remove stale Draft Issues only on complete sync
            // Note: Issue/PR items are NOT removed here - they are synced from the issue/PR side
            ProcessingContext context = ProcessingContext.forSync(scopeId, null);
            int removed = itemProcessor.removeStaleDraftIssues(projectId, syncedItemNodeIds, context);
            if (removed > 0) {
                log.debug("Removed stale Draft Issues: projectId={}, count={}", projectId, removed);
            }
        } else {
            log.warn(
                "Skipped stale Draft Issue removal: reason=incompleteSync, projectId={}, pagesProcessed={}",
                projectId,
                pageCount
            );
        }

        // Determine final status based on item sync success and phase results
        boolean itemsSynced = abortReason == null && !hasMore;
        SyncResult.Status finalStatus;

        if (abortReason != null) {
            // Item sync was aborted
            finalStatus = abortReason;
        } else if (!itemsSynced || !fieldsSynced || !statusUpdatesSynced) {
            // Item sync incomplete or secondary phases failed
            finalStatus = SyncResult.Status.COMPLETED_WITH_WARNINGS;
        } else {
            // All phases completed successfully
            finalStatus = SyncResult.Status.COMPLETED;
        }

        log.info(
            "Completed Draft Issue sync: projectId={}, draftIssuesProcessed={}, skipped={}, fieldValuePaginations={}, " +
                "resumed={}, incremental={}, status={}, phases=[fields={}, statusUpdates={}, draftIssues={}]",
            projectId,
            totalSynced,
            totalSkipped,
            itemsNeedingFieldValuePagination.size(),
            resuming,
            incrementalSync,
            finalStatus,
            fieldsSynced,
            statusUpdatesSynced,
            itemsSynced
        );

        // Return phase-aware SyncResult
        if (finalStatus == SyncResult.Status.ABORTED_RATE_LIMIT) {
            return SyncResult.abortedRateLimitWithPhases(totalSynced, fieldsSynced, statusUpdatesSynced, itemsSynced);
        } else if (finalStatus == SyncResult.Status.ABORTED_ERROR) {
            return SyncResult.abortedErrorWithPhases(totalSynced, fieldsSynced, statusUpdatesSynced, itemsSynced);
        } else if (finalStatus == SyncResult.Status.COMPLETED_WITH_WARNINGS) {
            return SyncResult.completedWithWarnings(totalSynced, fieldsSynced, statusUpdatesSynced, itemsSynced);
        } else {
            return SyncResult.completedWithPhases(totalSynced, fieldsSynced, statusUpdatesSynced, itemsSynced);
        }
    }

    /**
     * Synchronizes project field definitions with pagination support.
     * <p>
     * Handles projects with many fields (>100) by paginating through all pages.
     * Uses Mono.defer() with retry for transport error resilience.
     *
     * @return true if field sync completed successfully, false if aborted or failed
     */
    private boolean syncProjectFields(HttpGraphQlClient client, Project project, Long scopeId) {
        String projectNodeId = project.getNodeId();
        if (projectNodeId == null) {
            return true; // Nothing to sync, consider it success
        }

        Long projectId = project.getId();
        List<String> allSyncedFieldIds = new ArrayList<>();
        boolean completedNormally = false;

        try {
            String cursor = null;
            boolean hasMore = true;
            int pageCount = 0;

            while (hasMore) {
                pageCount++;
                if (pageCount >= MAX_PAGINATION_PAGES) {
                    log.warn(
                        "Reached maximum pagination limit for project fields: projectId={}, limit={}",
                        projectId,
                        MAX_PAGINATION_PAGES
                    );
                    break;
                }

                final String currentCursor = cursor;

                // Use Mono.defer() for transport error retry
                // Uses PROJECT_FIELD_PAGE_SIZE (50) - fields have minimal nesting,
                // but 50 is sufficient for most projects and reduces response size.
                ClientGraphQlResponse graphQlResponse = Mono.defer(() ->
                    client
                        .documentName(GET_PROJECT_WITH_FIELDS_DOCUMENT)
                        .variable("nodeId", projectNodeId)
                        .variable("fieldsFirst", PROJECT_FIELD_PAGE_SIZE)
                        .variable("fieldsAfter", currentCursor)
                        .execute()
                )
                    .retryWhen(
                        Retry.backoff(TRANSPORT_MAX_RETRIES, TRANSPORT_INITIAL_BACKOFF)
                            .maxBackoff(TRANSPORT_MAX_BACKOFF)
                            .jitter(JITTER_FACTOR)
                            .filter(this::isTransportError)
                            .doBeforeRetry(signal ->
                                log.warn(
                                    "Retrying project fields sync after transport error: projectId={}, attempt={}, error={}",
                                    projectId,
                                    signal.totalRetries() + 1,
                                    signal.failure().getMessage()
                                )
                            )
                    )
                    .block(syncProperties.graphqlTimeout());

                if (graphQlResponse == null || !graphQlResponse.isValid()) {
                    log.warn(
                        "Received invalid GraphQL response for project fields: projectId={}, errors={}",
                        projectId,
                        graphQlResponse != null ? graphQlResponse.getErrors() : "null"
                    );
                    return false;
                }

                graphQlClientProvider.trackRateLimit(scopeId, graphQlResponse);

                if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                    log.warn("Aborting project fields sync due to critical rate limit: projectId={}", projectId);
                    return false;
                }

                GHProjectV2FieldConfigurationConnection fieldsConnection = graphQlResponse
                    .field("node.fields")
                    .toEntity(GHProjectV2FieldConfigurationConnection.class);

                if (
                    fieldsConnection == null ||
                    fieldsConnection.getNodes() == null ||
                    fieldsConnection.getNodes().isEmpty()
                ) {
                    completedNormally = true;
                    break;
                }

                // Process this page of fields in a transaction
                transactionTemplate.executeWithoutResult(status -> {
                    for (GHProjectV2FieldConfiguration fieldConfig : fieldsConnection.getNodes()) {
                        GitHubProjectFieldDTO fieldDto = GitHubProjectFieldDTO.fromFieldConfiguration(fieldConfig);
                        if (fieldDto == null || fieldDto.id() == null) {
                            continue;
                        }

                        allSyncedFieldIds.add(fieldDto.id());

                        ProjectField.DataType dataType = fieldDto.getDataTypeEnum();
                        if (dataType == null) {
                            dataType = ProjectField.DataType.TEXT;
                        }

                        // Use actual timestamps from GitHub, falling back to now if not available
                        Instant createdAt = fieldDto.createdAt() != null ? fieldDto.createdAt() : Instant.now();
                        Instant updatedAt = fieldDto.updatedAt() != null ? fieldDto.updatedAt() : Instant.now();

                        projectFieldRepository.upsertCore(
                            fieldDto.id(),
                            projectId,
                            fieldDto.name(),
                            dataType.name(),
                            fieldDto.getOptionsJson(),
                            createdAt,
                            updatedAt
                        );
                    }
                });

                var pageInfo = fieldsConnection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;

                // Apply pagination throttle
                if (hasMore && !syncProperties.paginationThrottle().isZero()) {
                    try {
                        Thread.sleep(syncProperties.paginationThrottle().toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.debug("Project fields sync interrupted during throttle: projectId={}", projectId);
                        return false;
                    }
                }
            }

            // Set completedNormally only if not already set (e.g., by empty response branch)
            // This preserves true if already set via early break, otherwise uses hasMore state
            completedNormally = completedNormally || !hasMore;

            // On successful completion, handle cleanup and update timestamp
            if (completedNormally) {
                // Remove stale fields only if we have synced IDs to compare against
                if (!allSyncedFieldIds.isEmpty()) {
                    transactionTemplate.executeWithoutResult(status -> {
                        int removed = projectFieldRepository.deleteByProjectIdAndIdNotIn(projectId, allSyncedFieldIds);
                        if (removed > 0) {
                            log.debug("Removed stale project fields: projectId={}, count={}", projectId, removed);
                        }
                    });
                }

                // Update fields synced timestamp (even for projects with zero fields)
                updateFieldsSyncCompleted(projectId, Instant.now());
            }

            log.debug(
                "Synced project fields: projectId={}, fieldCount={}, success={}",
                projectId,
                allSyncedFieldIds.size(),
                completedNormally
            );
            return completedNormally;
        } catch (Exception e) {
            log.warn("Failed to sync project fields: projectId={}, error={}", projectId, e.getMessage());
            return false;
        }
    }

    /**
     * Updates field sync completion state in a new transaction.
     * <p>
     * Clears the cursor and sets the fieldsSyncedAt timestamp.
     */
    private void updateFieldsSyncCompleted(Long projectId, Instant syncedAt) {
        TransactionTemplate requiresNewTemplate = new TransactionTemplate(transactionTemplate.getTransactionManager());
        requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNewTemplate.executeWithoutResult(status -> {
            projectRepository.updateFieldsSyncedAt(projectId, syncedAt);
            log.debug("Updated project fields sync completion: projectId={}", projectId);
        });
    }

    /**
     * Synchronizes project status updates.
     * <p>
     * Status updates track project health with ON_TRACK, AT_RISK, OFF_TRACK statuses.
     * Uses Mono.defer() with retry for transport error resilience.
     *
     * @param client  the GraphQL client
     * @param project the project to sync status updates for
     * @param scopeId the scope ID for rate limit tracking
     * @return true if status update sync completed successfully, false if aborted or failed
     */
    private boolean syncProjectStatusUpdates(HttpGraphQlClient client, Project project, Long scopeId) {
        String projectNodeId = project.getNodeId();
        if (projectNodeId == null) {
            return true; // Nothing to sync, consider it success
        }

        Long projectId = project.getId();
        boolean completedNormally = false;

        try {
            List<String> syncedStatusUpdateNodeIds = new ArrayList<>();
            String cursor = null;
            boolean hasMore = true;
            int pageCount = 0;

            while (hasMore) {
                pageCount++;
                if (pageCount >= MAX_PAGINATION_PAGES) {
                    log.warn(
                        "Reached maximum pagination limit for status updates: projectId={}, limit={}",
                        projectId,
                        MAX_PAGINATION_PAGES
                    );
                    break;
                }

                final String currentCursor = cursor;

                // Uses STATUS_UPDATE_PAGE_SIZE (100) - status updates have minimal nesting
                // (only creator field), so full page size is cost-efficient.
                ClientGraphQlResponse graphQlResponse = Mono.defer(() ->
                    client
                        .documentName(GET_PROJECT_STATUS_UPDATES_DOCUMENT)
                        .variable("nodeId", projectNodeId)
                        .variable("first", STATUS_UPDATE_PAGE_SIZE)
                        .variable("after", currentCursor)
                        .execute()
                )
                    .retryWhen(
                        Retry.backoff(TRANSPORT_MAX_RETRIES, TRANSPORT_INITIAL_BACKOFF)
                            .maxBackoff(TRANSPORT_MAX_BACKOFF)
                            .jitter(JITTER_FACTOR)
                            .filter(this::isTransportError)
                            .doBeforeRetry(signal ->
                                log.warn(
                                    "Retrying status updates sync after transport error: projectId={}, attempt={}, error={}",
                                    projectId,
                                    signal.totalRetries() + 1,
                                    signal.failure().getMessage()
                                )
                            )
                    )
                    .block(syncProperties.graphqlTimeout());

                if (graphQlResponse == null || !graphQlResponse.isValid()) {
                    log.warn(
                        "Received invalid GraphQL response for status updates: projectId={}, errors={}",
                        projectId,
                        graphQlResponse != null ? graphQlResponse.getErrors() : "null"
                    );
                    return false;
                }

                graphQlClientProvider.trackRateLimit(scopeId, graphQlResponse);

                if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                    log.warn("Aborting status updates sync due to critical rate limit: projectId={}", projectId);
                    return false;
                }

                GHProjectV2StatusUpdateConnection statusUpdatesConnection = graphQlResponse
                    .field("node.statusUpdates")
                    .toEntity(GHProjectV2StatusUpdateConnection.class);

                if (
                    statusUpdatesConnection == null ||
                    statusUpdatesConnection.getNodes() == null ||
                    statusUpdatesConnection.getNodes().isEmpty()
                ) {
                    completedNormally = true;
                    break;
                }

                // Process this page of status updates in a transaction
                transactionTemplate.executeWithoutResult(status -> {
                    ProcessingContext context = ProcessingContext.forSync(scopeId, null);

                    for (GHProjectV2StatusUpdate graphQlStatusUpdate : statusUpdatesConnection.getNodes()) {
                        GitHubProjectStatusUpdateDTO dto = GitHubProjectStatusUpdateDTO.fromStatusUpdate(
                            graphQlStatusUpdate
                        );
                        if (dto == null || dto.nodeId() == null) {
                            continue;
                        }

                        syncedStatusUpdateNodeIds.add(dto.nodeId());
                        statusUpdateProcessor.process(dto, project, context);
                    }
                });

                var pageInfo = statusUpdatesConnection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;

                // Apply pagination throttle
                if (hasMore && !syncProperties.paginationThrottle().isZero()) {
                    try {
                        Thread.sleep(syncProperties.paginationThrottle().toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.debug("Status updates sync interrupted during throttle: projectId={}", projectId);
                        return false;
                    }
                }
            }

            // Set completedNormally only if not already set (e.g., by empty response branch)
            // This preserves true if already set via early break, otherwise uses hasMore state
            completedNormally = completedNormally || !hasMore;

            // On successful completion, handle cleanup and update timestamp
            if (completedNormally) {
                // Remove stale status updates only if we have synced IDs to compare against
                if (!syncedStatusUpdateNodeIds.isEmpty()) {
                    transactionTemplate.executeWithoutResult(status -> {
                        ProcessingContext context = ProcessingContext.forSync(scopeId, null);
                        int removed = statusUpdateProcessor.removeStaleStatusUpdates(
                            projectId,
                            syncedStatusUpdateNodeIds,
                            context
                        );
                        if (removed > 0) {
                            log.debug("Removed stale status updates: projectId={}, count={}", projectId, removed);
                        }
                    });
                }

                // Update status updates synced timestamp (even for projects with zero updates)
                updateStatusUpdatesSyncCompleted(projectId, Instant.now());
            }

            log.debug(
                "Synced project status updates: projectId={}, count={}, success={}",
                projectId,
                syncedStatusUpdateNodeIds.size(),
                completedNormally
            );
            return completedNormally;
        } catch (Exception e) {
            log.warn("Failed to sync project status updates: projectId={}, error={}", projectId, e.getMessage());
            return false;
        }
    }

    /**
     * Updates status update sync completion state in a new transaction.
     * <p>
     * Clears the cursor and sets the statusUpdatesSyncedAt timestamp.
     */
    private void updateStatusUpdatesSyncCompleted(Long projectId, Instant syncedAt) {
        TransactionTemplate requiresNewTemplate = new TransactionTemplate(transactionTemplate.getTransactionManager());
        requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNewTemplate.executeWithoutResult(status -> {
            projectRepository.updateStatusUpdatesSyncedAt(projectId, syncedAt);
            log.debug("Updated project status updates sync completion: projectId={}", projectId);
        });
    }

    /**
     * Removes projects that no longer exist in the organization.
     */
    private void removeDeletedProjects(Long organizationId, Set<Long> syncedProjectIds) {
        transactionTemplate.executeWithoutResult(status -> {
            List<Project> existingProjects = projectRepository.findAllByOwnerTypeAndOwnerId(
                Project.OwnerType.ORGANIZATION,
                organizationId
            );

            ProcessingContext context = ProcessingContext.forSync(null, null);
            int removed = 0;

            for (Project project : existingProjects) {
                if (!syncedProjectIds.contains(project.getId())) {
                    projectProcessor.delete(project.getId(), context);
                    removed++;
                }
            }

            if (removed > 0) {
                log.info("Removed stale projects: orgId={}, projectCount={}", organizationId, removed);
            }
        });
    }

    /**
     * Persists the item sync cursor checkpoint in a new transaction.
     * <p>
     * Uses REQUIRES_NEW propagation to ensure cursor is saved even if caller
     * transaction rolls back. Delegates to {@link BackfillStateProvider} SPI
     * for consistent cursor persistence across all sync services.
     */
    private void persistItemSyncCursor(Long projectId, String cursor) {
        TransactionTemplate requiresNewTemplate = new TransactionTemplate(transactionTemplate.getTransactionManager());
        requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNewTemplate.executeWithoutResult(status -> {
            backfillStateProvider.updateProjectItemSyncCursor(projectId, cursor);
            log.debug("Persisted project item sync cursor: projectId={}", projectId);
        });
    }

    /**
     * Updates sync completion state in a new transaction.
     * <p>
     * Clears the cursor and sets the itemsSyncedAt timestamp. Delegates to
     * {@link BackfillStateProvider} SPI for consistent state persistence
     * across all sync services.
     */
    private void updateItemsSyncCompleted(Long projectId, Instant syncedAt) {
        TransactionTemplate requiresNewTemplate = new TransactionTemplate(transactionTemplate.getTransactionManager());
        requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNewTemplate.executeWithoutResult(status -> {
            backfillStateProvider.updateProjectItemsSyncedAt(projectId, syncedAt);
            log.debug("Updated project items sync completion: projectId={}", projectId);
        });
    }

    /**
     * Gets projects for an organization that need item sync, respecting cooldown.
     * <p>
     * Returns all projects ordered by last sync time (oldest first) that haven't
     * been synced within the cooldown period (from {@code SyncSchedulerProperties}).
     * <p>
     * This mirrors the repository sync behavior in {@code GitHubDataSyncService.shouldSync()}.
     *
     * @param organizationId the organization ID
     * @return list of projects needing item sync
     */
    public List<Project> getProjectsNeedingItemSync(Long organizationId) {
        Instant cooldownThreshold = Instant.now().minusSeconds(syncSchedulerProperties.cooldownMinutes() * 60L);
        return projectRepository.findProjectsNeedingItemSync(organizationId, cooldownThreshold);
    }

    /**
     * Processes field values for a project item.
     * <p>
     * Uses atomic upsert to handle concurrent updates and removes stale values.
     *
     * @param itemId      the item's database ID
     * @param fieldValues the field values from the GraphQL response
     */
    private void processFieldValues(Long itemId, List<GitHubProjectFieldValueDTO> fieldValues) {
        if (itemId == null || fieldValues == null || fieldValues.isEmpty()) {
            return;
        }

        List<String> processedFieldIds = new ArrayList<>();

        for (GitHubProjectFieldValueDTO fieldValue : fieldValues) {
            if (fieldValue == null || fieldValue.fieldId() == null) {
                continue;
            }

            // Verify the field exists before attempting to insert
            // (fields should have been synced before items)
            if (!projectFieldRepository.existsById(fieldValue.fieldId())) {
                log.debug(
                    "Skipped field value: reason=fieldNotFound, fieldId={}, itemId={}",
                    fieldValue.fieldId(),
                    itemId
                );
                continue;
            }

            processedFieldIds.add(fieldValue.fieldId());

            projectFieldValueRepository.upsertCore(
                itemId,
                fieldValue.fieldId(),
                fieldValue.textValue(),
                fieldValue.numberValue(),
                fieldValue.dateValue(),
                fieldValue.singleSelectOptionId(),
                fieldValue.iterationId(),
                Instant.now()
            );
        }

        // Remove field values that are no longer present
        if (!processedFieldIds.isEmpty()) {
            int removed = projectFieldValueRepository.deleteByItemIdAndFieldIdNotIn(itemId, processedFieldIds);
            if (removed > 0) {
                log.debug("Removed stale field values: itemId={}, count={}", itemId, removed);
            }
        }
    }

    /**
     * Syncs remaining field values for an item that had truncated inline data.
     * <p>
     * This method paginates through the remaining field values using the
     * {@code GetProjectItemFieldValues} GraphQL query, starting from the cursor
     * where the inline data was truncated.
     * <p>
     * This follows the same nested pagination pattern used in:
     * <ul>
     *   <li>{@code GitHubIssueSyncService.syncRemainingComments()} for issue comments</li>
     *   <li>{@code GitHubPullRequestSyncService.syncRemainingReviews()} for PR reviews</li>
     * </ul>
     *
     * @param scopeId        the scope ID for authentication
     * @param projectId      the project database ID (for logging)
     * @param itemWithCursor the item with cursor information
     * @param client         the GraphQL client
     * @param timeout        the timeout for GraphQL requests
     */
    private void syncRemainingFieldValues(
        Long scopeId,
        Long projectId,
        ItemWithFieldValueCursor itemWithCursor,
        HttpGraphQlClient client,
        Duration timeout
    ) {
        String cursor = itemWithCursor.fieldValueCursor();
        Long itemId = itemWithCursor.itemId();
        String itemNodeId = itemWithCursor.itemNodeId();

        boolean hasMore = true;
        int pageCount = 0;
        int totalFieldValuesSynced = 0;

        while (hasMore) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.warn(
                    "Reached maximum pagination limit for item field values: itemId={}, projectId={}, limit={}",
                    itemId,
                    projectId,
                    MAX_PAGINATION_PAGES
                );
                break;
            }

            try {
                final String currentCursor = cursor;
                final int currentPage = pageCount;

                ClientGraphQlResponse graphQlResponse = Mono.defer(() ->
                    client
                        .documentName(GET_PROJECT_ITEM_FIELD_VALUES_DOCUMENT)
                        .variable("itemId", itemNodeId)
                        .variable("first", FIELD_VALUES_PAGINATION_SIZE)
                        .variable("after", currentCursor)
                        .execute()
                )
                    .retryWhen(
                        Retry.backoff(TRANSPORT_MAX_RETRIES, TRANSPORT_INITIAL_BACKOFF)
                            .maxBackoff(TRANSPORT_MAX_BACKOFF)
                            .jitter(JITTER_FACTOR)
                            .filter(this::isTransportError)
                            .doBeforeRetry(signal ->
                                log.warn(
                                    "Retrying field values pagination after transport error: itemId={}, page={}, attempt={}, error={}",
                                    itemId,
                                    currentPage,
                                    signal.totalRetries() + 1,
                                    signal.failure().getMessage()
                                )
                            )
                    )
                    .block(timeout);

                if (graphQlResponse == null || !graphQlResponse.isValid()) {
                    log.warn(
                        "Received invalid GraphQL response for item field values: itemId={}, errors={}",
                        itemId,
                        graphQlResponse != null ? graphQlResponse.getErrors() : "null"
                    );
                    break;
                }

                graphQlClientProvider.trackRateLimit(scopeId, graphQlResponse);

                if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                    log.warn("Aborting field values pagination due to critical rate limit: itemId={}", itemId);
                    break;
                }

                // Parse the response - the node query returns the item directly
                GHProjectV2ItemFieldValueConnection fieldValuesConnection = graphQlResponse
                    .field("node.fieldValues")
                    .toEntity(GHProjectV2ItemFieldValueConnection.class);

                if (
                    fieldValuesConnection == null ||
                    fieldValuesConnection.getNodes() == null ||
                    fieldValuesConnection.getNodes().isEmpty()
                ) {
                    break;
                }

                // Process this page of field values in a transaction
                final Long finalItemId = itemId;
                transactionTemplate.executeWithoutResult(status -> {
                    // Verify the item still exists
                    if (!projectItemRepository.existsById(finalItemId)) {
                        log.debug("Skipped field values: reason=itemNotFound, itemId={}", finalItemId);
                        return;
                    }

                    // Convert and process field values
                    List<GitHubProjectFieldValueDTO> fieldValueDTOs = fieldValuesConnection
                        .getNodes()
                        .stream()
                        .map(GitHubProjectFieldValueDTO::fromFieldValue)
                        .filter(dto -> dto != null && dto.fieldId() != null)
                        .toList();

                    // Process each field value (append mode - don't remove stale values yet)
                    for (GitHubProjectFieldValueDTO fieldValue : fieldValueDTOs) {
                        // Verify the field exists before attempting to insert
                        if (!projectFieldRepository.existsById(fieldValue.fieldId())) {
                            log.debug(
                                "Skipped field value: reason=fieldNotFound, fieldId={}, itemId={}",
                                fieldValue.fieldId(),
                                finalItemId
                            );
                            continue;
                        }

                        projectFieldValueRepository.upsertCore(
                            finalItemId,
                            fieldValue.fieldId(),
                            fieldValue.textValue(),
                            fieldValue.numberValue(),
                            fieldValue.dateValue(),
                            fieldValue.singleSelectOptionId(),
                            fieldValue.iterationId(),
                            Instant.now()
                        );
                    }
                });

                totalFieldValuesSynced += fieldValuesConnection.getNodes().size();

                var pageInfo = fieldValuesConnection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;

                // Apply pagination throttle
                if (hasMore && !syncProperties.paginationThrottle().isZero()) {
                    try {
                        Thread.sleep(syncProperties.paginationThrottle().toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.debug("Field values pagination interrupted during throttle: itemId={}", itemId);
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn(
                    "Failed to sync remaining field values: itemId={}, projectId={}, error={}",
                    itemId,
                    projectId,
                    e.getMessage()
                );
                break;
            }
        }

        if (totalFieldValuesSynced > 0) {
            log.debug(
                "Synced remaining field values: itemId={}, projectId={}, count={}, pages={}",
                itemId,
                projectId,
                totalFieldValuesSynced,
                pageCount
            );
        }
    }

    /**
     * Result container for project list page processing.
     */
    private record PageResult(int count, int skipped) {}

    /**
     * Result container for project item page processing with incremental sync support.
     * Tracks both processed items (updated) and skipped items (unchanged since last sync).
     */
    private record ItemPageResult(int processedCount, int skippedCount) {}

    /**
     * Record for tracking items that need follow-up field value pagination.
     * <p>
     * When an item has more than 20 field values (the inline page size), we need to
     * fetch the remaining field values using a separate query. This follows the same
     * pattern as {@code IssueWithCommentCursor} in GitHubIssueSyncService.
     *
     * @param itemId           the database ID of the item
     * @param itemNodeId       the GraphQL node ID of the item (used in follow-up query)
     * @param fieldValueCursor the cursor to resume field value pagination from
     */
    private record ItemWithFieldValueCursor(Long itemId, String itemNodeId, String fieldValueCursor) {}

    /**
     * Determines if an exception is a transport-level error that should be retried.
     */
    private boolean isTransportError(Throwable throwable) {
        if (throwable instanceof GraphQlTransportException) {
            return true;
        }

        Throwable cause = throwable;
        while (cause != null) {
            String className = cause.getClass().getName();

            if (className.contains("PrematureCloseException")) {
                return true;
            }
            if (className.contains("AbortedException") || className.contains("ConnectionResetException")) {
                return true;
            }

            if (cause instanceof java.io.IOException) {
                String message = cause.getMessage();
                if (message != null) {
                    String lower = message.toLowerCase();
                    if (
                        lower.contains("connection reset") ||
                        lower.contains("broken pipe") ||
                        lower.contains("connection abort") ||
                        lower.contains("premature") ||
                        lower.contains("stream closed")
                    ) {
                        return true;
                    }
                }
            }

            cause = cause.getCause();
        }
        return false;
    }
}
