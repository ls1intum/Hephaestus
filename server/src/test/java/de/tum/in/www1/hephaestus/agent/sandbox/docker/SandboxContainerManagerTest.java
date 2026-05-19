package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.agent.sandbox.SandboxProperties;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxException;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@DisplayName("SandboxContainerManager")
class SandboxContainerManagerTest extends BaseUnitTest {

    @Mock
    private DockerContainerOperations containerOps;

    private SandboxContainerManager manager;
    private ExecutorService executor;

    private static final String CONTAINER_ID = "abc123";

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
            209_715_200L,
            500_000,
            null
        );
        executor = Executors.newSingleThreadExecutor();
        manager = new SandboxContainerManager(containerOps, properties, executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Nested
    @DisplayName("waitForCompletion")
    class WaitForCompletion {

        @Test
        @DisplayName("should return exit code on normal completion")
        void shouldReturnExitCodeOnCompletion() {
            when(containerOps.waitContainer(CONTAINER_ID)).thenReturn(new DockerOperations.WaitResult(0));

            SandboxContainerManager.WaitOutcome outcome = manager.waitForCompletion(
                CONTAINER_ID,
                Duration.ofMinutes(5)
            );

            assertThat(outcome.exitCode()).isZero();
            assertThat(outcome.timedOut()).isFalse();
        }

        @Test
        @DisplayName("should return non-zero exit code")
        void shouldReturnNonZeroExitCode() {
            when(containerOps.waitContainer(CONTAINER_ID)).thenReturn(new DockerOperations.WaitResult(42));

            SandboxContainerManager.WaitOutcome outcome = manager.waitForCompletion(
                CONTAINER_ID,
                Duration.ofMinutes(5)
            );

            assertThat(outcome.exitCode()).isEqualTo(42);
            assertThat(outcome.timedOut()).isFalse();
        }

        @Test
        @DisplayName("should timeout and stop container")
        void shouldTimeoutAndStop() {
            // Simulate a container that never exits
            when(containerOps.waitContainer(CONTAINER_ID)).thenAnswer(inv -> {
                Thread.sleep(5000);
                return new DockerOperations.WaitResult(137);
            });

            SandboxContainerManager.WaitOutcome outcome = manager.waitForCompletion(
                CONTAINER_ID,
                Duration.ofMillis(100)
            );

            assertThat(outcome.timedOut()).isTrue();
            verify(containerOps).stopContainer(eq(CONTAINER_ID), anyInt());
        }

        @Test
        @DisplayName("should throw SandboxException on execution error")
        void shouldThrowOnExecutionError() {
            when(containerOps.waitContainer(CONTAINER_ID)).thenThrow(new SandboxException("Docker error"));

            assertThatThrownBy(() -> manager.waitForCompletion(CONTAINER_ID, Duration.ofMinutes(5))).isInstanceOf(
                SandboxException.class
            );
        }

        @Test
        @DisplayName("should re-interrupt thread and throw on InterruptedException during initial wait")
        void shouldReInterruptOnInitialWaitInterruption() throws Exception {
            // Block waitContainer so we can interrupt the calling thread
            when(containerOps.waitContainer(CONTAINER_ID)).thenAnswer(inv -> {
                Thread.sleep(10_000);
                return new DockerOperations.WaitResult(0);
            });

            Thread testThread = Thread.currentThread();
            // Schedule interruption after a short delay
            var interruptor = new Thread(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
                testThread.interrupt();
            });
            interruptor.start();

            assertThatThrownBy(() -> manager.waitForCompletion(CONTAINER_ID, Duration.ofMinutes(5)))
                .isInstanceOf(SandboxException.class)
                .hasMessageContaining("Interrupted");

            // Thread interrupt flag should be restored
            assertThat(Thread.interrupted()).isTrue(); // also clears it
            interruptor.join(1000);
        }

        @Test
        @DisplayName("should handle post-timeout second wait failure gracefully")
        void shouldHandlePostTimeoutWaitFailure() {
            // waitContainer blocks forever, triggering timeout, then post-stop wait also fails
            when(containerOps.waitContainer(CONTAINER_ID)).thenAnswer(inv -> {
                Thread.sleep(60_000);
                return new DockerOperations.WaitResult(0);
            });

            SandboxContainerManager.WaitOutcome outcome = manager.waitForCompletion(
                CONTAINER_ID,
                Duration.ofMillis(50)
            );

            // Should return SIGKILL exit code because post-stop wait times out
            assertThat(outcome.timedOut()).isTrue();
            assertThat(outcome.exitCode()).isEqualTo(137);
        }

        @Test
        @DisplayName("should tolerate stopContainer failure during timeout")
        void shouldTolerateStopFailureDuringTimeout() {
            when(containerOps.waitContainer(CONTAINER_ID)).thenAnswer(inv -> {
                Thread.sleep(60_000);
                return new DockerOperations.WaitResult(0);
            });
            doThrow(new SandboxException("stop failed")).when(containerOps).stopContainer(eq(CONTAINER_ID), anyInt());

            SandboxContainerManager.WaitOutcome outcome = manager.waitForCompletion(
                CONTAINER_ID,
                Duration.ofMillis(50)
            );

            // Should still return timeout result even if stop failed
            assertThat(outcome.timedOut()).isTrue();
        }
    }

    @Nested
    @DisplayName("getLogs")
    class GetLogs {

        @Test
        @DisplayName("should return logs from container")
        void shouldReturnLogs() {
            when(containerOps.getLogs(CONTAINER_ID, 100)).thenReturn("test output\n");

            String logs = manager.getLogs(CONTAINER_ID, 100);

            assertThat(logs).isEqualTo("test output\n");
        }

        @Test
        @DisplayName("should return empty string on failure")
        void shouldReturnEmptyOnFailure() {
            when(containerOps.getLogs(CONTAINER_ID, 100)).thenThrow(new RuntimeException("Docker error"));

            String logs = manager.getLogs(CONTAINER_ID, 100);

            assertThat(logs).isEmpty();
        }
    }

    @Nested
    @DisplayName("forceRemove")
    class ForceRemove {

        @Test
        @DisplayName("should delegate to containerOps with force=true")
        void shouldDelegateForceRemove() {
            manager.forceRemove(CONTAINER_ID);

            verify(containerOps).removeContainer(CONTAINER_ID, true);
        }
    }

    @Nested
    @DisplayName("listManagedContainers")
    class ListManagedContainers {

        @Test
        @DisplayName("should list containers by managed label")
        void shouldListByManagedLabel() {
            when(containerOps.listContainersByLabel("hephaestus.managed", "true")).thenReturn(
                List.of(new DockerOperations.ContainerInfo("c1", "/test", Map.of(), "running"))
            );

            var containers = manager.listManagedContainers();

            assertThat(containers).hasSize(1);
            assertThat(containers.get(0).id()).isEqualTo("c1");
        }
    }
}
