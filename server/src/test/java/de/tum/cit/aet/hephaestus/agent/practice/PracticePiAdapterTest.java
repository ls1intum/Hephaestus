package de.tum.cit.aet.hephaestus.agent.practice;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.runtime.AgentImageProperties;
import de.tum.cit.aet.hephaestus.agent.runtime.PiResultParser;
import de.tum.cit.aet.hephaestus.agent.runtime.PiRuntimeFactory;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.agent.sandbox.ImagePullPolicy;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

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
    void buildsSpecWithImage() {
        var spec = adapter.buildSandboxSpec(proxyRequest());
        assertThat(spec.image()).isEqualTo(IMAGE);
    }

    @Test
    void precomputeReferencesContextTarget() {
        String step = PracticePiAdapter.buildPrecomputeStep();
        assertThat(step)
            .contains("/workspace/" + SandboxLayout.CONTEXT_PREFIX + "diff.patch")
            .contains("/workspace/" + SandboxLayout.CONTEXT_PREFIX + "metadata.json")
            // scripts receive the materialised context dir so they can read project_inventory.json etc.
            .contains("--context /workspace/" + SandboxLayout.CONTEXT_PREFIX)
            .doesNotContain("/workspace/.context/");
    }

    @Test
    void precomputeIsBestEffortNonFatal() {
        // The precompute fragment is interpolated verbatim into the agent's sh -c command. Its robustness
        // properties are load-bearing: a failed (or absent) precompute must NOT abort the whole agent run.
        String step = PracticePiAdapter.buildPrecomputeStep();
        assertThat(step)
            // Non-fatal contract: a failed runner falls into the '|| { … ; true; }' guard and continues.
            .contains("|| {")
            .contains("; true; }")
            // Zero-script tolerance: bun is reached via ';' (not '&&') after the sed strip, so a missing
            // '*.ts' / failed cp still lets the runner start.
            .contains("2>/dev/null ; bun run")
            // The runner gets the repo mount, the cleaned diff, and writes into the precompute-out dir.
            .contains("--repo " + SandboxLayout.REPO_MOUNT)
            .contains("/diff_clean.patch")
            .contains("--output /workspace/work/precompute-out")
            // The agent-facing [L<n>] line annotations are stripped to a raw diff for the static parser.
            .contains("sed 's/^\\[L[0-9]*\\] //'");
    }

    @Test
    void networkPolicyContract() {
        var spec = adapter.buildSandboxSpec(proxyRequest());
        assertThat(spec.networkPolicy().llmProxyToken()).isEqualTo("job-token-123");
    }

    @Test
    void outputPath() {
        assertThat(adapter.buildSandboxSpec(proxyRequest()).outputPath()).isEqualTo(SandboxLayout.OUTPUT_PATH);
    }

    @Test
    void doesNotInjectPromptFile() {
        var spec = adapter.buildSandboxSpec(proxyRequest());
        assertThat(spec.inputFiles()).doesNotContainKey(".prompt");
    }

    @Test
    void proxyWithBaseUrlBuildsWithoutThrowing() {
        // Regression: PROXY is the DEFAULT credentialMode and a baseUrl is independently settable, so a valid
        // persisted config legitimately reaches the adapter as PROXY + non-null baseUrl. This must build a
        // spec (PiPlanSpec normalises the shadowed baseUrl to null), not throw on every job.
        PracticeAgentRequest request = new PracticeAgentRequest(
            LlmProvider.OPENAI,
            CredentialMode.PROXY,
            null,
            "gpt-oss-120b",
            "https://gpu.example.com/v1",
            "job-token-123",
            false,
            600
        );

        var spec = adapter.buildSandboxSpec(request);

        assertThat(spec.image()).isEqualTo(IMAGE);
        assertThat(spec.networkPolicy().llmProxyToken()).isEqualTo("job-token-123");
    }
}
