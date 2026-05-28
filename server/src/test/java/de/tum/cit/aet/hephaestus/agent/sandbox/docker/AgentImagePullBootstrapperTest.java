package de.tum.cit.aet.hephaestus.agent.sandbox.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.runtime.AgentImageProperties;
import de.tum.cit.aet.hephaestus.agent.sandbox.ImagePullPolicy;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;

class AgentImagePullBootstrapperTest extends BaseUnitTest {

    private static final String IMAGE = "ghcr.io/ls1intum/hephaestus/agent-pi:latest";

    @Mock
    private DockerImageOperations imageOps;

    private AgentImagePullBootstrapper bootstrapperWith(ImagePullPolicy policy, SimpleMeterRegistry registry) {
        return new AgentImagePullBootstrapper(imageOps, new AgentImageProperties(IMAGE, policy), registry);
    }

    @Test
    void shouldSkipPullWhenDaemonUnreachableAndPolicyIsAlways() {
        var registry = new SimpleMeterRegistry();
        when(imageOps.ping()).thenReturn(false);

        bootstrapperWith(ImagePullPolicy.ALWAYS, registry).pullOnStartup();

        verify(imageOps).ping();
        verifyNoMoreInteractions(imageOps);
        assertThat(registry.counter("agent.image.pull.skipped", "reason", "docker_unreachable").count()).isEqualTo(1d);
    }

    @ParameterizedTest(name = "ALWAYS pull returns {0} → outcome={1}, failure-counter={2}")
    @CsvSource({ "true, success, 0", "false, failure, 1" })
    void shouldRecordOutcomeMetricWhenAlwaysPolicyRunsPull(
        boolean pullSucceeds,
        String outcomeTag,
        int expectedFailureCount
    ) {
        var registry = new SimpleMeterRegistry();
        when(imageOps.ping()).thenReturn(true);
        when(imageOps.pullImage(IMAGE)).thenReturn(pullSucceeds);

        bootstrapperWith(ImagePullPolicy.ALWAYS, registry).pullOnStartup();

        verify(imageOps, never()).imageIsPresent(IMAGE);
        verify(imageOps).pullImage(IMAGE);
        assertThat(registry.timer("agent.image.pull.duration", "outcome", outcomeTag).count()).isEqualTo(1L);
        assertThat(registry.counter("agent.image.pull.failure").count()).isEqualTo(expectedFailureCount);
    }

    @ParameterizedTest(name = "IF_NOT_PRESENT, imageIsPresent={0} → pullImage invoked={1}")
    @CsvSource({ "true, 0", "false, 1" })
    void shouldHonourCacheWhenPolicyIsIfNotPresent(boolean alreadyPresent, int expectedPulls) {
        when(imageOps.imageIsPresent(IMAGE)).thenReturn(alreadyPresent);
        if (!alreadyPresent) {
            when(imageOps.ping()).thenReturn(true);
            when(imageOps.pullImage(IMAGE)).thenReturn(true);
        }

        bootstrapperWith(ImagePullPolicy.IF_NOT_PRESENT, new SimpleMeterRegistry()).pullOnStartup();

        verify(imageOps).imageIsPresent(IMAGE);
        verify(imageOps, alreadyPresent ? never() : org.mockito.Mockito.times(expectedPulls)).pullImage(IMAGE);
    }

    @Test
    void shouldOnlyProbePresenceWhenPolicyIsNever() {
        bootstrapperWith(ImagePullPolicy.NEVER, new SimpleMeterRegistry()).pullOnStartup();

        verify(imageOps).imageIsPresent(any());
        verifyNoMoreInteractions(imageOps);
    }
}
