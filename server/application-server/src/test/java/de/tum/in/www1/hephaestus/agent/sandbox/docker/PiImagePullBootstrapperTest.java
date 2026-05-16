package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.agent.runtime.PiAgentProperties;
import de.tum.in.www1.hephaestus.agent.sandbox.ImagePullPolicy;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@DisplayName("PiImagePullBootstrapper")
class PiImagePullBootstrapperTest extends BaseUnitTest {

    private static final String IMAGE = "ghcr.io/ls1intum/hephaestus/agent-pi:latest";

    @Mock
    private DockerImageOperations imageOps;

    private PiImagePullBootstrapper bootstrapperWith(ImagePullPolicy policy) {
        return new PiImagePullBootstrapper(
            imageOps,
            new PiAgentProperties(IMAGE, "pi-runner.mjs", policy),
            new SimpleMeterRegistry()
        );
    }

    // ─── ALWAYS policy ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("ALWAYS: short-circuits + emits skipped counter when Docker daemon is unreachable")
    void always_skipsWhenDaemonUnreachable() {
        when(imageOps.ping()).thenReturn(false);
        var registry = new SimpleMeterRegistry();
        var bootstrapper = new PiImagePullBootstrapper(
            imageOps,
            new PiAgentProperties(IMAGE, "pi-runner.mjs", ImagePullPolicy.ALWAYS),
            registry
        );

        bootstrapper.pullOnStartup();

        verify(imageOps).ping();
        verifyNoMoreInteractions(imageOps);
        assertThat(registry.counter("agent.pi.image.pull.skipped", "reason", "docker_unreachable").count()).isEqualTo(
            1d
        );
    }

    @Test
    @DisplayName("ALWAYS: records a success duration timer when the pull completes")
    void always_recordsSuccessDuration() {
        var registry = new SimpleMeterRegistry();
        var bootstrapper = new PiImagePullBootstrapper(
            imageOps,
            new PiAgentProperties(IMAGE, "pi-runner.mjs", ImagePullPolicy.ALWAYS),
            registry
        );
        when(imageOps.ping()).thenReturn(true);
        when(imageOps.pullImage(IMAGE)).thenReturn(true);

        bootstrapper.pullOnStartup();

        assertThat(registry.timer("agent.pi.image.pull.duration", "outcome", "success").count()).isEqualTo(1L);
        assertThat(registry.counter("agent.pi.image.pull.failure").count()).isEqualTo(0d);
    }

    @Test
    @DisplayName("ALWAYS: records a failure duration timer + failure counter when the pull fails")
    void always_recordsFailure() {
        var registry = new SimpleMeterRegistry();
        var bootstrapper = new PiImagePullBootstrapper(
            imageOps,
            new PiAgentProperties(IMAGE, "pi-runner.mjs", ImagePullPolicy.ALWAYS),
            registry
        );
        when(imageOps.ping()).thenReturn(true);
        when(imageOps.pullImage(IMAGE)).thenReturn(false);

        bootstrapper.pullOnStartup();

        assertThat(registry.timer("agent.pi.image.pull.duration", "outcome", "failure").count()).isEqualTo(1L);
        assertThat(registry.counter("agent.pi.image.pull.failure").count()).isEqualTo(1d);
    }

    @Test
    @DisplayName("ALWAYS: delegates to the configured image from PiAgentProperties")
    void always_usesConfiguredImage() {
        var bootstrapper = bootstrapperWith(ImagePullPolicy.ALWAYS);
        when(imageOps.ping()).thenReturn(true);
        when(imageOps.pullImage(IMAGE)).thenReturn(true);

        bootstrapper.pullOnStartup();

        verify(imageOps).pullImage(eq(IMAGE));
    }

    @Test
    @DisplayName("ALWAYS: pulls even when image already present locally")
    void always_pullsEvenIfImagePresent() {
        var bootstrapper = bootstrapperWith(ImagePullPolicy.ALWAYS);
        when(imageOps.ping()).thenReturn(true);
        when(imageOps.pullImage(IMAGE)).thenReturn(true);

        bootstrapper.pullOnStartup();

        // imageIsPresent must NOT be consulted for ALWAYS policy
        verify(imageOps, never()).imageIsPresent(IMAGE);
        verify(imageOps).pullImage(IMAGE);
    }

    // ─── IF_NOT_PRESENT policy ──────────────────────────────────────────────────

    @Test
    @DisplayName("IF_NOT_PRESENT: skips pull when image already in daemon cache")
    void ifNotPresent_skipsWhenImagePresent() {
        var bootstrapper = bootstrapperWith(ImagePullPolicy.IF_NOT_PRESENT);
        when(imageOps.imageIsPresent(IMAGE)).thenReturn(true);

        bootstrapper.pullOnStartup();

        verify(imageOps).imageIsPresent(IMAGE);
        verify(imageOps, never()).ping();
        verify(imageOps, never()).pullImage(IMAGE);
    }

    @Test
    @DisplayName("IF_NOT_PRESENT: pulls when image absent from daemon cache")
    void ifNotPresent_pullsWhenImageAbsent() {
        var bootstrapper = bootstrapperWith(ImagePullPolicy.IF_NOT_PRESENT);
        when(imageOps.imageIsPresent(IMAGE)).thenReturn(false);
        when(imageOps.ping()).thenReturn(true);
        when(imageOps.pullImage(IMAGE)).thenReturn(true);

        bootstrapper.pullOnStartup();

        verify(imageOps).pullImage(IMAGE);
    }

    // ─── NEVER policy ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("NEVER: only probes image presence to emit a warning; never pulls")
    void never_onlyProbesPresence() {
        var bootstrapper = bootstrapperWith(ImagePullPolicy.NEVER);

        bootstrapper.pullOnStartup();

        // NEVER deliberately reads imageIsPresent so it can emit an operator warning when
        // the image isn't in the local daemon (container creation would otherwise fail with
        // an inscrutable error at attach time). The contract is "no pulls", not "no probes".
        verify(imageOps).imageIsPresent(any());
        verifyNoMoreInteractions(imageOps);
    }
}
