package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.agent.runtime.AgentImageProperties;
import de.tum.in.www1.hephaestus.agent.sandbox.ImagePullPolicy;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;

@DisplayName("AgentImagePullBootstrapper")
class AgentImagePullBootstrapperTest extends BaseUnitTest {

    private static final String IMAGE = "ghcr.io/ls1intum/hephaestus/agent-pi:latest";

    @Mock
    private DockerImageOperations imageOps;

    private AgentImagePullBootstrapper bootstrapperWith(ImagePullPolicy policy, SimpleMeterRegistry registry) {
        return new AgentImagePullBootstrapper(imageOps, new AgentImageProperties(IMAGE, policy, false), registry);
    }

    @Test
    @DisplayName("ALWAYS: short-circuits + emits skipped counter when Docker daemon is unreachable")
    void alwaysSkipsWhenDaemonUnreachable() {
        var registry = new SimpleMeterRegistry();
        when(imageOps.ping()).thenReturn(false);

        bootstrapperWith(ImagePullPolicy.ALWAYS, registry).pullOnStartup();

        verify(imageOps).ping();
        verifyNoMoreInteractions(imageOps);
        assertThat(registry.counter("agent.image.pull.skipped", "reason", "docker_unreachable").count()).isEqualTo(1d);
    }

    @ParameterizedTest(name = "ALWAYS: pullImage returns {0} → outcome={1}, failure-counter={2}")
    @CsvSource({ "true, success, 0", "false, failure, 1" })
    void alwaysRecordsOutcome(boolean pullSucceeds, String outcomeTag, int expectedFailureCount) {
        var registry = new SimpleMeterRegistry();
        when(imageOps.ping()).thenReturn(true);
        when(imageOps.pullImage(IMAGE)).thenReturn(pullSucceeds);

        bootstrapperWith(ImagePullPolicy.ALWAYS, registry).pullOnStartup();

        // imageIsPresent must NOT be consulted for ALWAYS — that's the whole point of the policy.
        verify(imageOps, never()).imageIsPresent(IMAGE);
        verify(imageOps).pullImage(IMAGE);
        assertThat(registry.timer("agent.image.pull.duration", "outcome", outcomeTag).count()).isEqualTo(1L);
        assertThat(registry.counter("agent.image.pull.failure").count()).isEqualTo(expectedFailureCount);
    }

    @Test
    @DisplayName("IF_NOT_PRESENT: skips pull when image already in daemon cache")
    void ifNotPresentSkipsWhenImagePresent() {
        when(imageOps.imageIsPresent(IMAGE)).thenReturn(true);

        bootstrapperWith(ImagePullPolicy.IF_NOT_PRESENT, new SimpleMeterRegistry()).pullOnStartup();

        verify(imageOps).imageIsPresent(IMAGE);
        verify(imageOps, never()).ping();
        verify(imageOps, never()).pullImage(IMAGE);
    }

    @Test
    @DisplayName("IF_NOT_PRESENT: pulls when image absent from daemon cache")
    void ifNotPresentPullsWhenImageAbsent() {
        when(imageOps.imageIsPresent(IMAGE)).thenReturn(false);
        when(imageOps.ping()).thenReturn(true);
        when(imageOps.pullImage(IMAGE)).thenReturn(true);

        bootstrapperWith(ImagePullPolicy.IF_NOT_PRESENT, new SimpleMeterRegistry()).pullOnStartup();

        verify(imageOps).pullImage(IMAGE);
    }

    @Test
    @DisplayName("NEVER: only probes image presence to emit an operator warning; never pulls")
    void neverOnlyProbesPresence() {
        bootstrapperWith(ImagePullPolicy.NEVER, new SimpleMeterRegistry()).pullOnStartup();

        // The contract is "no pulls", not "no probes": the probe lets us warn if the image
        // is missing locally so the operator doesn't chase an inscrutable container error.
        verify(imageOps).imageIsPresent(any());
        verifyNoMoreInteractions(imageOps);
    }
}
