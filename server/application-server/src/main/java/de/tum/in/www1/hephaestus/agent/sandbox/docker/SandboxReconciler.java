package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import de.tum.in.www1.hephaestus.agent.job.AgentJobRepository;
import de.tum.in.www1.hephaestus.agent.job.AgentJobStatus;
import de.tum.in.www1.hephaestus.agent.sandbox.SandboxProperties;
import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Detects and cleans up orphaned sandbox resources.
 *
 * <p>Two triggers:
 *
 * <ol>
 *   <li><b>Startup</b> ({@link ApplicationReadyEvent}): RUNNING jobs with no matching container are
 *       marked FAILED. Handles crash recovery.
 *   <li><b>Periodic</b> ({@link Scheduled}): orphaned containers and networks are cleaned up on a
 *       configurable interval.
 * </ol>
 *
 * <p>Each operation is idempotent and wrapped in try-catch. A partial failure in one resource does
 * not block cleanup of others.
 */
@WorkspaceAgnostic("Sandbox reconciler operates on Docker infrastructure, not workspace-scoped data")
public class SandboxReconciler {

    private static final Logger log = LoggerFactory.getLogger(SandboxReconciler.class);

    private final AgentJobRepository jobRepository;
    private final SandboxContainerManager containerManager;
    private final SandboxNetworkManager networkManager;
    private final SandboxProperties properties;
    private final Counter orphanedJobs;
    private final Counter orphanedContainers;
    private final Counter orphanedNetworks;
    private final Timer reconciliationDuration;

    public SandboxReconciler(
        AgentJobRepository jobRepository,
        SandboxContainerManager containerManager,
        SandboxNetworkManager networkManager,
        SandboxProperties properties,
        MeterRegistry meterRegistry
    ) {
        this.jobRepository = jobRepository;
        this.containerManager = containerManager;
        this.networkManager = networkManager;
        this.properties = properties;
        this.orphanedJobs = Counter.builder("sandbox.reconciler.orphaned")
            .tag("resource", "job")
            .description("Orphaned jobs marked as FAILED")
            .register(meterRegistry);
        this.orphanedContainers = Counter.builder("sandbox.reconciler.orphaned")
            .tag("resource", "container")
            .description("Orphaned containers removed")
            .register(meterRegistry);
        this.orphanedNetworks = Counter.builder("sandbox.reconciler.orphaned")
            .tag("resource", "network")
            .description("Orphaned networks removed")
            .register(meterRegistry);
        this.reconciliationDuration = Timer.builder("sandbox.reconciler.duration")
            .description("Duration of periodic reconciliation sweeps")
            .register(meterRegistry);
    }

    /**
     * On startup: mark orphaned RUNNING jobs as FAILED.
     *
     * <p>Each job is saved independently (via Spring Data's default transactional save) so a failure
     * on one job does not roll back others.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!properties.enabled()) {
            return;
        }

        log.info("Sandbox reconciler: startup check");

        List<AgentJob> runningJobs = jobRepository.findByStatus(AgentJobStatus.RUNNING);
        if (runningJobs.isEmpty()) {
            log.info("No orphaned running jobs found");
            return;
        }

        // Build set of job IDs that have a live container (matched via label).
        // Uses the same label-based approach as periodic reconciliation for consistency.
        // This is robust even if AgentJob.containerId was never persisted by the caller.
        Set<UUID> jobIdsWithActiveContainers = containerManager
            .listManagedContainers()
            .stream()
            .map(c -> c.labels().get(SandboxLabels.JOB_ID))
            .filter(Objects::nonNull)
            .flatMap(idStr -> {
                try {
                    return Stream.of(UUID.fromString(idStr));
                } catch (IllegalArgumentException e) {
                    return Stream.empty();
                }
            })
            .collect(Collectors.toSet());

        int orphanedCount = 0;
        for (AgentJob job : runningJobs) {
            if (!jobIdsWithActiveContainers.contains(job.getId())) {
                try {
                    job.setStatus(AgentJobStatus.FAILED);
                    job.setErrorMessage("Orphaned after server restart — container missing");
                    job.setCompletedAt(Instant.now());
                    jobRepository.save(job);
                    orphanedCount++;
                    orphanedJobs.increment();
                    log.warn("Marked orphaned job as FAILED: jobId={}", job.getId());
                } catch (Exception e) {
                    log.error("Failed to mark orphaned job as FAILED: jobId={}, error={}", job.getId(), e.getMessage());
                }
            }
        }

        log.info("Startup reconciliation complete: {} orphaned jobs marked FAILED", orphanedCount);
    }

    /** Periodic sweep: clean up orphaned Docker resources. */
    @Scheduled(
        initialDelayString = "${hephaestus.sandbox.reconciliation-interval-seconds:60}",
        fixedDelayString = "${hephaestus.sandbox.reconciliation-interval-seconds:60}",
        timeUnit = TimeUnit.SECONDS
    )
    public void periodicReconciliation() {
        if (!properties.enabled()) {
            return;
        }

        reconciliationDuration.record(() -> {
            log.debug("Sandbox reconciler: periodic sweep");

            Set<UUID> activeJobIds = jobRepository
                .findByStatusIn(List.of(AgentJobStatus.QUEUED, AgentJobStatus.RUNNING))
                .stream()
                .map(AgentJob::getId)
                .collect(Collectors.toSet());

            cleanupOrphanedContainers(activeJobIds);
            cleanupOrphanedNetworks(activeJobIds);
        });
    }

    private void cleanupOrphanedContainers(Set<UUID> activeJobIds) {
        try {
            List<DockerOperations.ContainerInfo> containers = containerManager.listManagedContainers();
            for (DockerOperations.ContainerInfo container : containers) {
                String jobIdStr = container.labels().get(SandboxLabels.JOB_ID);
                if (jobIdStr == null) {
                    continue;
                }
                try {
                    UUID jobId = UUID.fromString(jobIdStr);
                    if (!activeJobIds.contains(jobId)) {
                        log.warn("Removing orphaned container: id={}, jobId={}", container.id(), jobId);
                        containerManager.forceRemove(container.id());
                        orphanedContainers.increment();
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Container {} has invalid job-id label: {}", container.id(), jobIdStr);
                } catch (Exception e) {
                    log.warn("Failed to cleanup orphaned container {}: {}", container.id(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to scan for orphaned containers: {}", e.getMessage());
        }
    }

    private void cleanupOrphanedNetworks(Set<UUID> activeJobIds) {
        try {
            List<DockerOperations.NetworkInfo> networks = networkManager.listOrphanedNetworks();

            for (DockerOperations.NetworkInfo network : networks) {
                // Extract job ID from network name "agent-net-{jobId}"
                String name = network.name();
                if (!name.startsWith(SandboxNetworkManager.NETWORK_PREFIX)) {
                    continue;
                }
                String jobIdStr = name.substring(SandboxNetworkManager.NETWORK_PREFIX.length());
                try {
                    UUID jobId = UUID.fromString(jobIdStr);
                    if (!activeJobIds.contains(jobId)) {
                        log.warn("Removing orphaned network: id={}, name={}", network.id(), name);
                        // Disconnect app-server before removing — Docker refuses to remove
                        // networks with connected containers. Normal cleanup may have failed
                        // to disconnect (the exact scenario reconciliation handles).
                        try {
                            networkManager.disconnectAppServer(network.id());
                        } catch (Exception disconnectEx) {
                            log.debug(
                                "Could not disconnect app-server from orphaned network {}: {}",
                                name,
                                disconnectEx.getMessage()
                            );
                        }
                        networkManager.removeNetwork(network.id());
                        orphanedNetworks.increment();
                    }
                } catch (IllegalArgumentException e) {
                    log.debug("Network {} has non-UUID suffix: {}", name, jobIdStr);
                } catch (Exception e) {
                    log.warn("Failed to cleanup orphaned network {}: {}", name, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to scan for orphaned networks: {}", e.getMessage());
        }
    }
}
