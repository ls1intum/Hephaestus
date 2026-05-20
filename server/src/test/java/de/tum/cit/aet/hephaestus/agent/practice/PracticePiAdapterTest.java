package de.tum.cit.aet.hephaestus.agent.practice;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.runtime.AgentImageProperties;
import de.tum.cit.aet.hephaestus.agent.runtime.PiResultParser;
import de.tum.cit.aet.hephaestus.agent.runtime.PiRuntimeFactory;
import de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi;
import de.tum.cit.aet.hephaestus.agent.sandbox.ImagePullPolicy;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

@DisplayName("PracticePiAdapter")
class PracticePiAdapterTest extends BaseUnitTest {

    private static final String IMAGE = "ghcr.io/ls1intum/hephaestus/agent-pi:latest";
    private PracticePiAdapter adapter;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        adapter = new PracticePiAdapter(
            new PiRuntimeFactory(mapper),
            new PiResultParser(mapper, metrics),
            new AgentImageProperties(IMAGE, ImagePullPolicy.IF_NOT_PRESENT)
        );
    }

    private PracticeAgentRequest proxyRequest() {
        return new PracticeAgentRequest(
            LlmProvider.AZURE_OPENAI,
            CredentialMode.PROXY,
            null,
            null,
            null,
            "job-token-123",
            false,
            600
        );
    }

    @Test
    @DisplayName("builds a sandbox spec with the configured image")
    void buildsSpecWithImage() {
        var spec = adapter.buildSandboxSpec(proxyRequest());
        assertThat(spec.image()).isEqualTo(IMAGE);
    }

    @Test
    @DisplayName("precompute step references the shared context/target/ paths")
    void precomputeReferencesContextTarget() {
        String step = PracticePiAdapter.buildPrecomputeStep();
        assertThat(step)
            .contains("/workspace/" + WorkspaceAbi.CONTEXT_TARGET_PREFIX + "diff.patch")
            .contains("/workspace/" + WorkspaceAbi.CONTEXT_TARGET_PREFIX + "metadata.json")
            .doesNotContain("/workspace/.context/");
    }

    @Test
    @DisplayName("PROXY-mode network policy propagates the job token")
    void networkPolicyContract() {
        var spec = adapter.buildSandboxSpec(proxyRequest());
        assertThat(spec.networkPolicy().llmProxyToken()).isEqualTo("job-token-123");
    }

    @Test
    @DisplayName("output path is the shared WorkspaceAbi.OUTPUT_PATH")
    void outputPath() {
        assertThat(adapter.buildSandboxSpec(proxyRequest()).outputPath()).isEqualTo(WorkspaceAbi.OUTPUT_PATH);
    }

    @Test
    @DisplayName("does NOT inject a legacy .prompt file (handler writes task.json instead)")
    void doesNotInjectPromptFile() {
        var spec = adapter.buildSandboxSpec(proxyRequest());
        assertThat(spec.inputFiles()).doesNotContainKey(".prompt");
    }
}
