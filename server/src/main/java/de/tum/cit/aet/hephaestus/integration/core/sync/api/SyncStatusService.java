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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
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
    private final AsyncTaskExecutor taskExecutor;
    private final Map<IntegrationKind, ConnectionSyncStateProvider> providers;
    private final Map<IntegrationKind, IntegrationSyncRunner> runners;

    public SyncStatusService(
        ConnectionAdminService connectionAdminService,
        SyncJobService syncJobService,
        SyncJobRepository syncJobRepository,
        @Qualifier("applicationTaskExecutor") AsyncTaskExecutor taskExecutor,
        List<ConnectionSyncStateProvider> providerBeans,
        List<IntegrationSyncRunner> runnerBeans
    ) {
        this.connectionAdminService = connectionAdminService;
        this.syncJobService = syncJobService;
        this.syncJobRepository = syncJobRepository;
        this.taskExecutor = taskExecutor;
        this.providers = providerBeans
            .stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    ConnectionSyncStateProvider::kind,
                    p -> p,
                    SyncStatusService::rejectDuplicate
                )
            );
        this.runners = runnerBeans
            .stream()
            .collect(
                Collectors.toUnmodifiableMap(IntegrationSyncRunner::kind, r -> r, SyncStatusService::rejectDuplicate)
            );
    }

    public ConnectionSyncStatusDTO getStatus(long workspaceId, long connectionId) {
        Connection connection = connectionAdminService.findInWorkspaceOrThrow(workspaceId, connectionId);
        ConnectionSyncStateProvider provider = providers.get(connection.getKind());
        IntegrationRef ref = connection.toRef();
        ConnectionSyncDetails details =
            provider == null ? ConnectionSyncDetails.empty() : provider.describe(ref, connectionId);
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
            details.rateLimit() == null ? null : RateLimitSnapshotDTO.from(details.rateLimit()),
            details.backfill() == null ? null : BackfillSummaryDTO.from(details.backfill()),
            new ResourceCountsDTO((long) resources.size(), erroredResources)
        );
    }

    public List<SyncResourceStateDTO> getResources(Long workspaceId, long connectionId) {
        Connection connection = connectionAdminService.findInWorkspaceOrThrow(workspaceId, connectionId);
        ConnectionSyncStateProvider provider = providers.get(connection.getKind());
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
                    ")"
            );
        }
        IntegrationSyncRunner runner = runners.get(connection.getKind());
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
            taskExecutor.execute(() ->
                syncJobService.executeBody(started, handle -> {
                    if (type == SyncJobType.BACKFILL) {
                        runner.backfill(ref, handle);
                    } else {
                        runner.reconcile(ref, handle);
                    }
                })
            );
            return new TriggerOutcome(SyncJobDTO.from(started.job()), true);
        } catch (SyncJobConflictException e) {
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
