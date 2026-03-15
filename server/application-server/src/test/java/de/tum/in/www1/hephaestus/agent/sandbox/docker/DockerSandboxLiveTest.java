package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import de.tum.in.www1.hephaestus.agent.sandbox.SandboxProperties;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.NetworkPolicy;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.ResourceLimits;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxResult;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxSpec;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SecurityProfile;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

/**
 * Integration tests for the Docker sandbox manager using a real Docker daemon.
 *
 * <p>These tests require Docker to be available on the machine. They are excluded from the default
 * test suite (tagged {@code "live"}) and run with {@code -Dgroups=live}.
 *
 * <p>Each test creates real containers that are cleaned up in {@code @AfterEach}.
 */
@Tag("live")
@DisplayName("Docker Sandbox Live")
class DockerSandboxLiveTest {

  private DockerSandboxAdapter sandboxAdapter;
  private SandboxContainerManager containerManager;
  private SandboxNetworkManager networkManager;
  private SandboxWorkspaceManager workspaceManager;
  private DockerClientOperations dockerOps;
  private ExecutorService dockerWaitExecutor;

  @BeforeAll
  static void checkDocker() {
    assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker not available — skipping integration tests");
  }

  @BeforeEach
  void setUp() {
    SandboxProperties properties =
        new SandboxProperties(
            true, "unix:///var/run/docker.sock", false, null, 5, 10, 60, null, 8080, null, null);

    var dockerClient =
        DockerClientImpl.getInstance(
            DefaultDockerClientConfig.createDefaultConfigBuilder().build(),
            new ApacheDockerHttpClient.Builder()
                .dockerHost(URI.create("unix:///var/run/docker.sock"))
                .build());

    dockerOps = new DockerClientOperations(dockerClient);
    dockerWaitExecutor = Executors.newCachedThreadPool();
    containerManager = new SandboxContainerManager(dockerOps, properties, dockerWaitExecutor);
    networkManager = new SandboxNetworkManager(dockerOps, properties);
    workspaceManager = new SandboxWorkspaceManager(dockerOps);
    ContainerSecurityPolicy securityPolicy = new ContainerSecurityPolicy(properties, null);

    sandboxAdapter =
        new DockerSandboxAdapter(
            networkManager,
            workspaceManager,
            containerManager,
            securityPolicy,
            properties,
            new SimpleMeterRegistry());
  }

  @AfterEach
  void cleanupOrphanedResources() {
    if (dockerWaitExecutor != null) {
      dockerWaitExecutor.shutdownNow();
    }
    // Safety net: remove any managed containers left over from failed tests
    try {
      containerManager
          .listManagedContainers()
          .forEach(
              c -> {
                try {
                  containerManager.forceRemove(c.id());
                } catch (Exception ignored) {
                }
              });
    } catch (Exception ignored) {
    }

    // Cleanup orphaned networks
    try {
      networkManager
          .listOrphanedNetworks()
          .forEach(
              n -> {
                try {
                  networkManager.removeNetwork(n.id());
                } catch (Exception ignored) {
                }
              });
    } catch (Exception ignored) {
    }
  }

  // Use a relaxed security profile for integration tests (no seccomp, no read-only rootfs)
  // because the test environment may not have the seccomp profile on the classpath
  // and alpine needs write access for some operations
  private SecurityProfile testSecurityProfile() {
    return new SecurityProfile(
        null,
        "", // no seccomp in tests
        false, // need writable rootfs for tests
        true,
        false, // skip cgroupns in tests
        "private",
        List.of("ALL"),
        Map.of());
  }

  @Nested
  @DisplayName("End-to-end execution")
  class EndToEnd {

    @Test
    @DisplayName("should run alpine container and collect output")
    void shouldRunAndCollectOutput() {
      UUID jobId = UUID.randomUUID();

      // Create a script that writes output
      String script =
          "#!/bin/sh\nmkdir -p /workspace/.output\necho '{\"status\":\"ok\"}' > /workspace/.output/result.json\necho 'done'";

      SandboxSpec spec =
          new SandboxSpec(
              jobId,
              "alpine:latest",
              List.of("sh", "-c", script),
              Map.of("TEST_VAR", "hello"),
              new NetworkPolicy(true, null, null), // Use bridge for simplicity
              new ResourceLimits(512 * 1024 * 1024, 1.0, 128, Duration.ofMinutes(1)),
              testSecurityProfile(),
              Map.of(),
              "/workspace/.output");

      SandboxResult result = sandboxAdapter.execute(spec);

      assertThat(result.exitCode()).isZero();
      assertThat(result.timedOut()).isFalse();
      assertThat(result.duration()).isPositive();
      assertThat(result.outputFiles()).containsKey("result.json");
      assertThat(new String(result.outputFiles().get("result.json"))).contains("ok");
    }

    @Test
    @DisplayName("should handle non-zero exit code")
    void shouldHandleNonZeroExit() {
      UUID jobId = UUID.randomUUID();

      SandboxSpec spec =
          new SandboxSpec(
              jobId,
              "alpine:latest",
              List.of("sh", "-c", "exit 42"),
              Map.of(),
              new NetworkPolicy(true, null, null),
              new ResourceLimits(256 * 1024 * 1024, 0.5, 64, Duration.ofMinutes(1)),
              testSecurityProfile(),
              Map.of(),
              "/workspace/.output");

      SandboxResult result = sandboxAdapter.execute(spec);

      assertThat(result.exitCode()).isEqualTo(42);
      assertThat(result.timedOut()).isFalse();
    }
  }

  @Nested
  @DisplayName("Timeout enforcement")
  class TimeoutEnforcement {

    @Test
    @DisplayName("should kill container after timeout")
    void shouldKillAfterTimeout() {
      UUID jobId = UUID.randomUUID();

      SandboxSpec spec =
          new SandboxSpec(
              jobId,
              "alpine:latest",
              List.of("sh", "-c", "sleep 300"),
              Map.of(),
              new NetworkPolicy(true, null, null),
              new ResourceLimits(256 * 1024 * 1024, 0.5, 64, Duration.ofSeconds(3)),
              testSecurityProfile(),
              Map.of(),
              "/workspace/.output");

      SandboxResult result = sandboxAdapter.execute(spec);

      assertThat(result.timedOut()).isTrue();
      // Exit code should be 137 (SIGKILL) or 143 (SIGTERM)
      assertThat(result.exitCode()).isIn(137, 143);
    }
  }

  @Nested
  @DisplayName("File injection")
  class FileInjection {

    @Test
    @DisplayName("should inject files and make them readable")
    void shouldInjectFiles() {
      UUID jobId = UUID.randomUUID();

      SandboxSpec spec =
          new SandboxSpec(
              jobId,
              "alpine:latest",
              List.of(
                  "sh",
                  "-c",
                  "mkdir -p /workspace/.output && cat /workspace/.prompt > /workspace/.output/echo.txt"),
              Map.of(),
              new NetworkPolicy(true, null, null),
              new ResourceLimits(256 * 1024 * 1024, 0.5, 64, Duration.ofMinutes(1)),
              testSecurityProfile(),
              Map.of(".prompt", "injected content".getBytes()),
              "/workspace/.output");

      SandboxResult result = sandboxAdapter.execute(spec);

      assertThat(result.exitCode()).isZero();
      assertThat(result.outputFiles()).containsKey("echo.txt");
      assertThat(new String(result.outputFiles().get("echo.txt"))).isEqualTo("injected content");
    }
  }

  @Nested
  @DisplayName("Cleanup")
  class Cleanup {

    @Test
    @DisplayName("should clean up all resources after execution")
    void shouldCleanupAfterExecution() {
      UUID jobId = UUID.randomUUID();

      SandboxSpec spec =
          new SandboxSpec(
              jobId,
              "alpine:latest",
              List.of("echo", "cleanup-test"),
              Map.of(),
              new NetworkPolicy(true, null, null),
              new ResourceLimits(256 * 1024 * 1024, 0.5, 64, Duration.ofMinutes(1)),
              testSecurityProfile(),
              Map.of(),
              "/workspace/.output");

      sandboxAdapter.execute(spec);

      // Verify no managed containers remain
      assertThat(
              containerManager.listManagedContainers().stream()
                  .filter(c -> jobId.toString().equals(c.labels().get("hephaestus.job-id")))
                  .toList())
          .isEmpty();

      // Verify no orphaned networks remain for this job
      assertThat(
              networkManager.listOrphanedNetworks().stream()
                  .filter(n -> n.name().contains(jobId.toString()))
                  .toList())
          .isEmpty();
    }
  }
}
