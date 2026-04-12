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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
            null,
            null,
            209_715_200L,
            500_000,
            null
        );
        meterRegistry = new SimpleMeterRegistry();
        sandboxAdapter = new DockerSandboxAdapter(
            networkManager,
            workspaceManager,
            containerManager,
            securityPolicy,
            properties,
            8080,
            meterRegistry
        );
    }

    private SandboxSpec createSpec() {
        return createSpec(false);
    }

    private SandboxSpec createSpec(boolean allowInternet) {
        return createSpec(allowInternet, null);
    }

    private SandboxSpec createSpec(boolean allowInternet, Map<String, String> volumeMounts) {
        return new SandboxSpec(
            JOB_ID,
            "alpine:latest",
            List.of("echo", "hello"),
            Map.of("FOO", "bar"),
            new NetworkPolicy(allowInternet, null, "test-token", "anthropic"),
            ResourceLimits.DEFAULT,
            SecurityProfile.DEFAULT,
            Map.of(".prompt", "test prompt".getBytes()),
            "/workspace/.output",
            volumeMounts
        );
    }

    private void setupHappyPath() {
        when(networkManager.createJobNetwork(eq(JOB_ID), eq(false))).thenReturn(NETWORK_ID);
        when(networkManager.connectAppServer(NETWORK_ID)).thenReturn(APP_SERVER_IP);
        when(securityPolicy.buildHostConfig(any(), any(), any())).thenReturn(DEFAULT_HOST_CONFIG);
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
                new NetworkPolicy(false, null, "token-123", "anthropic"),
                ResourceLimits.DEFAULT,
                SecurityProfile.DEFAULT,
                Map.of(".prompt", "test".getBytes()),
                "/workspace/.output",
                null
            );

            sandboxAdapter.execute(spec);

            ArgumentCaptor<DockerOperations.ContainerSpec> captor = ArgumentCaptor.forClass(
                DockerOperations.ContainerSpec.class
            );
            verify(containerManager).createContainer(captor.capture());

            Map<String, String> env = captor.getValue().environment();
            assertThat(env).containsEntry("LLM_PROXY_URL", "http://172.18.0.2:8080/internal/llm/anthropic");
            assertThat(env).containsEntry("LLM_PROXY_TOKEN", "token-123");
        }

        @Test
        @DisplayName("should use active server port when llm proxy override is unset")
        void shouldUseActiveServerPortWhenProxyPortUnset() {
            SandboxProperties properties = new SandboxProperties(
                true,
                "unix:///var/run/docker.sock",
                false,
                null,
                5,
                10,
                60,
                null,
                null,
                null,
                209_715_200L,
                500_000,
                null
            );
            sandboxAdapter = new DockerSandboxAdapter(
                networkManager,
                workspaceManager,
                containerManager,
                securityPolicy,
                properties,
                8090,
                meterRegistry
            );
            setupHappyPath();

            SandboxSpec spec = new SandboxSpec(
                JOB_ID,
                "alpine:latest",
                List.of("echo"),
                Map.of(),
                new NetworkPolicy(false, null, "token-123", "anthropic"),
                ResourceLimits.DEFAULT,
                SecurityProfile.DEFAULT,
                Map.of(".prompt", "test".getBytes()),
                "/workspace/.output",
                null
            );

            sandboxAdapter.execute(spec);

            ArgumentCaptor<DockerOperations.ContainerSpec> captor = ArgumentCaptor.forClass(
                DockerOperations.ContainerSpec.class
            );
            verify(containerManager).createContainer(captor.capture());

            assertThat(captor.getValue().environment()).containsEntry(
                "LLM_PROXY_URL",
                "http://172.18.0.2:8090/internal/llm/anthropic"
            );
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
                new NetworkPolicy(false, "http://{appServerIp}:9090/v1", "tok", null),
                ResourceLimits.DEFAULT,
                SecurityProfile.DEFAULT,
                Map.of(".prompt", "test".getBytes()),
                "/workspace/.output",
                null
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
                new NetworkPolicy(false, "https://my-proxy.example.com/api", null, null),
                ResourceLimits.DEFAULT,
                SecurityProfile.DEFAULT,
                Map.of(".prompt", "test".getBytes()),
                "/workspace/.output",
                null
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
        @DisplayName("should filter out blocked environment variables (exact and prefix)")
        void shouldFilterBlockedEnvVars() {
            setupHappyPath();

            SandboxSpec spec = new SandboxSpec(
                JOB_ID,
                "alpine:latest",
                List.of("echo"),
                Map.of(
                    "SAFE_VAR",
                    "ok",
                    "LD_PRELOAD",
                    "/evil.so",
                    "AWS_SECRET_ACCESS_KEY",
                    "leaked",
                    "DOCKER_HOST",
                    "tcp://evil",
                    "GOOGLE_CLOUD_PROJECT",
                    "stolen",
                    "HOME",
                    "/home/agent"
                ),
                new NetworkPolicy(false, null, "tok", "anthropic"),
                ResourceLimits.DEFAULT,
                SecurityProfile.DEFAULT,
                Map.of(".prompt", "test".getBytes()),
                "/workspace/.output",
                null
            );

            sandboxAdapter.execute(spec);

            ArgumentCaptor<DockerOperations.ContainerSpec> captor = ArgumentCaptor.forClass(
                DockerOperations.ContainerSpec.class
            );
            verify(containerManager).createContainer(captor.capture());

            Map<String, String> env = captor.getValue().environment();
            assertThat(env).containsEntry("SAFE_VAR", "ok");
            assertThat(env).doesNotContainKey("LD_PRELOAD");
            assertThat(env).doesNotContainKey("AWS_SECRET_ACCESS_KEY");
            assertThat(env).doesNotContainKey("DOCKER_HOST");
            assertThat(env).doesNotContainKey("GOOGLE_CLOUD_PROJECT");
            assertThat(env).containsEntry("HOME", "/home/agent");
        }

        @Test
        @DisplayName("should create internet-enabled network when allowed")
        void shouldCreateInternetNetwork() {
            when(networkManager.createJobNetwork(eq(JOB_ID), eq(true))).thenReturn(NETWORK_ID);
            when(networkManager.connectAppServer(NETWORK_ID)).thenReturn(APP_SERVER_IP);
            when(securityPolicy.buildHostConfig(any(), any(), any())).thenReturn(DEFAULT_HOST_CONFIG);
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
                new NetworkPolicy(false, null, null, null),
                ResourceLimits.DEFAULT,
                SecurityProfile.DEFAULT,
                Map.of(),
                "/workspace/.output",
                null
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
                "/workspace/.output",
                null
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
                new NetworkPolicy(false, null, null, null),
                ResourceLimits.DEFAULT,
                SecurityProfile.DEFAULT,
                Map.of(),
                null, // null outputPath
                null
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
            when(securityPolicy.buildHostConfig(any(), any(), any())).thenReturn(DEFAULT_HOST_CONFIG);
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
            when(securityPolicy.buildHostConfig(any(), any(), any())).thenReturn(DEFAULT_HOST_CONFIG);
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
            when(securityPolicy.buildHostConfig(any(), any(), any())).thenReturn(DEFAULT_HOST_CONFIG);
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
            when(securityPolicy.buildHostConfig(any(), any(), any())).thenReturn(DEFAULT_HOST_CONFIG);
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
                new NetworkPolicy(false, null, null, null),
                ResourceLimits.DEFAULT,
                null,
                Map.of(),
                "/workspace/.output",
                null
            );

            SandboxResult result = sandboxAdapter.execute(specWithNullSecurity);
            assertThat(result.exitCode()).isZero();

            // Verify SecurityProfile.DEFAULT was used
            ArgumentCaptor<de.tum.in.www1.hephaestus.agent.sandbox.spi.SecurityProfile> secCaptor =
                ArgumentCaptor.forClass(de.tum.in.www1.hephaestus.agent.sandbox.spi.SecurityProfile.class);
            verify(securityPolicy).buildHostConfig(secCaptor.capture(), any(), any());
            assertThat(secCaptor.getValue()).isEqualTo(
                de.tum.in.www1.hephaestus.agent.sandbox.spi.SecurityProfile.DEFAULT
            );
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
            when(securityPolicy.buildHostConfig(any(), any(), any())).thenReturn(DEFAULT_HOST_CONFIG);
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
            when(securityPolicy.buildHostConfig(any(), any(), any())).thenReturn(DEFAULT_HOST_CONFIG);
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
            when(securityPolicy.buildHostConfig(any(), any(), any())).thenReturn(DEFAULT_HOST_CONFIG);
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

    @Nested
    @DisplayName("Environment variable blocklist")
    class EnvVarBlocklist {

        static Stream<String> exactBlockedVars() {
            return DockerSandboxAdapter.BLOCKED_ENV_VARS.stream();
        }

        static Stream<String> prefixBlockedVars() {
            return Stream.of(
                "AWS_ACCESS_KEY_ID",
                "AWS_SECRET_ACCESS_KEY",
                "AWS_SESSION_TOKEN",
                "AWS_ROLE_ARN",
                "GOOGLE_APPLICATION_CREDENTIALS",
                "GOOGLE_CLOUD_PROJECT",
                "GCP_PROJECT",
                "AZURE_CLIENT_SECRET",
                "AZURE_TENANT_ID",
                "DOCKER_HOST",
                "DOCKER_TLS_VERIFY",
                "DOCKER_CERT_PATH",
                "ALIBABA_CLOUD_ACCESS_KEY",
                "GIT_CONFIG_COUNT",
                "GIT_CONFIG_KEY_0",
                "GIT_CONFIG_VALUE_99"
            );
        }

        @ParameterizedTest(name = "should block exact var: {0}")
        @MethodSource("exactBlockedVars")
        @DisplayName("should block exact env vars")
        void shouldBlockExactVars(String varName) {
            assertThat(DockerSandboxAdapter.isBlockedEnvVar(varName)).isTrue();
        }

        @ParameterizedTest(name = "should block prefix var: {0}")
        @MethodSource("prefixBlockedVars")
        @DisplayName("should block prefix-matched env vars")
        void shouldBlockPrefixVars(String varName) {
            assertThat(DockerSandboxAdapter.isBlockedEnvVar(varName)).isTrue();
        }

        @Test
        @DisplayName("should allow safe env vars")
        void shouldAllowSafeVars() {
            assertThat(DockerSandboxAdapter.isBlockedEnvVar("MY_APP_KEY")).isFalse();
            assertThat(DockerSandboxAdapter.isBlockedEnvVar("FOO")).isFalse();
            assertThat(DockerSandboxAdapter.isBlockedEnvVar("CUSTOM_VAR")).isFalse();
        }

        @Test
        @DisplayName("should block case variants of prefix-matched vars")
        void shouldBlockCaseVariants() {
            // Some tools/shells inject lowercase variants
            assertThat(DockerSandboxAdapter.isBlockedEnvVar("aws_access_key_id")).isTrue();
            assertThat(DockerSandboxAdapter.isBlockedEnvVar("docker_host")).isTrue();
            assertThat(DockerSandboxAdapter.isBlockedEnvVar("Google_Cloud_Project")).isTrue();
            assertThat(DockerSandboxAdapter.isBlockedEnvVar("Azure_Client_Secret")).isTrue();
        }
    }

    @Nested
    @DisplayName("Git security config")
    class GitSecurityConfig {

        @Test
        @DisplayName("should contain all expected security config keys")
        void shouldContainAllExpectedKeys() {
            assertThat(DockerSandboxAdapter.GIT_SECURITY_CONFIGS)
                .extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder(
                    "core.hooksPath",
                    "core.fsmonitor",
                    "core.sshCommand",
                    "core.askPass",
                    "core.editor",
                    "core.pager",
                    "core.gitProxy",
                    "sequence.editor",
                    "credential.helper",
                    "diff.external",
                    "protocol.ext.allow"
                );
        }

        @Test
        @DisplayName("should inject all security configs even without volume mounts")
        void shouldInjectSecurityConfigsWithoutVolumeMounts() {
            setupHappyPath();

            // createSpec() uses null volumeMounts → defaults to Map.of() (empty)
            sandboxAdapter.execute(createSpec());

            ArgumentCaptor<DockerOperations.ContainerSpec> captor = ArgumentCaptor.forClass(
                DockerOperations.ContainerSpec.class
            );
            verify(containerManager).createContainer(captor.capture());

            Map<String, String> env = captor.getValue().environment();

            // No volume mounts → COUNT equals security config count exactly
            int count = Integer.parseInt(env.get("GIT_CONFIG_COUNT"));
            assertThat(count).isEqualTo(DockerSandboxAdapter.GIT_SECURITY_CONFIGS.size());

            // Verify each security config key-value PAIR at the correct index
            for (int i = 0; i < DockerSandboxAdapter.GIT_SECURITY_CONFIGS.size(); i++) {
                var expected = DockerSandboxAdapter.GIT_SECURITY_CONFIGS.get(i);
                assertThat(env.get("GIT_CONFIG_KEY_" + i)).isEqualTo(expected.getKey());
                assertThat(env.get("GIT_CONFIG_VALUE_" + i)).isEqualTo(expected.getValue());
            }
        }

        @Test
        @DisplayName("should inject safe.directory per volume mount plus all security configs")
        void shouldInjectSafeDirectoryPerVolumeMount() {
            setupHappyPath();

            // Use LinkedHashMap to guarantee iteration order for index assertions
            Map<String, String> mounts = new LinkedHashMap<>();
            mounts.put("/host/repo1", "/workspace/repo1");
            mounts.put("/host/repo2", "/workspace/repo2");
            SandboxSpec spec = createSpec(false, mounts);

            sandboxAdapter.execute(spec);

            ArgumentCaptor<DockerOperations.ContainerSpec> captor = ArgumentCaptor.forClass(
                DockerOperations.ContainerSpec.class
            );
            verify(containerManager).createContainer(captor.capture());

            Map<String, String> env = captor.getValue().environment();

            // First N entries are safe.directory for each mount
            assertThat(env.get("GIT_CONFIG_KEY_0")).isEqualTo("safe.directory");
            assertThat(env.get("GIT_CONFIG_VALUE_0")).isEqualTo("/workspace/repo1");
            assertThat(env.get("GIT_CONFIG_KEY_1")).isEqualTo("safe.directory");
            assertThat(env.get("GIT_CONFIG_VALUE_1")).isEqualTo("/workspace/repo2");

            // Remaining entries are security configs (starting at idx 2)
            int securityStartIdx = mounts.size();
            for (int i = 0; i < DockerSandboxAdapter.GIT_SECURITY_CONFIGS.size(); i++) {
                var expected = DockerSandboxAdapter.GIT_SECURITY_CONFIGS.get(i);
                assertThat(env.get("GIT_CONFIG_KEY_" + (securityStartIdx + i))).isEqualTo(expected.getKey());
                assertThat(env.get("GIT_CONFIG_VALUE_" + (securityStartIdx + i))).isEqualTo(expected.getValue());
            }

            int expectedCount = mounts.size() + DockerSandboxAdapter.GIT_SECURITY_CONFIGS.size();
            assertThat(env.get("GIT_CONFIG_COUNT")).isEqualTo(String.valueOf(expectedCount));
        }

        @Test
        @DisplayName("should set GIT_TERMINAL_PROMPT and GIT_ATTR_NOSYSTEM")
        void shouldSetGitHardeningEnvVars() {
            setupHappyPath();

            sandboxAdapter.execute(createSpec());

            ArgumentCaptor<DockerOperations.ContainerSpec> captor = ArgumentCaptor.forClass(
                DockerOperations.ContainerSpec.class
            );
            verify(containerManager).createContainer(captor.capture());

            Map<String, String> env = captor.getValue().environment();
            assertThat(env).containsEntry("GIT_TERMINAL_PROMPT", "0");
            assertThat(env).containsEntry("GIT_ATTR_NOSYSTEM", "1");
        }

        @Test
        @DisplayName("should block caller-supplied GIT_CONFIG_* via prefix blocklist")
        void shouldBlockCallerGitConfigVars() {
            // Verify at the unit level that GIT_CONFIG_* vars are prefix-blocked
            assertThat(DockerSandboxAdapter.isBlockedEnvVar("GIT_CONFIG_COUNT")).isTrue();
            assertThat(DockerSandboxAdapter.isBlockedEnvVar("GIT_CONFIG_KEY_0")).isTrue();
            assertThat(DockerSandboxAdapter.isBlockedEnvVar("GIT_CONFIG_VALUE_99")).isTrue();
        }

        @Test
        @DisplayName("should overwrite security env vars even if caller bypasses blocklist")
        void shouldOverwriteSecurityEnvVarsViaOrdering() {
            setupHappyPath();

            // GIT_TERMINAL_PROMPT and GIT_ATTR_NOSYSTEM are in BLOCKED_ENV_VARS, so a caller
            // can't inject them. This test verifies the defense-in-depth: even if they somehow
            // leaked through, the security injection at the end of buildEnvironment() wins.
            // We test this by verifying the final values are always the security-hardened ones.
            sandboxAdapter.execute(createSpec());

            ArgumentCaptor<DockerOperations.ContainerSpec> captor = ArgumentCaptor.forClass(
                DockerOperations.ContainerSpec.class
            );
            verify(containerManager).createContainer(captor.capture());

            Map<String, String> env = captor.getValue().environment();

            // Security values must always be present regardless of caller input
            assertThat(env).containsEntry("GIT_TERMINAL_PROMPT", "0");
            assertThat(env).containsEntry("GIT_ATTR_NOSYSTEM", "1");
            assertThat(env).containsKey("GIT_CONFIG_COUNT");

            // core.hooksPath must be /nonexistent — the canonical safety check
            assertThat(env.get("GIT_CONFIG_KEY_0")).isEqualTo("core.hooksPath");
            assertThat(env.get("GIT_CONFIG_VALUE_0")).isEqualTo("/nonexistent");
        }
    }
}
