package de.tum.in.www1.hephaestus.gitprovider.sync;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.exception.InstallationNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncContextProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncSession;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncStatistics;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncTarget;
import de.tum.in.www1.hephaestus.gitprovider.issuedependency.github.GitHubIssueDependencySyncService;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.github.GitHubIssueTypeSyncService;
import de.tum.in.www1.hephaestus.gitprovider.subissue.github.GitHubSubIssueSyncService;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for periodic GitHub data synchronization across all scopes.
 *
 * <h2>Purpose</h2>
 * Runs scheduled sync jobs using Spring's {@code @Scheduled} annotation.
 * Iterates over all active scopes and syncs their repositories via GraphQL.
 *
 * <h2>Architecture</h2>
 * Uses SPI interfaces to remain decoupled from consuming modules:
 * <ul>
 *   <li>{@link SyncTargetProvider} - provides scope/repository info to sync</li>
 *   <li>{@link SyncContextProvider} - manages context for logging and isolation</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * This class is thread-safe by design:
 * <ul>
 *   <li>Spring's default scheduling uses a single-threaded executor, so
 *       {@code syncDataCron()} will not run concurrently with itself</li>
 *   <li>Each scope sync is isolated - no shared mutable state between scopes</li>
 *   <li>Context is set/cleared per scope via {@code SyncContextProvider}</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Spring creates this component at startup (after {@code NatsConsumerService}
 *       due to {@code @Order(2)})</li>
 *   <li>{@code @Scheduled} method runs at cron interval from {@code hephaestus.sync.cron}</li>
 *   <li>Each run processes all ACTIVE scopes, respecting monitoring filters</li>
 * </ol>
 *
 * <h2>Configuration</h2>
 * <ul>
 *   <li>{@code hephaestus.sync.cron} - Cron expression for sync schedule (default: "0 0 3 * * *" = 3 AM daily)</li>
 *   <li>{@code hephaestus.sync.filters.allowed-organizations} - Limit to specific orgs (dev filter)</li>
 *   <li>{@code hephaestus.sync.filters.allowed-repositories} - Limit to specific repos (dev filter)</li>
 * </ul>
 *
 * @see GitHubDataSyncService
 * @see SyncTargetProvider
 */
@Order(value = 2)
@Component
public class GitHubDataSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(GitHubDataSyncScheduler.class);

    private final SyncTargetProvider syncTargetProvider;
    private final SyncContextProvider syncContextProvider;
    private final GitHubDataSyncService dataSyncService;
    private final GitHubSubIssueSyncService subIssueSyncService;
    private final GitHubIssueTypeSyncService issueTypeSyncService;
    private final GitHubIssueDependencySyncService issueDependencySyncService;
    private final Executor monitoringExecutor;

    public GitHubDataSyncScheduler(
        SyncTargetProvider syncTargetProvider,
        SyncContextProvider syncContextProvider,
        GitHubDataSyncService dataSyncService,
        GitHubSubIssueSyncService subIssueSyncService,
        GitHubIssueTypeSyncService issueTypeSyncService,
        GitHubIssueDependencySyncService issueDependencySyncService,
        @Qualifier("monitoringExecutor") Executor monitoringExecutor
    ) {
        this.syncTargetProvider = syncTargetProvider;
        this.syncContextProvider = syncContextProvider;
        this.dataSyncService = dataSyncService;
        this.subIssueSyncService = subIssueSyncService;
        this.issueTypeSyncService = issueTypeSyncService;
        this.issueDependencySyncService = issueDependencySyncService;
        this.monitoringExecutor = monitoringExecutor;
    }

    /**
     * Scheduled job to sync GitHub data for all active scopes.
     * Respects monitoring filters to limit sync scope during development.
     */
    @Scheduled(cron = "${hephaestus.sync.cron}")
    public void syncDataCron() {
        log.info("Starting scheduled sync");

        // Get statistics for logging
        SyncStatistics stats = syncTargetProvider.getSyncStatistics();

        // Get sync sessions (already filtered by status and monitoring scope)
        List<SyncSession> sessions = syncTargetProvider.getSyncSessions();

        if (sessions.isEmpty()) {
            log.info(
                "No scopes to sync: totalScopes={}, skippedByStatus={}, skippedByFilter={}",
                stats.totalScopes(),
                stats.skippedByStatus(),
                stats.skippedByFilter()
            );
            return;
        }

        if (stats.filterActive()) {
            log.info(
                "Monitoring filter active: scopesToSync={}, totalScopes={}, skippedByStatus={}, skippedByFilter={}",
                stats.activeAndAllowed(),
                stats.totalScopes(),
                stats.skippedByStatus(),
                stats.skippedByFilter()
            );
        } else {
            log.info(
                "Found scopes to sync: count={}, skippedByStatus={}",
                stats.activeAndAllowed(),
                stats.skippedByStatus()
            );
        }

        // Process all workspaces in parallel - each has its own GitHub App installation
        // with separate rate limits, so there's no reason to sync sequentially.
        // Uses virtual threads (monitoringExecutor) for efficient I/O-bound operations.
        //
        // Each future handles its own exceptions via exceptionally() so that:
        // 1. One workspace failure doesn't prevent other workspaces from completing
        // 2. All exceptions are logged with proper context
        // 3. We can report accurate success/failure counts
        //
        // No global timeout: large repositories (10k+ issues) combined with rate limits
        // can legitimately take many hours. Individual GraphQL calls have their own
        // timeouts for transient failures. Spring's single-threaded scheduler prevents
        // overlapping runs, and join() respects thread interruption for JVM shutdown.
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        CompletableFuture<?>[] futures = sessions.stream()
            .map(session -> CompletableFuture.runAsync(() -> syncScope(session), monitoringExecutor)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        // Already logged inside syncScope
                        failureCount.incrementAndGet();
                    } else {
                        successCount.incrementAndGet();
                    }
                }))
            .toArray(CompletableFuture[]::new);

        // Wait for all workspace syncs to complete
        // Use get() instead of join() because get() throws InterruptedException,
        // allowing graceful shutdown when Ctrl+C is pressed.
        try {
            CompletableFuture.allOf(futures).get();
            log.info(
                "Completed scheduled sync: scopeCount={}, successful={}, failed={}",
                sessions.size(),
                successCount.get(),
                failureCount.get()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn(
                "Scheduled sync interrupted (shutdown requested): scopeCount={}, successful={}, failed={}",
                sessions.size(),
                successCount.get(),
                failureCount.get()
            );
        } catch (ExecutionException e) {
            // Should not happen since each future handles its own exceptions via whenComplete()
            log.error("Unexpected error during scheduled sync", e);
        }
    }

    private void syncScope(SyncSession session) {
        try {
            // Set context for logging and isolation
            syncContextProvider.setContext(session.syncContext());

            log.info(
                "Starting scope sync: scopeId={}, scopeSlug={}, accountLogin={}",
                session.scopeId(),
                session.slug(),
                sanitizeForLog(session.accountLogin())
            );

            // Wrap sync operations with context propagation for async threads
            Runnable syncTask = syncContextProvider.wrapWithContext(() -> {
                // Sync issue types FIRST (before repository syncs) because they are
                // organization-level entities that issues reference. This ensures
                // issue types exist when issues are processed during repository sync.
                syncIssueTypes(session);

                // Sync repositories, organizations, and teams (via syncSyncTarget)
                for (SyncTarget target : session.syncTargets()) {
                    dataSyncService.syncSyncTarget(target);
                }

                // Sync sub-issues and issue dependencies via GraphQL
                // These are scope-level relationships that require issues/PRs to exist first
                syncSubIssues(session);
                syncIssueDependencies(session);
            });

            // Execute synchronously in the scheduler thread
            syncTask.run();
        } catch (InstallationNotFoundException e) {
            log.warn(
                "Aborting scope sync: reason=installationDeleted, scopeId={}, scopeSlug={}, installationId={}",
                session.scopeId(),
                session.slug(),
                e.getInstallationId()
            );
        } catch (Exception e) {
            log.error("Failed to sync scope: scopeId={}, scopeSlug={}", session.scopeId(), session.slug(), e);
        } finally {
            syncContextProvider.clearContext();
        }
    }

    private void syncSubIssues(SyncSession session) {
        try {
            log.debug("Starting sub-issues sync: scopeId={}, scopeSlug={}", session.scopeId(), session.slug());
            subIssueSyncService.syncSubIssuesForScope(session.scopeId());
        } catch (InstallationNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to sync sub-issues: scopeId={}, scopeSlug={}", session.scopeId(), session.slug(), e);
        }
    }

    private void syncIssueTypes(SyncSession session) {
        try {
            log.debug("Starting issue types sync: scopeId={}, scopeSlug={}", session.scopeId(), session.slug());
            issueTypeSyncService.syncIssueTypesForScope(session.scopeId());
        } catch (InstallationNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to sync issue types: scopeId={}, scopeSlug={}", session.scopeId(), session.slug(), e);
        }
    }

    private void syncIssueDependencies(SyncSession session) {
        // NOTE (Dec 2025): issue_dependencies webhook is STILL NOT AVAILABLE
        // (GitHub shipped UI without API/webhook - see Discussion #165749)
        // GraphQL bulk sync is currently the ONLY way to get dependency data
        try {
            log.debug("Starting issue dependencies sync: scopeId={}, scopeSlug={}", session.scopeId(), session.slug());
            issueDependencySyncService.syncDependenciesForScope(session.scopeId());
        } catch (InstallationNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error(
                "Failed to sync issue dependencies: scopeId={}, scopeSlug={}",
                session.scopeId(),
                session.slug(),
                e
            );
        }
    }
}
