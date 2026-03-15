package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.agent.sandbox.SandboxProperties;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.NetworkPolicy;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.ResourceLimits;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxCancelledException;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxException;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxResult;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxSpec;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SecurityProfile;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

@DisplayName("DockerSandboxAdapter")
class DockerSandboxAdapterTest extends BaseUnitTest {

    @Mock
    private SandboxNetworkManager networkManager;

    @Mock
    private SandboxWorkspaceManager workspaceManager;

    @Mock
    private SandboxContainerManager containerManager;

    @Mock
    private ContainerSecurityPolicy securityPolicy;

    private DockerSandboxAdapter sandboxAdapter;
    private SimpleMeterRegistry meterRegistry;

    private static final UUID JOB_ID = UUID.randomUUID();
    private static final String NETWORK_ID = "net-123abc";
    private static final String CONTAINER_ID = "container-456def";
    private static final String APP_SERVER_IP = "172.18.0.2";

    /** Reusable default host config — avoids 14-arg constructor duplication across tests. */
    private static final DockerOperations.HostConfigSpec DEFAULT_HOST_CONFIG = new DockerOperations.HostConfigSpec(
        4L * 1024 * 1024 * 1024,
        4L * 1024 * 1024 * 1024,
        2_000_000_000L,
        256,
        true,
        false,
        List.of("ALL"),
        List.of(),
        Map.of(),
        List.of(),
        "private",
        "none",
        null,
        Map.of()
    );

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
        sandboxAdapter = new DockerSandboxAdapter(
            networkManager,
            workspaceManager,
            containerManager,
            securityPolicy,
            properties,
            meterRegistry
        );
    }

    private SandboxSpec createSpec() {
        return createSpec(false);
    }

    private SandboxSpec createSpec(boolean allowInternet) {
        return new SandboxSpec(
            JOB_ID,
            "alpine:latest",
            List.of("echo", "hello"),
            Map.of("FOO", "bar"),
            new NetworkPolicy(allowInternet, null, "test-token"),
            ResourceLimits.DEFAULT,
            SecurityProfile.DEFAULT,
            Map.of(".prompt", "test prompt".getBytes()),
            "/workspace/.output"
        );
    }

    private void setupHappyPath() {
        when(networkManager.createJobNetwork(eq(JOB_ID), eq(false))).thenReturn(NETWORK_ID);
        when(networkManager.connectAppServer(NETWORK_ID)).thenReturn(APP_SERVER_IP);
        when(securityPolicy.buildHostConfig(any(), any(), any())).thenReturn(
            DEFAULT_HOST_CONFIG
        );
        when(securityPolicy.buildLabels(JOB_ID)).thenReturn(
            Map.of("hephaestus.managed", "true", "hephaestus.job-id", JOB_ID.toString())
        );
        when(containerManager.createContainer(any())).thenReturn(CONTAINER_ID);
        when(containerManager.waitForCompletion(eq(CONTAINER_ID), any())).thenReturn(
            new SandboxContainerManager.WaitOutcome(0, false)
        );
        when(workspaceManager.collectOutput(eq(CONTAINER_ID), anyString())).thenReturn(
            Map.of("result.json", "{}".getBytes())
        );
        when(containerManager.getLogs(eq(CONTAINER_ID), anyInt())).thenReturn("hello\n");
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("should execute 4-phase lifecycle and return result")
        void shouldExecuteFullLifecycle() {
            setupHappyPath();

            SandboxResult result = sandboxAdapter.execute(createSpec());

            assertThat(result.exitCode()).isZero();
            assertThat(result.timedOut()).isFalse();
            assertThat(result.outputFiles()).containsKey("result.json");
            assertThat(result.logs()).isEqualTo("hello\n");
            assertThat(result.duration()).isPositive();

            // Verify all 4 phases
            verify(networkManager).createJobNetwork(JOB_ID, false);
            verify(networkManager).connectAppServer(NETWORK_ID);
            verify(containerManager).createContainer(any());
            verify(workspaceManager).injectFiles(eq(CONTAINER_ID), any());
            verify(containerManager).startContainer(CONTAINER_ID);
            verify(containerManager).waitForCompletion(eq(CONTAINER_ID), any());
            verify(workspaceManager).collectOutput(CONTAINER_ID, "/workspace/.output");
            verify(containerManager).getLogs(CONTAINER_ID, 500);

            // Verify cleanup
            verify(containerManager).forceRemove(CONTAINER_ID);
            verify(networkManager).disconnectAppServer(NETWORK_ID);
            verify(networkManager).removeNetwork(NETWORK_ID);
        }

        @Test
        @DisplayName("should inject LLM proxy URL from app-server IP when no explicit URL set")
        void shouldInjectDefaultLlmProxyUrl() {
            setupHappyPath();

            SandboxSpec spec = new SandboxSpec(
                JOB_ID,
                "alpine:latest",
                List.of("echo"),
                Map.of(),
                new NetworkPolicy(false, null, "token-123"),
                ResourceLimits.DEFAULT,
                SecurityProfile.DEFAULT,
                Map.of(".prompt", "test".getBytes()),
                "/workspace/.output"
            );

            sandboxAdapter.execute(spec);

            ArgumentCaptor<DockerOperations.ContainerSpec> captor = ArgumentCaptor.forClass(
                DockerOperations.ContainerSpec.class
            );
            verify(containerManager).createContainer(captor.capture());

            Map<String, String> env = captor.getValue().environment();
            assertThat(env).containsEntry("LLM_PROXY_URL", "http://172.18.0.2:8080");
            assertThat(env).containsEntry("LLM_PROXY_TOKEN", "token-123");
        }

        @Test
        @DisplayName("should resolve proxy URL template placeholder")
        void shouldResolveProxyUrlPlaceholder() {
            setupHappyPath();

            SandboxSpec spec = new SandboxSpec(
                JOB_ID,
                "alpine:latest",
                List.of("echo"),
                Map.of(),
                new NetworkPolicy(false, "http://{appServerIp}:9090/v1", "tok"),
                ResourceLimits.DEFAULT,
                SecurityProfile.DEFAULT,
                Map.of(".prompt", "test".getBytes()),
                "/workspace/.output"
            );

            sandboxAdapter.execute(spec);

            ArgumentCaptor<DockerOperations.ContainerSpec> captor = ArgumentCaptor.forClass(
                DockerOperations.ContainerSpec.class
            );
            verify(containerManager).createContainer(captor.capture());

            assertThat(captor.getValue().environment()).containsEntry("LLM_PROXY_URL", "http://172.18.0.2:9090/v1");
        }

        @Test
        @DisplayName("should use explicit proxy URL as-is when no placeholder")
        void shouldUseExplicitProxyUrl() {
            setupHappyPath();

            SandboxSpec spec = new SandboxSpec(
                JOB_ID,
                "alpine:latest",
                List.of("echo"),
                Map.of(),
                new NetworkPolicy(false, "https://my-proxy.example.com/api", null),
                ResourceLimits.DEFAULT,
                SecurityProfile.DEFAULT,
                Map.of(".prompt", "test".getBytes()),
                "/workspace/.output"
            );

            sandboxAdapter.execute(spec);

            ArgumentCaptor<DockerOperations.ContainerSpec> captor = ArgumentCaptor.forClass(
                DockerOperations.ContainerSpec.class
            );
            verify(containerManager).createContainer(captor.capture());

            assertThat(captor.getValue().environment()).containsEntry(
                "LLM_PROXY_URL",
                "https://my-proxy.example.com/api"
            );
        }

        @Test
        @DisplayName("should merge user-provided environment variables")
        void shouldMergeUserEnvironment() {
            setupHappyPath();

            sandboxAdapter.execute(createSpec());

            ArgumentCaptor<DockerOperations.ContainerSpec> captor = ArgumentCaptor.forClass(
                DockerOperations.ContainerSpec.class
            );
            verify(containerManager).createContainer(captor.capture());

            // createSpec() passes Map.of("FOO", "bar")
            assertThat(captor.getValue().environment()).containsEntry("FOO", "bar");
        }

        @Test
        @DisplayName("should create internet-enabled network when allowed")
        void shouldCreateInternetNetwork() {
            when(networkManager.createJobNetwork(eq(JOB_ID), eq(true))).thenReturn(NETWORK_ID);
            when(networkManager.connectAppServer(NETWORK_ID)).thenReturn(APP_SERVER_IP);
            when(securityPolicy.buildHostConfig(any(), any(), any())).thenReturn(
                new DockerOperations.HostConfigSpec(
                    4L * 1024 * 1024 * 1024,
                    4L * 1024 * 1024 * 1024,
                    2_000_000_000L,
                    256,
                    true,
                    false,
                    List.of("ALL"),
                    List.of(),
                    Map.of(),
                    List.of(),
                    "private",
                    "none",
                    null,
                    Map.of()
                )
            );
            when(securityPolicy.buildLabels(JOB_ID)).thenReturn(Map.of("hephaestus.managed", "true"));
            when(containerManager.createContainer(any())).thenReturn(CONTAINER_ID);
            when(containerManager.waitForCompletion(eq(CONTAINER_ID), any())).thenReturn(
                new SandboxContainerManager.WaitOutcome(0, false)
            );
            when(workspaceManager.collectOutput(eq(CONTAINER_ID), anyString())).thenReturn(Map.of());
            when(containerManager.getLogs(eq(CONTAINER_ID), anyInt())).thenReturn("");

            sandboxAdapter.execute(createSpec(true));

            verify(networkManager).createJobNetwork(JOB_ID, true);
        }

        @Test
        @DisplayName("should skip file injection when no input files")
        void shouldSkipInjectionWhenNoFiles() {
            setupHappyPath();

            SandboxSpec specWithoutFiles = new SandboxSpec(
                JOB_ID,
                "alpine:latest",
                List.of("echo"),
                Map.of(),
                new NetworkPolicy(false, null, null),
                ResourceLimits.DEFAULT,
                SecurityProfile.DEFAULT,
                Map.of(),
                "/workspace/.output"
            );

            sandboxAdapter.execute(specWithoutFiles);

            verify(workspaceManager, never()).injectFiles(anyString(), any());
        }

        @Test
        @DisplayName("should handle null networkPolicy gracefully")
        void shouldHandleNullNetworkPolicy() {
            when(networkManager.createJobNetwork(eq(JOB_ID), eq(false))).thenReturn(NETWORK_ID);
            when(networkManager.connectAppServer(NETWORK_ID)).thenReturn(APP_SERVER_IP);
            when(securityPolicy.buildHostConfig(any(), any(), any())).thenReturn(DEFAULT_HOST_CONFIG);
            when(securityPolicy.buildLabels(JOB_ID)).thenReturn(Map.of());
            when(containerManager.createContainer(any())).thenReturn(CONTAINER_ID);
            when(containerManager.waitForCompletion(eq(CONTAINER_ID), any())).thenReturn(
                new SandboxContainerManager.WaitOutcome(0, false)
            );
            when(workspaceManager.collectOutput(eq(CONTAINER_ID), anyString())).thenReturn(Map.of());
            when(containerManager.getLogs(eq(CONTAINER_ID), anyInt())).thenReturn("");

            SandboxSpec spec = new SandboxSpec(
                JOB_ID,
                "alpine:latest",
                List.of("echo"),
                Map.of("USER_VAR", "value"),
                null, // null networkPolicy
                ResourceLimits.DEFAULT,
                SecurityProfile.DEFAULT,
                Map.of(),
                "/workspace/.output"
            );

            SandboxResult result = sandboxAdapter.execute(spec);
            assertThat(result.exitCode()).isZero();

            // Verify no LLM proxy vars injected
            ArgumentCaptor<DockerOperations.ContainerSpec> captor = ArgumentCaptor.forClass(
                DockerOperations.ContainerSpec.class
            );
            verify(containerManager).createContainer(captor.capture());
            Map<String, String> env = captor.getValue().environment();
            assertThat(env).doesNotContainKey("LLM_PROXY_URL");
            assertThat(env).doesNotContainKey("LLM_PROXY_TOKEN");
            assertThat(env).containsEntry("USER_VAR", "value");
        }

        @Test
        @DisplayName("should use default output path when spec outputPath is null")
        void shouldUseDefaultOutputPath() {
            setupHappyPath();

            SandboxSpec spec = new SandboxSpec(
                JOB_ID,
                "alpine:latest",
                List.of("echo"),
                Map.of(),
                new NetworkPolicy(false, null, null),
                ResourceLimits.DEFAULT,
                SecurityProfile.DEFAULT,
                Map.of(),
                null // null outputPath
            );

            sandboxAdapter.execute(spec);

            verify(workspaceManager).collectOutput(CONTAINER_ID, "/workspace/.output");
        }
    }

    @Nested
    @DisplayName("Timeout handling")
    class TimeoutHandling {

        private void setupTimeoutPath() {
            when(networkManager.createJobNetwork(eq(JOB_ID), eq(false))).thenReturn(NETWORK_ID);
            when(networkManager.connectAppServer(NETWORK_ID)).thenReturn(APP_SERVER_IP);
            when(securityPolicy.buildHostConfig(any(), any(), any())).thenReturn(
                new DockerOperations.HostConfigSpec(
                    4L * 1024 * 1024 * 1024,
                    4L * 1024 * 1024 * 1024,
                    2_000_000_000L,
                    256,
                    true,
                    false,
                    List.of("ALL"),
                    List.of(),
                    Map.of(),
                    List.of(),
                    "private",
                    "none",
                    null,
                    Map.of()
                )
            );
            when(securityPolicy.buildLabels(JOB_ID)).thenReturn(
                Map.of("hephaestus.managed", "true", "hephaestus.job-id", JOB_ID.toString())
            );
            when(containerManager.createContainer(any())).thenReturn(CONTAINER_ID);
            when(containerManager.waitForCompletion(eq(CONTAINER_ID), any())).thenReturn(
                new SandboxContainerManager.WaitOutcome(137, true)
            );
            when(containerManager.getLogs(eq(CONTAINER_ID), anyInt())).thenReturn("timeout\n");
        }

        @Test
        @DisplayName("should return timedOut=true when container exceeds timeout")
        void shouldReturnTimedOutOnTimeout() {
            setupTimeoutPath();
            when(workspaceManager.collectOutput(eq(CONTAINER_ID), anyString())).thenReturn(Map.of());

            SandboxResult result = sandboxAdapter.execute(createSpec());

            assertThat(result.timedOut()).isTrue();
            assertThat(result.exitCode()).isEqualTo(137);
        }

        @Test
        @DisplayName("should still collect output on timeout")
        void shouldCollectOutputOnTimeout() {
            setupTimeoutPath();
            when(workspaceManager.collectOutput(eq(CONTAINER_ID), anyString())).thenReturn(
                Map.of("partial.json", "{}".getBytes())
            );

            SandboxResult result = sandboxAdapter.execute(createSpec());

            assertThat(result.outputFiles()).containsKey("partial.json");
            verify(workspaceManager).collectOutput(CONTAINER_ID, "/workspace/.output");
        }
    }

    @Nested
    @DisplayName("Failure handling")
    class FailureHandling {

        @Test
        @DisplayName("should throw SandboxException on network creation failure")
        void shouldThrowOnNetworkFailure() {
            when(networkManager.createJobNetwork(any(), eq(false))).thenThrow(
                new SandboxException("Network creation failed")
            );

            assertThatThrownBy(() -> sandboxAdapter.execute(createSpec()))
                .isInstanceOf(SandboxException.class)
                .hasMessageContaining("Network creation failed");
        }

        @Test
        @DisplayName("should cleanup on container creation failure")
        void shouldCleanupOnContainerFailure() {
            when(networkManager.createJobNetwork(eq(JOB_ID), eq(false))).thenReturn(NETWORK_ID);
            when(networkManager.connectAppServer(NETWORK_ID)).thenReturn(APP_SERVER_IP);
            when(securityPolicy.buildHostConfig(any(), any(), any())).thenReturn(
                new DockerOperations.HostConfigSpec(
                    4L * 1024 * 1024 * 1024,
                    4L * 1024 * 1024 * 1024,
                    2_000_000_000L,
                    256,
                    true,
                    false,
                    List.of("ALL"),
                    List.of(),
                    Map.of(),
                    List.of(),
                    "private",
                    "none",
                    null,
                    Map.of()
                )
            );
            when(securityPolicy.buildLabels(JOB_ID)).thenReturn(Map.of());
            when(containerManager.createContainer(any())).thenThrow(new SandboxException("Image not found"));

            assertThatThrownBy(() -> sandboxAdapter.execute(createSpec())).isInstanceOf(SandboxException.class);

            // Network should still be cleaned up
            verify(networkManager).disconnectAppServer(NETWORK_ID);
            verify(networkManager).removeNetwork(NETWORK_ID);
        }

        @Test
        @DisplayName("should tolerate partial cleanup failure")
        void shouldToleratePartialCleanupFailure() {
            setupHappyPath();
            doThrow(new SandboxException("Container stuck")).when(containerManager).forceRemove(CONTAINER_ID);

            // Should not throw despite cleanup failure
            SandboxResult result = sandboxAdapter.execute(createSpec());
            assertThat(result.exitCode()).isZero();

            // Other cleanup steps should still execute
            verify(networkManager).disconnectAppServer(NETWORK_ID);
            verify(networkManager).removeNetwork(NETWORK_ID);
        }

        @Test
        @DisplayName("should capture container logs on error path before cleanup")
        void shouldCaptureLogsOnError() {
            when(networkManager.createJobNetwork(eq(JOB_ID), eq(false))).thenReturn(NETWORK_ID);
            when(networkManager.connectAppServer(NETWORK_ID)).thenReturn(APP_SERVER_IP);
            when(securityPolicy.buildHostConfig(any(), any(), any())).thenReturn(
                new DockerOperations.HostConfigSpec(
                    4L * 1024 * 1024 * 1024,
                    4L * 1024 * 1024 * 1024,
                    2_000_000_000L,
                    256,
                    true,
                    false,
                    List.of("ALL"),
                    List.of(),
                    Map.of(),
                    List.of(),
                    "private",
                    "none",
                    null,
                    Map.of()
                )
            );
            when(securityPolicy.buildLabels(JOB_ID)).thenReturn(Map.of());
            when(containerManager.createContainer(any())).thenReturn(CONTAINER_ID);
            when(containerManager.waitForCompletion(eq(CONTAINER_ID), any())).thenThrow(
                new SandboxException("Docker daemon lost")
            );
            when(containerManager.getLogs(eq(CONTAINER_ID), anyInt())).thenReturn("error logs here");

            try {
                sandboxAdapter.execute(createSpec());
            } catch (SandboxException ignored) {}

            // captureLogsOnError should call getLogs before cleanup removes the container
            verify(containerManager).getLogs(eq(CONTAINER_ID), anyInt());
        }

        @Test
        @DisplayName("should use SecurityProfile.DEFAULT when spec has null securityProfile")
        void shouldUseDefaultSecurityProfileWhenNull() {
            when(networkManager.createJobNetwork(eq(JOB_ID), eq(false))).thenReturn(NETWORK_ID);
            when(networkManager.connectAppServer(NETWORK_ID)).thenReturn(APP_SERVER_IP);
            when(securityPolicy.buildHostConfig(any(), any(), any())).thenReturn(
                new DockerOperations.HostConfigSpec(
                    4L * 1024 * 1024 * 1024,
                    4L * 1024 * 1024 * 1024,
                    2_000_000_000L,
                    256,
                    true,
                    false,
                    List.of("ALL"),
                    List.of(),
                    Map.of(),
                    List.of(),
                    "private",
                    "none",
                    null,
                    Map.of()
                )
            );
            when(securityPolicy.buildLabels(JOB_ID)).thenReturn(Map.of());
            when(containerManager.createContainer(any())).thenReturn(CONTAINER_ID);
            when(containerManager.waitForCompletion(eq(CONTAINER_ID), any())).thenReturn(
                new SandboxContainerManager.WaitOutcome(0, false)
            );
            when(workspaceManager.collectOutput(eq(CONTAINER_ID), anyString())).thenReturn(Map.of());
            when(containerManager.getLogs(eq(CONTAINER_ID), anyInt())).thenReturn("");

            // Create spec with null securityProfile — should NOT NPE
            SandboxSpec specWithNullSecurity = new SandboxSpec(
                JOB_ID,
                "alpine:latest",
                List.of("echo"),
                Map.of(),
                new NetworkPolicy(false, null, null),
                ResourceLimits.DEFAULT,
                null,
                Map.of(),
                "/workspace/.output"
            );

            SandboxResult result = sandboxAdapter.execute(specWithNullSecurity);
            assertThat(result.exitCode()).isZero();

            // Verify SecurityProfile.DEFAULT was used
            ArgumentCaptor<de.tum.in.www1.hephaestus.agent.sandbox.spi.SecurityProfile> secCaptor =
                ArgumentCaptor.forClass(de.tum.in.www1.hephaestus.agent.sandbox.spi.SecurityProfile.class);
            verify(securityPolicy).buildHostConfig(secCaptor.capture(), any(), any());
            assertThat(secCaptor.getValue()).isEqualTo(de.tum.in.www1.hephaestus.agent.sandbox.spi.SecurityProfile.DEFAULT);
        }
    }

    @Nested
    @DisplayName("Cancellation")
    class Cancellation {

        @Test
        @DisplayName("should throw SandboxCancelledException on cancellation between phases")
        void shouldThrowOnCancellation() {
            when(networkManager.createJobNetwork(eq(JOB_ID), eq(false))).thenAnswer(invocation -> {
                // Simulate cancellation during network creation
                sandboxAdapter.cancel(JOB_ID);
                return NETWORK_ID;
            });
            when(networkManager.connectAppServer(NETWORK_ID)).thenReturn(APP_SERVER_IP);

            assertThatThrownBy(() -> sandboxAdapter.execute(createSpec()))
                .isInstanceOf(SandboxCancelledException.class)
                .hasMessageContaining("cancelled");
        }

        @Test
        @DisplayName("cancel should stop running container and cause SandboxCancelledException")
        void shouldStopRunningContainer() throws Exception {
            CountDownLatch containerStarted = new CountDownLatch(1);
            CountDownLatch cancelDone = new CountDownLatch(1);
            var thrownException = new java.util.concurrent.atomic.AtomicReference<Exception>();

            when(networkManager.createJobNetwork(eq(JOB_ID), eq(false))).thenReturn(NETWORK_ID);
            when(networkManager.connectAppServer(NETWORK_ID)).thenReturn(APP_SERVER_IP);
            when(securityPolicy.buildHostConfig(any(), any(), any())).thenReturn(
                new DockerOperations.HostConfigSpec(
                    4L * 1024 * 1024 * 1024,
                    4L * 1024 * 1024 * 1024,
                    2_000_000_000L,
                    256,
                    true,
                    false,
                    List.of("ALL"),
                    List.of(),
                    Map.of(),
                    List.of(),
                    "private",
                    "none",
                    null,
                    Map.of()
                )
            );
            when(securityPolicy.buildLabels(JOB_ID)).thenReturn(Map.of("hephaestus.managed", "true"));
            when(containerManager.createContainer(any())).thenReturn(CONTAINER_ID);

            // Block on waitForCompletion until cancel is done
            when(containerManager.waitForCompletion(eq(CONTAINER_ID), any())).thenAnswer(inv -> {
                containerStarted.countDown();
                cancelDone.await(5, TimeUnit.SECONDS);
                return new SandboxContainerManager.WaitOutcome(137, false);
            });
            // Note: no getLogs stub needed — cancel throws SandboxCancelledException
            // before reaching the COLLECT phase or captureLogsOnError()

            Thread bg = new Thread(() -> {
                try {
                    sandboxAdapter.execute(createSpec());
                } catch (Exception e) {
                    thrownException.set(e);
                }
            });
            bg.start();

            assertThat(containerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            sandboxAdapter.cancel(JOB_ID);
            cancelDone.countDown();
            bg.join(5000);
            assertThat(bg.isAlive()).isFalse();

            verify(containerManager).stopContainer(CONTAINER_ID);
            // After waitForCompletion returns, checkCancelled() should throw
            assertThat(thrownException.get()).isInstanceOf(SandboxCancelledException.class);
        }

        @Test
        @DisplayName("cancel should be no-op for unknown job")
        void shouldNoOpForUnknownJob() {
            // Should not throw
            sandboxAdapter.cancel(UUID.randomUUID());
        }
    }

    @Nested
    @DisplayName("Health check")
    class HealthCheck {

        @Test
        @DisplayName("should return true when Docker is reachable")
        void shouldReturnTrueWhenHealthy() {
            when(containerManager.ping()).thenReturn(true);
            assertThat(sandboxAdapter.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("should return false when Docker is unreachable")
        void shouldReturnFalseWhenUnhealthy() {
            when(containerManager.ping()).thenReturn(false);
            assertThat(sandboxAdapter.isHealthy()).isFalse();
        }
    }

    @Nested
    @DisplayName("Metrics")
    class Metrics {

        @Test
        @DisplayName("should increment success counter on successful execution")
        void shouldIncrementSuccessCounter() {
            setupHappyPath();

            sandboxAdapter.execute(createSpec());

            assertThat(meterRegistry.counter("sandbox.executions", "outcome", "success").count()).isEqualTo(1.0);
            assertThat(meterRegistry.counter("sandbox.executions", "outcome", "failure").count()).isZero();
        }

        @Test
        @DisplayName("should increment timeout counter on timeout")
        void shouldIncrementTimeoutCounter() {
            when(networkManager.createJobNetwork(eq(JOB_ID), eq(false))).thenReturn(NETWORK_ID);
            when(networkManager.connectAppServer(NETWORK_ID)).thenReturn(APP_SERVER_IP);
            when(securityPolicy.buildHostConfig(any(), any(), any())).thenReturn(
                new DockerOperations.HostConfigSpec(
                    4L * 1024 * 1024 * 1024,
                    4L * 1024 * 1024 * 1024,
                    2_000_000_000L,
                    256,
                    true,
                    false,
                    List.of("ALL"),
                    List.of(),
                    Map.of(),
                    List.of(),
                    "private",
                    "none",
                    null,
                    Map.of()
                )
            );
            when(securityPolicy.buildLabels(JOB_ID)).thenReturn(Map.of());
            when(containerManager.createContainer(any())).thenReturn(CONTAINER_ID);
            when(containerManager.waitForCompletion(eq(CONTAINER_ID), any())).thenReturn(
                new SandboxContainerManager.WaitOutcome(137, true)
            );
            when(workspaceManager.collectOutput(eq(CONTAINER_ID), anyString())).thenReturn(Map.of());
            when(containerManager.getLogs(eq(CONTAINER_ID), anyInt())).thenReturn("");

            sandboxAdapter.execute(createSpec());

            assertThat(meterRegistry.counter("sandbox.executions", "outcome", "timeout").count()).isEqualTo(1.0);
            assertThat(meterRegistry.counter("sandbox.executions", "outcome", "success").count()).isZero();
        }

        @Test
        @DisplayName("should increment failure counter on error")
        void shouldIncrementFailureCounter() {
            when(networkManager.createJobNetwork(any(), eq(false))).thenThrow(new SandboxException("boom"));

            try {
                sandboxAdapter.execute(createSpec());
            } catch (SandboxException ignored) {}

            assertThat(meterRegistry.counter("sandbox.executions", "outcome", "failure").count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should increment cancelled counter on cancellation")
        void shouldIncrementCancelledCounter() {
            when(networkManager.createJobNetwork(eq(JOB_ID), eq(false))).thenAnswer(invocation -> {
                sandboxAdapter.cancel(JOB_ID);
                return NETWORK_ID;
            });
            when(networkManager.connectAppServer(NETWORK_ID)).thenReturn(APP_SERVER_IP);

            try {
                sandboxAdapter.execute(createSpec());
            } catch (SandboxCancelledException ignored) {}

            assertThat(meterRegistry.counter("sandbox.executions", "outcome", "cancelled").count()).isEqualTo(1.0);
            assertThat(meterRegistry.counter("sandbox.executions", "outcome", "failure").count()).isZero();
        }

        @Test
        @DisplayName("should record execution duration for all outcomes")
        void shouldRecordDurationAlways() {
            when(networkManager.createJobNetwork(any(), eq(false))).thenThrow(new SandboxException("fail"));

            try {
                sandboxAdapter.execute(createSpec());
            } catch (SandboxException ignored) {}

            assertThat(meterRegistry.timer("sandbox.execution.duration").count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should increment cleanup failure counter with step tag when cleanup fails")
        void shouldIncrementCleanupFailureCounterWithStep() {
            setupHappyPath();
            doThrow(new SandboxException("stuck")).when(containerManager).forceRemove(CONTAINER_ID);

            sandboxAdapter.execute(createSpec());

            assertThat(meterRegistry.counter("sandbox.cleanup.failures", "step", "remove container").count()).isEqualTo(
                1.0
            );
        }

        @Test
        @DisplayName("should track active containers gauge during execution")
        void shouldTrackActiveContainersGauge() throws Exception {
            CountDownLatch inExecution = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);

            when(networkManager.createJobNetwork(eq(JOB_ID), eq(false))).thenReturn(NETWORK_ID);
            when(networkManager.connectAppServer(NETWORK_ID)).thenReturn(APP_SERVER_IP);
            when(securityPolicy.buildHostConfig(any(), any(), any())).thenReturn(
                new DockerOperations.HostConfigSpec(
                    4L * 1024 * 1024 * 1024,
                    4L * 1024 * 1024 * 1024,
                    2_000_000_000L,
                    256,
                    true,
                    false,
                    List.of("ALL"),
                    List.of(),
                    Map.of(),
                    List.of(),
                    "private",
                    "none",
                    null,
                    Map.of()
                )
            );
            when(securityPolicy.buildLabels(JOB_ID)).thenReturn(Map.of("hephaestus.managed", "true"));
            when(containerManager.createContainer(any())).thenReturn(CONTAINER_ID);
            when(containerManager.waitForCompletion(eq(CONTAINER_ID), any())).thenAnswer(inv -> {
                inExecution.countDown();
                release.await(5, TimeUnit.SECONDS);
                return new SandboxContainerManager.WaitOutcome(0, false);
            });
            when(workspaceManager.collectOutput(eq(CONTAINER_ID), anyString())).thenReturn(Map.of());
            when(containerManager.getLogs(eq(CONTAINER_ID), anyInt())).thenReturn("");

            Thread bg = new Thread(() -> {
                try {
                    sandboxAdapter.execute(createSpec());
                } catch (Exception ignored) {}
            });
            bg.start();

            assertThat(inExecution.await(5, TimeUnit.SECONDS)).isTrue();

            // During execution, gauge should be 1
            assertThat(meterRegistry.get("sandbox.containers.active").gauge().value()).isEqualTo(1.0);

            release.countDown();
            bg.join(5000);
            assertThat(bg.isAlive()).isFalse();

            // After execution, gauge should be 0
            assertThat(meterRegistry.get("sandbox.containers.active").gauge().value()).isZero();
        }
    }

    @Nested
    @DisplayName("Duplicate job guard")
    class DuplicateJobGuard {

        @Test
        @DisplayName("should reject duplicate jobId")
        void shouldRejectDuplicateJobId() throws Exception {
            CountDownLatch enteredExecute = new CountDownLatch(1);
            CountDownLatch releaseBlock = new CountDownLatch(1);

            // First execute blocks in network creation; signals when registered
            when(networkManager.createJobNetwork(eq(JOB_ID), eq(false))).thenAnswer(inv -> {
                enteredExecute.countDown();
                releaseBlock.await(5, TimeUnit.SECONDS);
                return NETWORK_ID;
            });

            Thread bg = new Thread(() -> {
                try {
                    sandboxAdapter.execute(createSpec());
                } catch (Exception ignored) {}
            });
            bg.start();

            // Wait deterministically for the first execute to register
            assertThat(enteredExecute.await(5, TimeUnit.SECONDS)).isTrue();

            // Second execution with same jobId should be rejected
            assertThatThrownBy(() -> sandboxAdapter.execute(createSpec()))
                .isInstanceOf(SandboxException.class)
                .hasMessageContaining("already executing");

            releaseBlock.countDown();
            bg.join(5000);
            assertThat(bg.isAlive()).isFalse();
        }
    }
}
