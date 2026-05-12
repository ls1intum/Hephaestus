package de.tum.in.www1.hephaestus.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("PiRuntimeFactory")
class PiRuntimeFactoryTest extends BaseUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PiRuntimeFactory factory;

    @BeforeEach
    void setUp() {
        factory = new PiRuntimeFactory(objectMapper);
    }

    private PiPlanSpec proxySpec(LlmProvider provider, String modelName) {
        return new PiPlanSpec(
            provider,
            CredentialMode.PROXY,
            null,
            modelName,
            "job-token-123",
            false,
            600,
            "pi-runner.mjs",
            Map.of(),
            ""
        );
    }

    private PiPlanSpec apiKeySpec(LlmProvider provider) {
        return new PiPlanSpec(
            provider,
            CredentialMode.API_KEY,
            "sk-test",
            null,
            null,
            true,
            600,
            "pi-runner.mjs",
            Map.of(),
            ""
        );
    }

    @Nested
    @DisplayName("network policy")
    class NetworkPolicyContract {

        @Test
        @DisplayName("PROXY forwards allowInternet=false + jobToken")
        void proxyForwardsToken() {
            var policy = factory.build(proxySpec(LlmProvider.AZURE_OPENAI, null)).networkPolicy();
            assertThat(policy.internetAccess()).isFalse();
            assertThat(policy.llmProxyToken()).isEqualTo("job-token-123");
        }

        @Test
        @DisplayName("API_KEY allows internet, no proxy token")
        void apiKeyAllowsInternet() {
            var policy = factory.build(apiKeySpec(LlmProvider.OPENAI)).networkPolicy();
            assertThat(policy.internetAccess()).isTrue();
            assertThat(policy.llmProxyToken()).isNull();
        }
    }

    @Nested
    @DisplayName("input files")
    class InputFiles {

        @Test
        @DisplayName("loads pi-orchestrator.md and the runner from classpath")
        void loadsClasspathResources() {
            var inputs = factory.build(proxySpec(LlmProvider.AZURE_OPENAI, null)).inputFiles();
            assertThat(inputs.get(".pi/AGENTS.md")).isNotNull();
            assertThat(new String(inputs.get(".pi/AGENTS.md"), StandardCharsets.UTF_8)).contains("findings");
            assertThat(inputs.get(".run-pi.mjs")).isNotEmpty();
        }

        @Test
        @DisplayName("extra inputs merge into the input-files map")
        void extraInputsMerge() {
            PiPlanSpec spec = new PiPlanSpec(
                LlmProvider.OPENAI,
                CredentialMode.API_KEY,
                "sk-test",
                null,
                null,
                true,
                600,
                "pi-runner.mjs",
                Map.of("task.json", "{\"schemaVersion\":1}".getBytes(StandardCharsets.UTF_8)),
                ""
            );
            assertThat(factory.build(spec).inputFiles()).containsKey("task.json");
        }
    }

    @Nested
    @DisplayName("settings JSON")
    class SettingsJson {

        @ParameterizedTest(name = "{0} → defaultProvider {1}")
        @CsvSource({ "AZURE_OPENAI, azure-openai-responses", "OPENAI, openai", "ANTHROPIC, anthropic" })
        void mapsProvider(LlmProvider provider, String expected) throws Exception {
            byte[] json = factory.buildPracticeSettingsJson(provider, "some-model");
            JsonNode root = objectMapper.readTree(new String(json, StandardCharsets.UTF_8));
            assertThat(root.path("defaultProvider").asText()).isEqualTo(expected);
            assertThat(root.path("defaultModel").asText()).isEqualTo("some-model");
            assertThat(root.path("transport").asText()).isEqualTo("sse");
        }

        @Test
        @DisplayName("omits defaultModel when null/blank and includes compaction config")
        void omitsModelAndIncludesCompaction() throws Exception {
            byte[] json = factory.buildPracticeSettingsJson(LlmProvider.OPENAI, null);
            JsonNode root = objectMapper.readTree(new String(json, StandardCharsets.UTF_8));
            assertThat(root.has("defaultModel")).isFalse();
            assertThat(root.path("compaction").path("enabled").asBoolean()).isTrue();
            assertThat(root.path("compaction").path("reserveTokens").asInt()).isEqualTo(16384);
        }
    }

    @Nested
    @DisplayName("environment")
    class Environment {

        @Test
        @DisplayName("AGENT_BUDGET_MS reflects timeout minus buffer")
        void budgetSet() {
            assertThat(factory.build(proxySpec(LlmProvider.AZURE_OPENAI, null)).environment()).containsEntry(
                "AGENT_BUDGET_MS",
                "540000"
            ); // (600 - 60) * 1000
        }

        @Test
        @DisplayName("HOME / TMPDIR redirected to writable mount")
        void writableMounts() {
            var env = factory.build(proxySpec(LlmProvider.AZURE_OPENAI, null)).environment();
            assertThat(env)
                .containsEntry("HOME", "/home/agent")
                .containsEntry("TMPDIR", "/home/agent/.local/tmp")
                .containsEntry("PI_CODING_AGENT_DIR", "/home/agent/.pi");
        }

        @Test
        @DisplayName("Azure deployment map is set with explicit model")
        void azureDeploymentMap() {
            var env = factory.build(proxySpec(LlmProvider.AZURE_OPENAI, "gpt-5.4-mini")).environment();
            assertThat(env).containsEntry(
                "AZURE_OPENAI_DEPLOYMENT_NAME_MAP",
                "gpt-5.4-mini=gpt-5.4-mini,gpt-5.2=gpt-5.4-mini"
            );
        }

        @ParameterizedTest(name = "Azure deployment map is NOT set for {0}")
        @EnumSource(value = LlmProvider.class, names = { "OPENAI", "ANTHROPIC" })
        void azureDeploymentNotSetForOthers(LlmProvider provider) {
            assertThat(factory.build(proxySpec(provider, null)).environment()).doesNotContainKey(
                "AZURE_OPENAI_DEPLOYMENT_NAME_MAP"
            );
        }
    }

    @Nested
    @DisplayName("command")
    class CommandAssembly {

        @Test
        @DisplayName("[sh, -c, <assembled>] with node runner at the end and precompute prepended")
        void shCommandWithPrecomputeOrder() {
            PiPlanSpec spec = new PiPlanSpec(
                LlmProvider.OPENAI,
                CredentialMode.API_KEY,
                "sk-test",
                null,
                null,
                true,
                600,
                "pi-runner.mjs",
                Map.of(),
                "echo precompute && "
            );
            var plan = factory.build(spec);
            assertThat(plan.command()).hasSize(3);
            assertThat(plan.command().get(0)).isEqualTo("sh");
            String body = plan.command().get(2);
            assertThat(body.indexOf("echo precompute")).isLessThan(body.indexOf("node /workspace/.run-pi.mjs"));
        }
    }

    @Nested
    @DisplayName("spec validation")
    class SpecValidation {

        @Test
        @DisplayName("PROXY mode requires jobToken")
        void proxyRequiresToken() {
            assertThatThrownBy(() ->
                new PiPlanSpec(
                    LlmProvider.OPENAI,
                    CredentialMode.PROXY,
                    null,
                    null,
                    null,
                    false,
                    600,
                    "pi-runner.mjs",
                    Map.of(),
                    ""
                )
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jobToken");
        }

        @Test
        @DisplayName("API_KEY mode requires credential")
        void apiKeyRequiresCredential() {
            assertThatThrownBy(() ->
                new PiPlanSpec(
                    LlmProvider.OPENAI,
                    CredentialMode.API_KEY,
                    null,
                    null,
                    null,
                    false,
                    600,
                    "pi-runner.mjs",
                    Map.of(),
                    ""
                )
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credential");
        }

        @Test
        @DisplayName("timeoutSeconds must exceed the buffer")
        void timeoutMustExceedBuffer() {
            assertThatThrownBy(() ->
                new PiPlanSpec(
                    LlmProvider.OPENAI,
                    CredentialMode.API_KEY,
                    "sk",
                    null,
                    null,
                    true,
                    PiRuntimeFactory.TIMEOUT_BUFFER_SECONDS,
                    "pi-runner.mjs",
                    Map.of(),
                    ""
                )
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TIMEOUT_BUFFER_SECONDS");
        }

        @Test
        @DisplayName("runnerScript must not be blank")
        void runnerScriptNotBlank() {
            assertThatThrownBy(() ->
                new PiPlanSpec(
                    LlmProvider.OPENAI,
                    CredentialMode.API_KEY,
                    "sk",
                    null,
                    null,
                    true,
                    600,
                    "  ",
                    Map.of(),
                    ""
                )
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runnerScript");
        }
    }
}
