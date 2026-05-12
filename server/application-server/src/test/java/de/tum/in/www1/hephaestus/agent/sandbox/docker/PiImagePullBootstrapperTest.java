package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.agent.runtime.PiAgentProperties;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@DisplayName("PiImagePullBootstrapper")
class PiImagePullBootstrapperTest extends BaseUnitTest {

    private static final String IMAGE = "ghcr.io/ls1intum/hephaestus/agent-pi:latest";

    @Mock
    private DockerImageOperations imageOps;

    private SimpleMeterRegistry meterRegistry;
    private PiImagePullBootstrapper bootstrapper;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        bootstrapper = new PiImagePullBootstrapper(
            imageOps,
            new PiAgentProperties(IMAGE, "pi-runner.mjs", true),
            meterRegistry
        );
    }

    @Test
    @DisplayName("short-circuits + emits skipped counter when Docker daemon is unreachable")
    void skipsWhenDaemonUnreachable() {
        when(imageOps.ping()).thenReturn(false);

        bootstrapper.pullOnStartup();

        verify(imageOps).ping();
        verifyNoMoreInteractions(imageOps);
        assertThat(
            meterRegistry.counter("agent.pi.image.pull.skipped", "reason", "docker_unreachable").count()
        ).isEqualTo(1d);
    }

    @Test
    @DisplayName("records a success duration timer when the pull completes")
    void recordsSuccessDuration() {
        when(imageOps.ping()).thenReturn(true);
        when(imageOps.pullImage(IMAGE)).thenReturn(true);

        bootstrapper.pullOnStartup();

        assertThat(meterRegistry.timer("agent.pi.image.pull.duration", "outcome", "success").count()).isEqualTo(1L);
        assertThat(meterRegistry.counter("agent.pi.image.pull.failure").count()).isEqualTo(0d);
    }

    @Test
    @DisplayName("records a failure duration timer + failure counter when the pull fails")
    void recordsFailure() {
        when(imageOps.ping()).thenReturn(true);
        when(imageOps.pullImage(IMAGE)).thenReturn(false);

        bootstrapper.pullOnStartup();

        assertThat(meterRegistry.timer("agent.pi.image.pull.duration", "outcome", "failure").count()).isEqualTo(1L);
        assertThat(meterRegistry.counter("agent.pi.image.pull.failure").count()).isEqualTo(1d);
    }

    @Test
    @DisplayName("delegates to the configured image from PiAgentProperties")
    void usesConfiguredImage() {
        when(imageOps.ping()).thenReturn(true);
        when(imageOps.pullImage(IMAGE)).thenReturn(true);

        bootstrapper.pullOnStartup();

        verify(imageOps).pullImage(eq(IMAGE));
    }
}
