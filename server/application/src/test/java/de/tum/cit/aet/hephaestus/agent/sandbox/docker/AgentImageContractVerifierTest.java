package de.tum.cit.aet.hephaestus.agent.sandbox.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.agent.sandbox.docker.AgentImageContractVerifier.Outcome;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class AgentImageContractVerifierTest extends BaseUnitTest {

    private static final String IMAGE = "ghcr.io/ls1intum/hephaestus/agent-pi:0.73.2";

    @Mock
    private DockerImageOperations imageOps;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private Outcome verifyWithLabels(Optional<Map<String, String>> labels) {
        when(imageOps.imageLabels(IMAGE)).thenReturn(labels);
        return new AgentImageContractVerifier(imageOps, registry).verify(IMAGE);
    }

    private double counter(Outcome outcome) {
        return registry.counter(
                        "agent.image.contract", "outcome", outcome.name().toLowerCase(Locale.ROOT))
                .count();
    }

    @Test
    void shouldVerifyAnImageDeclaringTheContractThisServerStagesFor() {
        var outcome = verifyWithLabels(Optional.of(Map.of(
                SandboxLayout.RUNTIME_CONTRACT_LABEL, Integer.toString(SandboxLayout.RUNTIME_CONTRACT_VERSION))));

        assertThat(outcome).isEqualTo(Outcome.VERIFIED);
        assertThat(counter(Outcome.VERIFIED)).isEqualTo(1d);
    }

    @Test
    void shouldReportAnImageThatPredatesTheContract() {
        var outcome = verifyWithLabels(Optional.of(Map.of("hephaestus.component", "agent-pi")));

        assertThat(outcome).isEqualTo(Outcome.UNLABELLED);
        assertThat(counter(Outcome.UNLABELLED)).isEqualTo(1d);
    }

    @Test
    void shouldReportAnImageDeclaringADifferentContract() {
        var outcome = verifyWithLabels(Optional.of(Map.of(
                SandboxLayout.RUNTIME_CONTRACT_LABEL, Integer.toString(SandboxLayout.RUNTIME_CONTRACT_VERSION + 1))));

        assertThat(outcome).isEqualTo(Outcome.MISMATCH);
        assertThat(counter(Outcome.MISMATCH)).isEqualTo(1d);
    }

    @Test
    void shouldProveNothingWhenTheImageCannotBeInspected() {
        var outcome = verifyWithLabels(Optional.empty());

        assertThat(outcome).isEqualTo(Outcome.UNKNOWN);
        assertThat(counter(Outcome.UNKNOWN)).isEqualTo(1d);
    }
}
