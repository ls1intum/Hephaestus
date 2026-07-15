package de.tum.cit.aet.hephaestus.integration.core.sync.api;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.api.ConnectionAdminService;
import de.tum.cit.aet.hephaestus.integration.core.framework.IntegrationManifestRegistry;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionSyncDetails;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionSyncStateProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationSyncRunner;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncResourceState;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJob;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobConflictException;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobRepository;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobRequest;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobService;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobStatus;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobTrigger;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobType;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncStateConflictException;
import de.tum.cit.aet.hephaestus.integration.core.sync.activity.ConnectionActivity;
import de.tum.cit.aet.hephaestus.integration.core.sync.activity.ConnectionActivityRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Application-layer facade behind {@link SyncController}. Owns provider/runner dispatch by
 * {@link IntegrationKind}, the connection-health derivation, and the async hand-off for manually
 * triggered jobs — the controller stays a thin HTTP adapter (repository access rule).
 *
 * <p>Missing provider/runner for a kind degrades gracefully on reads (empty details / empty resource
 * list) and 409s only the write path ({@link #triggerSync}) via {@link SyncNotSupportedException}.
 */
@ConditionalOnServerRole
@Service
public class SyncStatusService {

    private final ConnectionAdminService connectionAdminService;
    private final SyncJobService syncJobService;
    private final SyncJobRepository syncJobRepository;
    private final ConnectionActivityRepository connectionActivityRepository;
    private final AsyncTaskExecutor taskExecutor;
    private final List<ConnectionSyncStateProvider> providerBeans;
    private final List<IntegrationSyncRunner> runnerBeans;

    public SyncStatusService(
        ConnectionAdminService connectionAdminService,
        SyncJobService syncJobService,
        SyncJobRepository syncJobRepository,
        ConnectionActivityRepository connectionActivityRepository,
        @Qualifier("applicationTaskExecutor") AsyncTaskExecutor taskExecutor,
        List<ConnectionSyncStateProvider> providerBeans,
        List<IntegrationSyncRunner> runnerBeans
    ) {
        this.connectionAdminService = connectionAdminService;
        this.syncJobService = syncJobService;
        this.syncJobRepository = syncJobRepository;
        this.connectionActivityRepository = connectionActivityRepository;
        this.taskExecutor = taskExecutor;
        // Resolved lazily per-lookup (see #providerFor/#runnerFor) rather than built into a map at
        // construction time: eagerly collecting into a Map<IntegrationKind, ...> forces every bean of
        // every kind to be present and duplicate-free at CONTEXT BOOT, which couples this service's
        // startup to whichever test doubles happen to be on the classpath. Lookup-time duplicate
        // detection preserves the "one impl per kind" invariant without that coupling.
        this.providerBeans = List.copyOf(providerBeans);
        this.runnerBeans = List.copyOf(runnerBeans);
    }

    private @Nullable ConnectionSyncStateProvider providerFor(IntegrationKind kind) {
        return resolveSingle(providerBeans, ConnectionSyncStateProvider::kind, kind);
    }

    private @Nullable IntegrationSyncRunner runnerFor(IntegrationKind kind) {
        return resolveSingle(runnerBeans, IntegrationSyncRunner::kind, kind);
    }

    /** Linear scan over the (small, fixed-size) per-kind bean list — same duplicate-detection as before, deferred to lookup time. */
    private static <T> @Nullable T resolveSingle(
        List<T> beans,
        Function<T, IntegrationKind> kindOf,
        IntegrationKind kind
    ) {
        T match = null;
        for (T bean : beans) {
            if (kindOf.apply(bean) == kind) {
                if (match != null) {
                    rejectDuplicate(match, bean);
                }
                match = bean;
            }
        }
        return match;
    }

    public ConnectionSyncStatusDTO getStatus(long workspaceId, long connectionId) {
        Connection connection = connectionAdminService.findInWorkspaceOrThrow(workspaceId, connectionId);
        ConnectionSyncStateProvider provider = providerFor(connection.getKind());
        IntegrationRef ref = connection.toRef();
        ConnectionSyncDetails providerDetails =
            provider == null ? ConnectionSyncDetails.empty() : provider.describe(ref, connectionId);
        // Webhook-liveness is core-owned (ConnectionActivityRecorder writes it from the NATS consumer
        // hook), never populated by the per-vendor provider — merged in here, not inside describe().
        Optional<ConnectionActivity> activity = connectionActivityRepository.findById(connectionId);
        ConnectionSyncDetails details = providerDetails.withActivity(
            activity.map(ConnectionActivity::getLastEventAt).orElse(null),
            activity.map(ConnectionActivity::getLastEventType).orElse(null)
        );
        List<SyncResourceState> resources = provider == null ? List.of() : provider.resources(ref, connectionId);

        Optional<SyncJob> activeJob = syncJobRepository.findFirstByConnection_IdAndStatusInOrderByCreatedAtDesc(
            connectionId,
            SyncJobStatus.ACTIVE
        );
        Optional<SyncJob> lastFinishedJob = syncJobRepository.findFirstByConnection_IdAndStatusInOrderByFinishedAtDesc(
            connectionId,
            SyncJobStatus.TERMINAL
        );
        Optional<SyncJob> lastSuccessfulJob =
            syncJobRepository.findFirstByConnection_IdAndStatusInOrderByFinishedAtDesc(
                connectionId,
                SyncJobStatus.SUCCESSFUL
            );

        long erroredResources = resources
            .stream()
            .filter(r -> r.lastError() != null)
            .count();
        ConnectionHealth health = deriveHealth(
            connection,
            lastFinishedJob,
            erroredResources,
            details.vendorHealthDegraded()
        );

        return new ConnectionSyncStatusDTO(
            connection.getId(),
            connection.getKind(),
            connection.getState(),
            health,
            lastSuccessfulJob.map(SyncJob::getFinishedAt).orElse(null),
            activeJob.map(SyncJobDTO::from).orElse(null),
            lastFinishedJob.map(SyncJobDTO::from).orElse(null),
            details.nextScheduledSyncAt(),
            details.webhookRegistered(),
            details.lastEventProcessedAt(),
            details.lastEventType(),
            details.rateLimit() == null ? null : RateLimitSnapshotDTO.from(details.rateLimit()),
            details.backfill() == null ? null : BackfillSummaryDTO.from(details.backfill()),
            new ResourceCountsDTO((long) resources.size(), erroredResources)
        );
    }

    public List<SyncResourceStateDTO> getResources(Long workspaceId, long connectionId) {
        Connection connection = connectionAdminService.findInWorkspaceOrThrow(workspaceId, connectionId);
        ConnectionSyncStateProvider provider = providerFor(connection.getKind());
        if (provider == null) {
            return List.of();
        }
        return provider.resources(connection.toRef(), connectionId).stream().map(SyncResourceStateDTO::from).toList();
    }

    public Page<SyncJobDTO> getJobs(Long workspaceId, long connectionId, Pageable pageable) {
        // 404s cleanly if the connection isn't in this workspace, before touching job history.
        connectionAdminService.findInWorkspaceOrThrow(workspaceId, connectionId);
        return syncJobRepository
            .findByConnection_IdAndWorkspace_Id(connectionId, workspaceId, pageable)
            .map(SyncJobDTO::from);
    }

    /** Outcome of a trigger call: {@code created=false} means an already-active job was returned (idempotent-absorb). */
    public record TriggerOutcome(SyncJobDTO job, boolean created) {}

    /**
     * Trigger a manual sync. Creates the job row synchronously (so a duplicate-active conflict can be
     * answered immediately) and dispatches the runner body on the bounded {@code applicationTaskExecutor}.
     *
     * @throws SyncStateConflictException if the connection is not ACTIVE (409)
     * @throws SyncNotSupportedException  if no runner is registered for this kind, or the kind's runner
     *                                     doesn't support {@code BACKFILL} (409)
     */
    public TriggerOutcome triggerSync(
        long workspaceId,
        long connectionId,
        SyncJobType type,
        @Nullable Long triggeredByUserId
    ) {
        Connection connection = connectionAdminService.findInWorkspaceOrThrow(workspaceId, connectionId);
        if (connection.getState() != IntegrationState.ACTIVE) {
            throw new SyncStateConflictException(
                "Cannot trigger sync: connection " +
                    connectionId +
                    " is not ACTIVE (state=" +
                    connection.getState() +
                    ")",
                Map.of("connectionId", connectionId, "connectionState", connection.getState())
            );
        }
        IntegrationSyncRunner runner = runnerFor(connection.getKind());
        if (runner == null || (type == SyncJobType.BACKFILL && !runner.supportsBackfill())) {
            throw new SyncNotSupportedException(connection.getKind());
        }

        IntegrationRef ref = connection.toRef();
        try {
            SyncJobService.Started started = syncJobService.beginJob(
                new SyncJobRequest(
                    workspaceId,
                    connectionId,
                    connection.getKind(),
                    type,
                    SyncJobTrigger.MANUAL,
                    triggeredByUserId
                )
            );
            try {
                taskExecutor.execute(() ->
                    syncJobService.executeBody(started, handle -> {
                        if (type == SyncJobType.BACKFILL) {
                            runner.backfill(ref, handle);
                        } else {
                            runner.reconcile(ref, handle);
                        }
                    })
                );
            } catch (TaskRejectedException e) {
                // The row was created but no body will run — finalize it so it doesn't hold the slot.
                syncJobService.failStarted(started, "Sync dispatch rejected (executor saturated)");
                throw e;
            }
            return new TriggerOutcome(SyncJobDTO.from(started.job()), true);
        } catch (SyncJobConflictException e) {
            // Absorb only a same-type duplicate (idempotent double-click). A different type in flight is a
            // genuine conflict — silently returning the running job would drop the caller's request.
            if (e.activeJob().getType() != type) {
                SyncJob active = e.activeJob();
                throw new SyncStateConflictException(
                    "Cannot start " +
                        type +
                        " sync: a " +
                        active.getType() +
                        " sync is already running for connection " +
                        connectionId,
                    Map.of(
                        "conflictingJobId",
                        active.getId(),
                        "conflictingJobType",
                        active.getType(),
                        "conflictingJobStatus",
                        active.getStatus()
                    )
                );
            }
            return new TriggerOutcome(SyncJobDTO.from(e.activeJob()), false);
        }
    }

    /**
     * @throws EntityNotFoundException     if the job doesn't exist in this workspace, or belongs to a
     *                                     different connection than the path's {@code connectionId}
     * @throws SyncStateConflictException if the job is already terminal (409)
     */
    public SyncJobDTO cancelJob(long workspaceId, long connectionId, long jobId) {
        // Validate the (workspace, connection) scope BEFORE mutating anything — a job id that exists
        // but belongs to a different connection must 404, not have its cancel flag flipped first.
        syncJobRepository
            .findByIdAndWorkspace_Id(jobId, workspaceId)
            .filter(j -> j.getConnection().getId() == connectionId)
            .orElseThrow(() -> new EntityNotFoundException("SyncJob", jobId));

        syncJobService.requestCancel(workspaceId, jobId);

        SyncJob job = syncJobRepository
            .findById(jobId)
            .orElseThrow(() -> new EntityNotFoundException("SyncJob", jobId));
        return SyncJobDTO.from(job);
    }

    /** Every kind the manifest registry knows about, joined against this workspace's connections. */
    public List<IntegrationCatalogEntryDTO> catalog(long workspaceId) {
        Map<IntegrationKind, Connection> byKind = new EnumMap<>(IntegrationKind.class);
        for (Connection c : connectionAdminService.listForWorkspace(workspaceId)) {
            byKind.merge(c.getKind(), c, SyncStatusService::preferred);
        }

        IntegrationManifestRegistry manifests = connectionAdminService.manifests();
        return manifests
            .registeredKinds()
            .stream()
            .sorted()
            .map(kind -> {
                Connection c = byKind.get(kind);
                boolean connected = c != null && c.getState() != IntegrationState.UNINSTALLED;
                String displayName = manifests
                    .manifestFor(kind)
                    .map(IntegrationManifest::displayName)
                    .orElse(kind.name());
                return new IntegrationCatalogEntryDTO(
                    kind,
                    displayName,
                    connected,
                    c == null ? null : c.getId(),
                    c == null ? null : c.getState()
                );
            })
            .toList();
    }

    /** When a workspace has multiple rows of the same kind (reconnect history), prefer the live one. */
    private static Connection preferred(Connection current, Connection candidate) {
        boolean currentLive = current.getState() != IntegrationState.UNINSTALLED;
        boolean candidateLive = candidate.getState() != IntegrationState.UNINSTALLED;
        if (currentLive != candidateLive) {
            return currentLive ? current : candidate;
        }
        return candidate.getCreatedAt().isAfter(current.getCreatedAt()) ? candidate : current;
    }

    /**
     * Connection health derivation (design doc §3.3): PENDING/SUSPENDED/UNINSTALLED map directly;
     * ACTIVE folds in the last finished job's outcome, errored resources, and vendor-reported degradation.
     */
    private static ConnectionHealth deriveHealth(
        Connection connection,
        Optional<SyncJob> lastFinishedJob,
        long erroredResources,
        boolean vendorHealthDegraded
    ) {
        return switch (connection.getState()) {
            case PENDING -> ConnectionHealth.PENDING;
            // UNINSTALLED has no further transitions and is not independently modeled in the 5-value
            // health enum — closest semantic is SUSPENDED ("not usable right now").
            case SUSPENDED, UNINSTALLED -> ConnectionHealth.SUSPENDED;
            case ACTIVE -> {
                if (
                    lastFinishedJob
                        .map(SyncJob::getStatus)
                        .filter(s -> s == SyncJobStatus.FAILED)
                        .isPresent()
                ) {
                    yield ConnectionHealth.FAILED;
                }
                boolean warningsJob = lastFinishedJob
                    .map(SyncJob::getStatus)
                    .filter(s -> s == SyncJobStatus.SUCCEEDED_WITH_WARNINGS)
                    .isPresent();
                if (erroredResources > 0 || warningsJob || vendorHealthDegraded) {
                    yield ConnectionHealth.DEGRADED;
                }
                yield ConnectionHealth.HEALTHY;
            }
        };
    }

    private static <T> T rejectDuplicate(T a, T b) {
        throw new IllegalStateException("Duplicate registration for the same IntegrationKind: " + a + " vs " + b);
    }
}
