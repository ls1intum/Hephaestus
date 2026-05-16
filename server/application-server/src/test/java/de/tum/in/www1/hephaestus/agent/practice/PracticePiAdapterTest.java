package de.tum.in.www1.hephaestus.agent.practice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.runtime.PiAgentProperties;
import de.tum.in.www1.hephaestus.agent.runtime.PiResultParser;
import de.tum.in.www1.hephaestus.agent.runtime.PiRuntimeFactory;
import de.tum.in.www1.hephaestus.agent.runtime.WorkspaceAbi;
import de.tum.in.www1.hephaestus.agent.sandbox.ImagePullPolicy;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
            new PiAgentProperties(IMAGE, "pi-runner.mjs", ImagePullPolicy.IF_NOT_PRESENT)
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
