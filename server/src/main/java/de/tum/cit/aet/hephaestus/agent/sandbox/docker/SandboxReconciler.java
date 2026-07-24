package de.tum.cit.aet.hephaestus.agent.sandbox.docker;

import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Detects and cleans up orphaned sandbox resources.
 *
 * <p>Two triggers clean resources owned by this worker's Docker daemon:
 *
 * <ol>
 *   <li><b>Startup</b> ({@link ApplicationReadyEvent}): resources left by a previous worker process
 *       are cleaned immediately.
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
    private static final String MDC_RECONCILER_TYPE = "reconciler.type";

    private final AgentJobRepository jobRepository;
    private final SandboxContainerManager containerManager;
    private final SandboxNetworkManager networkManager;
    private final Counter orphanedContainers;
    private final Counter orphanedNetworks;
    private final Timer reconciliationDuration;

    public SandboxReconciler(
        AgentJobRepository jobRepository,
        SandboxContainerManager containerManager,
        SandboxNetworkManager networkManager,
        MeterRegistry meterRegistry
    ) {
        this.jobRepository = jobRepository;
        this.containerManager = containerManager;
        this.networkManager = networkManager;
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

    /** On startup, clean only resources on this worker's Docker daemon. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        MDC.put(MDC_RECONCILER_TYPE, "startup");
        try {
            doStartup();
        } finally {
            MDC.remove(MDC_RECONCILER_TYPE);
        }
    }

    private void doStartup() {
        log.info("Sandbox reconciler: startup check");
        cleanupOrphanedDockerResources();
    }

    /** Clean up Docker resources left from previous runs — don't wait for periodic sweep. */
    private void cleanupOrphanedDockerResources() {
        Set<UUID> activeJobIds;
        try {
            activeJobIds = jobRepository
                .findByStatusIn(List.of(AgentJobStatus.QUEUED, AgentJobStatus.RUNNING))
                .stream()
                .map(AgentJob::getId)
                .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("Could not query active jobs — cleaning ALL managed resources: {}", e.getMessage());
            activeJobIds = Set.of(); // If DB is down, treat everything as orphaned
        }
        cleanupOrphanedContainers(activeJobIds);
        cleanupOrphanedNetworks(activeJobIds);
    }

    /** Periodic sweep: clean up orphaned Docker resources. */
    @Scheduled(
        initialDelayString = "${hephaestus.sandbox.reconciliation-initial-delay-seconds:10}",
        fixedDelayString = "${hephaestus.sandbox.reconciliation-interval-seconds:60}",
        timeUnit = TimeUnit.SECONDS
    )
    public void periodicReconciliation() {
        MDC.put(MDC_RECONCILER_TYPE, "periodic");
        try {
            reconciliationDuration.record(() -> {
                log.trace("Sandbox reconciler: periodic sweep");

                Set<UUID> activeJobIds;
                try {
                    activeJobIds = jobRepository
                        .findByStatusIn(List.of(AgentJobStatus.QUEUED, AgentJobStatus.RUNNING))
                        .stream()
                        .map(AgentJob::getId)
                        .collect(Collectors.toSet());
                } catch (Exception e) {
                    log.warn("Could not query active jobs — cleaning ALL managed resources: {}", e.getMessage());
                    activeJobIds = Set.of();
                }

                cleanupOrphanedContainers(activeJobIds);
                cleanupOrphanedNetworks(activeJobIds);
            });
        } finally {
            MDC.remove(MDC_RECONCILER_TYPE);
        }
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
