package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.agent.sandbox.SandboxProperties;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

@DisplayName("DockerHealthIndicator")
class DockerHealthIndicatorTest extends BaseUnitTest {

  @Mock private SandboxContainerManager containerManager;

  private DockerHealthIndicator indicator;

  @BeforeEach
  void setUp() {
    SandboxProperties properties =
        new SandboxProperties(
            true, "unix:///var/run/docker.sock", false, null, 5, 10, 60, null, 8080, null, null);
    indicator = new DockerHealthIndicator(containerManager, properties);
  }

  @Test
  @DisplayName("should report UP when Docker daemon is reachable")
  void shouldReportUpWhenReachable() {
    when(containerManager.ping()).thenReturn(true);
    when(containerManager.listManagedContainers()).thenReturn(List.of());

    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsKey("dockerHost");
    assertThat(health.getDetails()).containsEntry("activeContainers", 0);
    assertThat(health.getDetails()).containsEntry("maxConcurrentContainers", 5);
  }

  @Test
  @DisplayName("should report DOWN when Docker daemon is unreachable")
  void shouldReportDownWhenUnreachable() {
    when(containerManager.ping()).thenReturn(false);

    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("error", "Docker daemon not reachable");
  }

  @Test
  @DisplayName("should report DOWN with error details on exception")
  void shouldReportDownOnException() {
    when(containerManager.ping()).thenThrow(new RuntimeException("Connection refused"));

    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("error", "Connection refused");
  }
}
