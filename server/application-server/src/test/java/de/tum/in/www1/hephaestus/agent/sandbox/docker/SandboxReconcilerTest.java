package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import de.tum.in.www1.hephaestus.agent.job.AgentJobRepository;
import de.tum.in.www1.hephaestus.agent.job.AgentJobStatus;
import de.tum.in.www1.hephaestus.agent.sandbox.SandboxProperties;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

@DisplayName("SandboxReconciler")
class SandboxReconcilerTest extends BaseUnitTest {

    @Mock
    private AgentJobRepository jobRepository;

    @Mock
    private SandboxContainerManager containerManager;

    @Mock
    private SandboxNetworkManager networkManager;

    private SandboxReconciler reconciler;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        SandboxProperties properties = new SandboxProperties(
            true,
            "unix:///var/run/docker.sock",
            false,
            null,
            5,
            10,
            60,
            null,
            8080,
            null,
            null
        );
        meterRegistry = new SimpleMeterRegistry();
        reconciler = new SandboxReconciler(jobRepository, containerManager, networkManager, properties, meterRegistry);
    }

    @Nested
    @DisplayName("Disabled guard")
    class DisabledGuard {

        @Test
        @DisplayName("should skip startup reconciliation when disabled")
        void shouldSkipStartupWhenDisabled() {
            SandboxProperties disabledProps = new SandboxProperties(
                false,
                "unix:///var/run/docker.sock",
                false,
                null,
                5,
                10,
                60,
                null,
                8080,
                null,
                null
            );
            var disabledReconciler = new SandboxReconciler(
                jobRepository,
                containerManager,
                networkManager,
                disabledProps,
                meterRegistry
            );

            disabledReconciler.onStartup();

            verify(jobRepository, never()).findByStatus(any());
        }

        @Test
        @DisplayName("should skip periodic reconciliation when disabled")
        void shouldSkipPeriodicWhenDisabled() {
            SandboxProperties disabledProps = new SandboxProperties(
                false,
                "unix:///var/run/docker.sock",
                false,
                null,
                5,
                10,
                60,
                null,
                8080,
                null,
                null
            );
            var disabledReconciler = new SandboxReconciler(
                jobRepository,
                containerManager,
                networkManager,
                disabledProps,
                meterRegistry
            );

            disabledReconciler.periodicReconciliation();

            verify(jobRepository, never()).findByStatusIn(any());
        }
    }

    @Nested
    @DisplayName("Startup reconciliation")
    class StartupReconciliation {

        @Test
        @DisplayName("should mark orphaned RUNNING jobs as FAILED when no container exists")
        void shouldMarkOrphanedJobsAsFailed() {
            UUID jobId = UUID.randomUUID();
            AgentJob orphanedJob = new AgentJob();
            orphanedJob.setId(jobId);
            orphanedJob.setStatus(AgentJobStatus.RUNNING);
            // containerId intentionally NOT set — matches real-world behavior where
            // DockerSandboxAdapter.execute() never persists it to the entity.

            when(jobRepository.findByStatus(AgentJobStatus.RUNNING)).thenReturn(List.of(orphanedJob));
            when(containerManager.listManagedContainers()).thenReturn(List.of()); // No containers running

            reconciler.onStartup();

            ArgumentCaptor<AgentJob> captor = ArgumentCaptor.forClass(AgentJob.class);
            verify(jobRepository).save(captor.capture());
            AgentJob saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(AgentJobStatus.FAILED);
            assertThat(saved.getErrorMessage()).contains("Orphaned");
            assertThat(saved.getCompletedAt()).isNotNull();
            assertThat(meterRegistry.counter("sandbox.reconciler.orphaned", "resource", "job").count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should not mark jobs with active containers matched by label")
        void shouldNotMarkActiveContainerJobs() {
            UUID jobId = UUID.randomUUID();
            AgentJob activeJob = new AgentJob();
            activeJob.setId(jobId);
            activeJob.setStatus(AgentJobStatus.RUNNING);
            // containerId NOT set — label-based matching should still find the container

            when(jobRepository.findByStatus(AgentJobStatus.RUNNING)).thenReturn(List.of(activeJob));
            when(containerManager.listManagedContainers()).thenReturn(
                List.of(
                    new DockerOperations.ContainerInfo(
                        "active-container",
                        "test",
                        Map.of("hephaestus.managed", "true", "hephaestus.job-id", jobId.toString()),
                        "running"
                    )
                )
            );

            reconciler.onStartup();

            verify(jobRepository, never()).save(any());
        }

        @Test
        @DisplayName("should do nothing when no RUNNING jobs exist")
        void shouldDoNothingWithNoRunningJobs() {
            when(jobRepository.findByStatus(AgentJobStatus.RUNNING)).thenReturn(List.of());

            reconciler.onStartup();

            verify(containerManager, never()).listManagedContainers();
        }
    }

    @Nested
    @DisplayName("Periodic reconciliation")
    class PeriodicReconciliation {

        @Test
        @DisplayName("should remove orphaned containers")
        void shouldRemoveOrphanedContainers() {
            UUID orphanedJobId = UUID.randomUUID();
            String orphanedContainerId = "orphaned-container";

            when(jobRepository.findByStatusIn(any())).thenReturn(List.of()); // No active jobs

            when(containerManager.listManagedContainers()).thenReturn(
                List.of(
                    new DockerOperations.ContainerInfo(
                        orphanedContainerId,
                        "test",
                        Map.of("hephaestus.job-id", orphanedJobId.toString()),
                        "exited"
                    )
                )
            );

            when(networkManager.listOrphanedNetworks()).thenReturn(List.of());

            reconciler.periodicReconciliation();

            verify(containerManager).forceRemove(orphanedContainerId);
            assertThat(meterRegistry.counter("sandbox.reconciler.orphaned", "resource", "container").count()).isEqualTo(
                1.0
            );
        }

        @Test
        @DisplayName("should not remove containers with active jobs")
        void shouldNotRemoveActiveContainers() {
            UUID activeJobId = UUID.randomUUID();
            String containerId = "active-container";

            AgentJob activeJob = new AgentJob();
            activeJob.setId(activeJobId);
            activeJob.setStatus(AgentJobStatus.RUNNING);

            when(jobRepository.findByStatusIn(any())).thenReturn(List.of(activeJob));

            when(containerManager.listManagedContainers()).thenReturn(
                List.of(
                    new DockerOperations.ContainerInfo(
                        containerId,
                        "test",
                        Map.of("hephaestus.job-id", activeJobId.toString()),
                        "running"
                    )
                )
            );

            when(networkManager.listOrphanedNetworks()).thenReturn(List.of());

            reconciler.periodicReconciliation();

            verify(containerManager, never()).forceRemove(containerId);
        }

        @Test
        @DisplayName("should remove orphaned networks")
        void shouldRemoveOrphanedNetworks() {
            UUID orphanedJobId = UUID.randomUUID();
            String networkId = "net-orphaned";

            when(jobRepository.findByStatusIn(any())).thenReturn(List.of()); // No active jobs

            when(containerManager.listManagedContainers()).thenReturn(List.of());

            when(networkManager.listOrphanedNetworks()).thenReturn(
                List.of(new DockerOperations.NetworkInfo(networkId, "agent-net-" + orphanedJobId))
            );

            reconciler.periodicReconciliation();

            verify(networkManager).removeNetwork(networkId);
            assertThat(meterRegistry.counter("sandbox.reconciler.orphaned", "resource", "network").count()).isEqualTo(
                1.0
            );
        }

        @Test
        @DisplayName("should continue cleaning other containers when one fails")
        void shouldContinueOnContainerCleanupFailure() {
            UUID jobId1 = UUID.randomUUID();
            UUID jobId2 = UUID.randomUUID();

            when(jobRepository.findByStatusIn(any())).thenReturn(List.of());

            when(containerManager.listManagedContainers()).thenReturn(
                List.of(
                    new DockerOperations.ContainerInfo(
                        "ctr-1",
                        "t1",
                        Map.of("hephaestus.job-id", jobId1.toString()),
                        "exited"
                    ),
                    new DockerOperations.ContainerInfo(
                        "ctr-2",
                        "t2",
                        Map.of("hephaestus.job-id", jobId2.toString()),
                        "exited"
                    )
                )
            );

            doThrow(new RuntimeException("stuck container")).when(containerManager).forceRemove("ctr-1");

            when(networkManager.listOrphanedNetworks()).thenReturn(List.of());

            // Should not throw despite first container cleanup failure
            reconciler.periodicReconciliation();

            // Second container should still be cleaned up
            verify(containerManager).forceRemove("ctr-2");
        }

        @Test
        @DisplayName("should continue cleaning networks when container scan fails")
        void shouldContinueNetworkCleanupOnContainerScanFailure() {
            UUID orphanedJobId = UUID.randomUUID();

            when(jobRepository.findByStatusIn(any())).thenReturn(List.of());

            when(containerManager.listManagedContainers()).thenThrow(new RuntimeException("Docker unreachable"));

            when(networkManager.listOrphanedNetworks()).thenReturn(
                List.of(new DockerOperations.NetworkInfo("net-1", "agent-net-" + orphanedJobId))
            );

            // Should not throw
            reconciler.periodicReconciliation();

            // Network cleanup should still proceed
            verify(networkManager).removeNetwork("net-1");
        }

        @Test
        @DisplayName("should record reconciliation duration")
        void shouldRecordReconciliationDuration() {
            when(jobRepository.findByStatusIn(any())).thenReturn(List.of());
            when(containerManager.listManagedContainers()).thenReturn(List.of());
            when(networkManager.listOrphanedNetworks()).thenReturn(List.of());

            reconciler.periodicReconciliation();

            assertThat(meterRegistry.timer("sandbox.reconciler.duration").count()).isEqualTo(1);
        }
    }
}
