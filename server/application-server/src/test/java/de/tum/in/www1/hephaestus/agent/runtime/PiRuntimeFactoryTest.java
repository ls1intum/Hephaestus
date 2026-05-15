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
            null,
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
            byte[] json = factory.buildPiSettingsJson(provider, "some-model");
            JsonNode root = objectMapper.readTree(new String(json, StandardCharsets.UTF_8));
            assertThat(root.path("defaultProvider").asText()).isEqualTo(expected);
            assertThat(root.path("defaultModel").asText()).isEqualTo("some-model");
            assertThat(root.path("transport").asText()).isEqualTo("sse");
        }

        @Test
        @DisplayName("omits defaultModel when null/blank and includes compaction config")
        void omitsModelAndIncludesCompaction() throws Exception {
            byte[] json = factory.buildPiSettingsJson(LlmProvider.OPENAI, null);
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
        @DisplayName("AGENT_BUDGET_MS stays strictly below the spec hard timeout (leaves grace)")
        void budget_leavesGraceUnderSpecTimeout() {
            // Brittle predecessor asserted the exact constant `540000` from `(600-60)*1000` —
            // it would break on any buffer-constant tweak even when the architectural
            // invariant (budget < hard timeout) still held. Now assert the invariant itself.
            var spec = proxySpec(LlmProvider.AZURE_OPENAI, null);
            String budget = factory.build(spec).environment().get("AGENT_BUDGET_MS");
            assertThat(budget).as("AGENT_BUDGET_MS must be present").isNotNull();
            long budgetMs = Long.parseLong(budget);
            long hardTimeoutMs = (long) spec.timeoutSeconds() * 1_000L;
            assertThat(budgetMs)
                .as("Pi's self-watchdog must fire strictly before the SPI hard kill — leaves grace")
                .isLessThan(hardTimeoutMs)
                .isPositive();
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
        @DisplayName("UV_THREADPOOL_SIZE capped at 2 (libuv stack reservation)")
        void uvThreadpoolCapped() {
            // Drops libuv's default 4 worker stacks to 2; Pi is not fs/crypto/dns/zlib heavy in
            // interactive mode, so we trade unused worker capacity for ~2 MB RSS per runner.
            var env = factory.build(proxySpec(LlmProvider.OPENAI, null)).environment();
            assertThat(env).containsEntry("UV_THREADPOOL_SIZE", "2");
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

        @Test
        @DisplayName("baseUrl override exports PI_HEPHAESTUS_* env vars (routes via custom Pi provider)")
        void baseUrlExported() {
            PiPlanSpec spec = new PiPlanSpec(
                LlmProvider.OPENAI,
                CredentialMode.API_KEY,
                "sk-test",
                "gpt-x",
                "https://gpu.example.com/api",
                null,
                true,
                600,
                "pi-runner.mjs",
                Map.of(),
                ""
            );
            assertThat(factory.build(spec).environment())
                .containsEntry("OPENAI_API_KEY", "sk-test")
                .containsEntry("PI_HEPHAESTUS_BASE_URL", "https://gpu.example.com/api")
                .containsEntry("PI_HEPHAESTUS_API_KEY", "sk-test")
                .containsEntry("PI_HEPHAESTUS_MODEL", "gpt-x")
                .doesNotContainKey("OPENAI_BASE_URL");
        }

        @Test
        @DisplayName("baseUrl is ignored in PROXY mode (proxy URL comes from $LLM_PROXY_URL)")
        void baseUrlIgnoredInProxyMode() {
            PiPlanSpec spec = new PiPlanSpec(
                LlmProvider.OPENAI,
                CredentialMode.PROXY,
                null,
                null,
                "https://gpu.example.com/api",
                "job-token-123",
                false,
                600,
                "pi-runner.mjs",
                Map.of(),
                ""
            );
            assertThat(factory.build(spec).environment())
                .doesNotContainKey("OPENAI_BASE_URL")
                .doesNotContainKey("PI_HEPHAESTUS_BASE_URL");
        }
    }

    @Nested
    @DisplayName("custom provider extension")
    class CustomProvider {

        @Test
        @DisplayName("baseUrl-pinned OPENAI spec emits hephaestus-provider.ts under .pi-runtime/extensions/")
        void baseUrlOpenaiEmitsExtension() {
            PiPlanSpec spec = new PiPlanSpec(
                LlmProvider.OPENAI,
                CredentialMode.API_KEY,
                "sk-test",
                "gpt-x",
                "https://gpu.example.com/api",
                null,
                true,
                600,
                "pi-runner.mjs",
                Map.of(),
                ""
            );
            var inputs = factory.build(spec).inputFiles();
            String ts = new String(
                inputs.get(WorkspaceAbi.PI_RUNTIME_PREFIX + "extensions/hephaestus-provider.ts"),
                StandardCharsets.UTF_8
            );
            assertThat(ts)
                .contains("pi.registerProvider(\"hephaestus\"")
                .contains("PI_HEPHAESTUS_BASE_URL")
                .contains("api: \"openai-completions\"");
        }

        @Test
        @DisplayName("baseUrl-pinned ANTHROPIC spec uses anthropic-messages api in the extension")
        void baseUrlAnthropicUsesAnthropicMessagesApi() {
            PiPlanSpec spec = new PiPlanSpec(
                LlmProvider.ANTHROPIC,
                CredentialMode.API_KEY,
                "sk-test",
                "claude-x",
                "https://proxy.example.com",
                null,
                true,
                600,
                "pi-runner.mjs",
                Map.of(),
                ""
            );
            String ts = new String(
                factory
                    .build(spec)
                    .inputFiles()
                    .get(WorkspaceAbi.PI_RUNTIME_PREFIX + "extensions/hephaestus-provider.ts"),
                StandardCharsets.UTF_8
            );
            assertThat(ts).contains("api: \"anthropic-messages\"");
        }

        @Test
        @DisplayName("baseUrl-pinned spec writes defaultProvider=hephaestus in settings.json")
        void baseUrlPinnedSpecSetsHephaestusDefault() throws Exception {
            PiPlanSpec spec = new PiPlanSpec(
                LlmProvider.OPENAI,
                CredentialMode.API_KEY,
                "sk-test",
                "gpt-x",
                "https://gpu.example.com/api",
                null,
                true,
                600,
                "pi-runner.mjs",
                Map.of(),
                ""
            );
            JsonNode settings = objectMapper.readTree(
                new String(
                    factory.build(spec).inputFiles().get(WorkspaceAbi.PI_RUNTIME_PREFIX + "settings.json"),
                    StandardCharsets.UTF_8
                )
            );
            assertThat(settings.path("defaultProvider").asText()).isEqualTo("hephaestus");
            assertThat(settings.path("defaultModel").asText()).isEqualTo("gpt-x");
        }

        @Test
        @DisplayName("no baseUrl → defaultProvider=openai, no extension file written")
        void noBaseUrlNoExtension() throws Exception {
            PiPlanSpec spec = new PiPlanSpec(
                LlmProvider.OPENAI,
                CredentialMode.API_KEY,
                "sk-test",
                "gpt-x",
                null,
                null,
                true,
                600,
                "pi-runner.mjs",
                Map.of(),
                ""
            );
            var inputs = factory.build(spec).inputFiles();
            assertThat(inputs).doesNotContainKey(WorkspaceAbi.PI_RUNTIME_PREFIX + "extensions/hephaestus-provider.ts");
            JsonNode settings = objectMapper.readTree(
                new String(inputs.get(WorkspaceAbi.PI_RUNTIME_PREFIX + "settings.json"), StandardCharsets.UTF_8)
            );
            assertThat(settings.path("defaultProvider").asText()).isEqualTo("openai");
        }

        @Test
        @DisplayName("AZURE_OPENAI with baseUrl never writes the extension (Azure has its own routing)")
        void azureNeverUsesExtension() {
            PiPlanSpec spec = new PiPlanSpec(
                LlmProvider.AZURE_OPENAI,
                CredentialMode.API_KEY,
                "sk-test",
                "gpt-x",
                "https://gpu.example.com/api",
                null,
                true,
                600,
                "pi-runner.mjs",
                Map.of(),
                ""
            );
            assertThat(factory.build(spec).inputFiles()).doesNotContainKey(
                WorkspaceAbi.PI_RUNTIME_PREFIX + "extensions/hephaestus-provider.ts"
            );
        }
    }

    @Nested
    @DisplayName("command")
    class CommandAssembly {

        @Test
        @DisplayName("node invocation carries the lightweight V8 flags ahead of the runner path")
        void nodeFlagsApplied() {
            // The exact flag values are part of the runtime contract — each one buys measurable
            // memory savings on the mentor stress harness. Asserting them here prevents an
            // accidental revert of the audit fixes.
            String body = factory.build(apiKeySpec(LlmProvider.OPENAI)).command().get(2);
            // Find the node-invocation prefix and require all four flags appear before the script.
            int nodeIdx = body.indexOf("node ");
            int scriptIdx = body.indexOf(".run-pi.mjs");
            assertThat(nodeIdx).as("node invocation present").isGreaterThanOrEqualTo(0);
            assertThat(scriptIdx).as("runner script present").isGreaterThan(nodeIdx);
            String nodePrefix = body.substring(nodeIdx, scriptIdx);
            assertThat(nodePrefix)
                .contains("--max-old-space-size=256")
                .contains("--max-semi-space-size=16")
                .contains("--disable-source-maps")
                .contains("--no-warnings")
                .contains("--expose-gc");
        }

        @Test
        @DisplayName("[sh, -c, <assembled>] with node runner at the end and precompute prepended")
        void shCommandWithPrecomputeOrder() {
            PiPlanSpec spec = new PiPlanSpec(
                LlmProvider.OPENAI,
                CredentialMode.API_KEY,
                "sk-test",
                null,
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
            // Precompute step must run before the runner. Don't pin the exact `node` invocation —
            // the command line may carry V8 flags (`--max-old-space-size`, `--disable-source-maps`,
            // …) between `node` and the script path. Asserting the script path alone is enough.
            assertThat(body.indexOf("echo precompute")).isLessThan(body.indexOf("/workspace/.run-pi.mjs"));
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
