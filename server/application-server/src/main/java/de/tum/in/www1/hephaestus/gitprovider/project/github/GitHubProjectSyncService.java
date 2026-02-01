package de.tum.in.www1.hephaestus.gitprovider.project.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.LARGE_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.MAX_PAGINATION_PAGES;

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
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.Project;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectField;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectFieldRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectFieldValueRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectItem;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectDTO;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectFieldDTO;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectFieldValueDTO;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectItemDTO;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncResult;
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
 * This service follows the production-grade sync pattern with:
 * <ul>
 *   <li>Per-page transactions to avoid long-running transaction locks</li>
 *   <li>Cursor persistence for resumable sync operations</li>
 *   <li>Transport error retry for network failures</li>
 *   <li>Decoupled phases: project list sync, then item sync</li>
 * </ul>
 * <p>
 * Supports checkpoint/cursor persistence for resumable sync operations.
 * When sync is interrupted (rate limit, error, crash), it can resume from
 * where it left off using the persisted cursor.
 */
@Service
public class GitHubProjectSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubProjectSyncService.class);
    private static final String GET_ORGANIZATION_PROJECTS_DOCUMENT = "GetOrganizationProjects";
    private static final String GET_PROJECT_WITH_FIELDS_DOCUMENT = "GetProjectWithFields";
    private static final String GET_PROJECT_ITEMS_DOCUMENT = "GetProjectItems";

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
    private final ProjectFieldRepository projectFieldRepository;
    private final ProjectFieldValueRepository projectFieldValueRepository;
    private final OrganizationRepository organizationRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubProjectProcessor projectProcessor;
    private final GitHubProjectItemProcessor itemProcessor;
    private final BackfillStateProvider backfillStateProvider;
    private final TransactionTemplate transactionTemplate;
    private final GitHubSyncProperties syncProperties;
    private final GitHubExceptionClassifier exceptionClassifier;

    public GitHubProjectSyncService(
        ProjectRepository projectRepository,
        ProjectFieldRepository projectFieldRepository,
        ProjectFieldValueRepository projectFieldValueRepository,
        OrganizationRepository organizationRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubProjectProcessor projectProcessor,
        GitHubProjectItemProcessor itemProcessor,
        BackfillStateProvider backfillStateProvider,
        TransactionTemplate transactionTemplate,
        GitHubSyncProperties syncProperties,
        GitHubExceptionClassifier exceptionClassifier
    ) {
        this.projectRepository = projectRepository;
        this.projectFieldRepository = projectFieldRepository;
        this.projectFieldValueRepository = projectFieldValueRepository;
        this.organizationRepository = organizationRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.projectProcessor = projectProcessor;
        this.itemProcessor = itemProcessor;
        this.backfillStateProvider = backfillStateProvider;
        this.transactionTemplate = transactionTemplate;
        this.syncProperties = syncProperties;
        this.exceptionClassifier = exceptionClassifier;
    }

    /**
     * Synchronizes all projects for an organization using GraphQL.
     * <p>
     * This method syncs only the project list (metadata). Item sync should be
     * called separately via {@link #syncProjectItems} for each project.
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
                final Long orgId = organizationId;
                PageResult pageResult = transactionTemplate.execute(status -> {
                    ProcessingContext context = ProcessingContext.forSync(scopeId, null);
                    int projectsProcessed = 0;

                    for (var graphQlProject : response.getNodes()) {
                        GitHubProjectDTO dto = GitHubProjectDTO.fromProjectV2(graphQlProject);
                        if (dto == null) {
                            continue;
                        }

                        Project project = projectProcessor.process(dto, Project.OwnerType.ORGANIZATION, orgId, context);

                        if (project != null) {
                            project.setLastSyncAt(Instant.now());
                            project = projectRepository.save(project);
                            syncedProjectIds.add(project.getId());
                            projectsProcessed++;
                        }
                    }

                    return new PageResult(projectsProcessed);
                });

                if (pageResult != null) {
                    totalSynced += pageResult.count;
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
            "Completed project list sync: orgLogin={}, projectCount={}, status={}, scopeId={}",
            safeOrgLogin,
            totalSynced,
            finalStatus,
            scopeId
        );

        return new SyncResult(finalStatus, totalSynced);
    }

    /**
     * Synchronizes items for a single project with cursor persistence for resumability.
     * <p>
     * Uses the project's {@code itemSyncCursor} field for resumable pagination.
     * Each page is processed in its own transaction to avoid long-running locks.
     * On successful completion, clears the cursor and updates {@code itemsSyncedAt}.
     *
     * @param scopeId the scope ID for authentication
     * @param project the project to sync items for
     * @return sync result containing status and count of items synced
     */
    public SyncResult syncProjectItems(Long scopeId, Project project) {
        if (project == null || project.getNodeId() == null) {
            log.debug("Skipped project item sync: reason=invalidProject");
            return SyncResult.completed(0);
        }

        String projectNodeId = project.getNodeId();
        Long projectId = project.getId();
        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);
        Duration timeout = syncProperties.graphqlTimeout();

        // Sync fields first (separate phase)
        syncProjectFields(client, project, scopeId);

        // Resume from cursor if present (via SPI for consistency)
        String cursor = backfillStateProvider.getProjectItemSyncCursor(projectId).orElse(null);
        boolean resuming = cursor != null;

        if (resuming) {
            log.info(
                "Resuming project item sync from checkpoint: projectId={}, cursor={}",
                projectId,
                cursor.substring(0, Math.min(20, cursor.length())) + "..."
            );
        }

        List<String> syncedItemNodeIds = new ArrayList<>();
        int totalSynced = 0;
        boolean hasMore = true;
        int pageCount = 0;
        int retryAttempt = 0;
        SyncResult.Status abortReason = null;

        while (hasMore) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.warn(
                    "Reached maximum pagination limit for project items: projectId={}, limit={}",
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
                        .documentName(GET_PROJECT_ITEMS_DOCUMENT)
                        .variable("nodeId", projectNodeId)
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
                    break;
                }

                // Process this page of items in its own transaction
                final Long projId = projectId;
                PageResult pageResult = transactionTemplate.execute(status -> {
                    Project proj = projectRepository.findById(projId).orElse(null);
                    if (proj == null) {
                        return new PageResult(0);
                    }

                    ProcessingContext context = ProcessingContext.forSync(scopeId, null);
                    int itemsProcessed = 0;

                    for (GHProjectV2Item graphQlItem : itemsConnection.getNodes()) {
                        GitHubProjectItemDTO itemDto = GitHubProjectItemDTO.fromProjectV2Item(graphQlItem);
                        if (itemDto == null || itemDto.nodeId() == null) {
                            continue;
                        }

                        syncedItemNodeIds.add(itemDto.nodeId());
                        ProjectItem processedItem = itemProcessor.process(itemDto, proj, context);
                        if (processedItem != null) {
                            itemsProcessed++;

                            // Process field values for this item
                            processFieldValues(processedItem.getId(), itemDto.fieldValues());
                        }
                    }

                    return new PageResult(itemsProcessed);
                });

                if (pageResult != null) {
                    totalSynced += pageResult.count;
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

        // Handle completion
        boolean syncCompletedNormally = !hasMore && abortReason == null;
        if (syncCompletedNormally) {
            // Clear cursor and update sync timestamp
            updateItemsSyncCompleted(projectId, Instant.now());

            // Remove stale items only on complete sync
            if (!syncedItemNodeIds.isEmpty()) {
                ProcessingContext context = ProcessingContext.forSync(scopeId, null);
                int removed = itemProcessor.removeStaleItems(projectId, syncedItemNodeIds, context);
                if (removed > 0) {
                    log.debug("Removed stale project items: projectId={}, count={}", projectId, removed);
                }
            }
        } else {
            log.warn(
                "Skipped stale item removal: reason=incompleteSync, projectId={}, pagesProcessed={}",
                projectId,
                pageCount
            );
        }

        SyncResult.Status finalStatus = abortReason != null ? abortReason : SyncResult.Status.COMPLETED;

        log.info(
            "Completed project item sync: projectId={}, itemCount={}, resumed={}, status={}",
            projectId,
            totalSynced,
            resuming,
            finalStatus
        );

        return new SyncResult(finalStatus, totalSynced);
    }

    /**
     * Synchronizes project field definitions.
     * <p>
     * Uses Mono.defer() with retry for transport error resilience.
     */
    private void syncProjectFields(HttpGraphQlClient client, Project project, Long scopeId) {
        String projectNodeId = project.getNodeId();
        if (projectNodeId == null) {
            return;
        }

        try {
            // Use Mono.defer() for transport error retry
            ClientGraphQlResponse graphQlResponse = Mono.defer(() ->
                client.documentName(GET_PROJECT_WITH_FIELDS_DOCUMENT).variable("nodeId", projectNodeId).execute()
            )
                .retryWhen(
                    Retry.backoff(TRANSPORT_MAX_RETRIES, TRANSPORT_INITIAL_BACKOFF)
                        .maxBackoff(TRANSPORT_MAX_BACKOFF)
                        .jitter(JITTER_FACTOR)
                        .filter(this::isTransportError)
                        .doBeforeRetry(signal ->
                            log.warn(
                                "Retrying project fields sync after transport error: projectId={}, attempt={}, error={}",
                                project.getId(),
                                signal.totalRetries() + 1,
                                signal.failure().getMessage()
                            )
                        )
                )
                .block(syncProperties.graphqlTimeout());

            if (graphQlResponse == null || !graphQlResponse.isValid()) {
                log.warn("Received invalid GraphQL response for project fields: projectId={}", project.getId());
                return;
            }

            graphQlClientProvider.trackRateLimit(scopeId, graphQlResponse);

            GHProjectV2FieldConfigurationConnection fieldsConnection = graphQlResponse
                .field("node.fields")
                .toEntity(GHProjectV2FieldConfigurationConnection.class);

            if (fieldsConnection == null || fieldsConnection.getNodes() == null) {
                return;
            }

            // Process fields in a transaction
            final Long projectId = project.getId();
            transactionTemplate.executeWithoutResult(status -> {
                List<String> syncedFieldIds = new ArrayList<>();

                for (GHProjectV2FieldConfiguration fieldConfig : fieldsConnection.getNodes()) {
                    GitHubProjectFieldDTO fieldDto = GitHubProjectFieldDTO.fromFieldConfiguration(fieldConfig);
                    if (fieldDto == null || fieldDto.id() == null) {
                        continue;
                    }

                    syncedFieldIds.add(fieldDto.id());

                    ProjectField.DataType dataType = fieldDto.getDataTypeEnum();
                    if (dataType == null) {
                        dataType = ProjectField.DataType.TEXT;
                    }

                    projectFieldRepository.upsertCore(
                        fieldDto.id(),
                        projectId,
                        fieldDto.name(),
                        dataType.name(),
                        fieldDto.getOptionsJson(),
                        Instant.now(),
                        Instant.now()
                    );
                }

                if (!syncedFieldIds.isEmpty()) {
                    int removed = projectFieldRepository.deleteByProjectIdAndIdNotIn(projectId, syncedFieldIds);
                    if (removed > 0) {
                        log.debug("Removed stale project fields: projectId={}, count={}", projectId, removed);
                    }
                }
            });

            log.debug("Synced project fields: projectId={}", project.getId());
        } catch (Exception e) {
            log.warn("Failed to sync project fields: projectId={}, error={}", project.getId(), e.getMessage());
        }
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
     * Gets all projects for an organization that need item sync.
     * Ordered by last sync time (oldest first) to prioritize stale projects.
     *
     * @param organizationId the organization ID
     * @return list of projects needing item sync
     */
    public List<Project> getProjectsNeedingItemSync(Long organizationId) {
        return projectRepository.findProjectsNeedingItemSync(organizationId);
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
     * Result container for page processing.
     */
    private record PageResult(int count) {}

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
