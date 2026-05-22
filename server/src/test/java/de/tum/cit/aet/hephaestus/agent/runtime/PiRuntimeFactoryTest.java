package de.tum.cit.aet.hephaestus.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.mentor.MentorRunnerProfile;
import de.tum.cit.aet.hephaestus.agent.practice.PracticeRunnerProfile;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@DisplayName("PiRuntimeFactory")
class PiRuntimeFactoryTest extends BaseUnitTest {

    private static final PracticeRunnerProfile PRACTICE = new PracticeRunnerProfile();
    private static final MentorRunnerProfile MENTOR = new MentorRunnerProfile();

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
            PRACTICE,
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
            PRACTICE,
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
            assertThat(inputs.get(WorkspaceAbi.ORCHESTRATOR_PATH)).isNotNull();
            assertThat(new String(inputs.get(WorkspaceAbi.ORCHESTRATOR_PATH), StandardCharsets.UTF_8)).contains(
                "findings"
            );
            assertThat(inputs.get(WorkspaceAbi.RUNNER_SCRIPT_FILENAME)).isNotEmpty();
        }

        @Test
        @DisplayName("extra inputs merge into the input-files map when path is whitelisted")
        void extraInputsMerge() {
            byte[] payload = "deadbeef".getBytes(StandardCharsets.UTF_8);
            PiPlanSpec spec = new PiPlanSpec(
                LlmProvider.OPENAI,
                CredentialMode.API_KEY,
                "sk-test",
                null,
                null,
                null,
                true,
                600,
                PRACTICE,
                Map.of(WorkspaceAbi.CONTEXT_TARGET_PREFIX + "metadata.json", payload),
                ""
            );
            assertThat(factory.build(spec).inputFiles()).containsKey(
                WorkspaceAbi.CONTEXT_TARGET_PREFIX + "metadata.json"
            );
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
        @DisplayName("HOME / TMPDIR on tmpfs; PI_CODING_AGENT_DIR points into the workspace")
        void writableMounts() {
            var env = factory.build(proxySpec(LlmProvider.AZURE_OPENAI, null)).environment();
            assertThat(env)
                .containsEntry("HOME", "/home/agent")
                .containsEntry("TMPDIR", "/home/agent/.local/tmp")
                .containsEntry("PI_CODING_AGENT_DIR", WorkspaceAbi.PI_AGENT_DIR);
        }

        @Test
        @DisplayName("No image-wide UV_THREADPOOL_SIZE override (would serialise libuv fs bursts)")
        void uvThreadpoolNotForced() {
            var env = factory.build(proxySpec(LlmProvider.OPENAI, null)).environment();
            assertThat(env).doesNotContainKey("UV_THREADPOOL_SIZE");
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
                PRACTICE,
                Map.of(),
                ""
            );
            assertThat(factory.build(spec).environment())
                .doesNotContainKey("OPENAI_API_KEY") // would auto-activate built-in OpenAI provider
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
                PRACTICE,
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
        @DisplayName("baseUrl-pinned OPENAI spec emits hephaestus-provider.ts under .pi/extensions/")
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
                PRACTICE,
                Map.of(),
                ""
            );
            var inputs = factory.build(spec).inputFiles();
            String ts = new String(
                inputs.get(WorkspaceAbi.PI_AGENT_PREFIX + "extensions/hephaestus-provider.ts"),
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
                PRACTICE,
                Map.of(),
                ""
            );
            String ts = new String(
                factory
                    .build(spec)
                    .inputFiles()
                    .get(WorkspaceAbi.PI_AGENT_PREFIX + "extensions/hephaestus-provider.ts"),
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
                PRACTICE,
                Map.of(),
                ""
            );
            JsonNode settings = objectMapper.readTree(
                new String(
                    factory.build(spec).inputFiles().get(WorkspaceAbi.PI_AGENT_PREFIX + "settings.json"),
                    StandardCharsets.UTF_8
                )
            );
            assertThat(settings.path("defaultProvider").asText()).isEqualTo("hephaestus");
            assertThat(settings.path("defaultModel").asText()).isEqualTo("gpt-x");
        }

        @Test
        @DisplayName("model id passed verbatim — gateway-routed providers need the full id on the wire")
        void modelIdPassedVerbatim() throws Exception {
            PiPlanSpec spec = new PiPlanSpec(
                LlmProvider.OPENAI,
                CredentialMode.API_KEY,
                "sk-test",
                "openai/gpt-oss-120b",
                "https://gpu.example.com/api",
                null,
                true,
                600,
                PRACTICE,
                Map.of(),
                ""
            );
            JsonNode settings = objectMapper.readTree(
                new String(
                    factory.build(spec).inputFiles().get(WorkspaceAbi.PI_AGENT_PREFIX + "settings.json"),
                    StandardCharsets.UTF_8
                )
            );
            // defaultProvider=hephaestus pins the provider explicitly, so Pi does not parse the
            // slash in defaultModel as a provider/model reference; the full id is what the
            // extension's models[0].id matches AND what the gateway expects on the wire.
            assertThat(settings.path("defaultProvider").asText()).isEqualTo("hephaestus");
            assertThat(settings.path("defaultModel").asText()).isEqualTo("openai/gpt-oss-120b");
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
                PRACTICE,
                Map.of(),
                ""
            );
            var inputs = factory.build(spec).inputFiles();
            assertThat(inputs).doesNotContainKey(WorkspaceAbi.PI_AGENT_PREFIX + "extensions/hephaestus-provider.ts");
            JsonNode settings = objectMapper.readTree(
                new String(inputs.get(WorkspaceAbi.PI_AGENT_PREFIX + "settings.json"), StandardCharsets.UTF_8)
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
                PRACTICE,
                Map.of(),
                ""
            );
            assertThat(factory.build(spec).inputFiles()).doesNotContainKey(
                WorkspaceAbi.PI_AGENT_PREFIX + "extensions/hephaestus-provider.ts"
            );
        }
    }

    @Nested
    @DisplayName("command")
    class CommandAssembly {

        @Test
        @DisplayName("Practice profile contributes --no-warnings and no per-process env")
        void nodeFlagsForPractice() {
            String body = factory.build(apiKeySpec(LlmProvider.OPENAI)).command().get(2);
            int nodeIdx = body.indexOf("node ");
            int scriptIdx = body.indexOf(WorkspaceAbi.RUNNER_SCRIPT_FILENAME);
            String nodePrefix = body.substring(nodeIdx, scriptIdx);
            assertThat(nodePrefix)
                .contains("--no-warnings")
                .doesNotContain("--max-old-space-size")
                .doesNotContain("--expose-gc")
                .doesNotContain("--disable-source-maps");
            // Per-process env immediately preceding `node ` must be empty for practice.
            int lastAmp = body.lastIndexOf("&&", nodeIdx);
            int sliceStart = lastAmp >= 0 ? lastAmp + 2 : 0;
            assertThat(body.substring(sliceStart, nodeIdx)).doesNotContain("LD_PRELOAD").doesNotContain("MALLOC_CONF");
        }

        @Test
        @DisplayName("Mentor profile contributes heap cap, --expose-gc, and jemalloc preload scoped to node")
        void mentorProfileContributesMentorFlagsAndEnv() {
            PiPlanSpec spec = new PiPlanSpec(
                LlmProvider.OPENAI,
                CredentialMode.API_KEY,
                "sk-test",
                null,
                null,
                null,
                true,
                600,
                MENTOR,
                Map.of(),
                ""
            );
            String body = factory.build(spec).command().get(2);
            int nodeIdx = body.indexOf("node ");
            int scriptIdx = body.indexOf(WorkspaceAbi.RUNNER_SCRIPT_FILENAME);
            assertThat(body.substring(nodeIdx, scriptIdx))
                .contains("--max-old-space-size=256")
                .contains("--no-warnings")
                .contains("--expose-gc")
                .doesNotContain("--disable-source-maps");
            assertThat(body.substring(0, nodeIdx))
                .contains("LD_PRELOAD=/usr/local/lib/libjemalloc.so.2")
                .contains("MALLOC_CONF=background_thread:true");
        }

        @Test
        @DisplayName("Mentor and practice profiles resolve to distinct runner scripts")
        void profilesResolveToDistinctScripts() {
            PiPlanSpec base = proxySpec(LlmProvider.AZURE_OPENAI, null);
            byte[] practiceBytes = factory.build(base).inputFiles().get(WorkspaceAbi.RUNNER_SCRIPT_FILENAME);
            PiPlanSpec mentorSpec = new PiPlanSpec(
                LlmProvider.OPENAI,
                CredentialMode.API_KEY,
                "sk-test",
                null,
                null,
                null,
                true,
                600,
                MENTOR,
                Map.of(),
                ""
            );
            byte[] mentorBytes = factory.build(mentorSpec).inputFiles().get(WorkspaceAbi.RUNNER_SCRIPT_FILENAME);
            assertThat(mentorBytes).isNotEqualTo(practiceBytes);
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
                PRACTICE,
                Map.of(),
                "echo precompute && "
            );
            var plan = factory.build(spec);
            assertThat(plan.command()).hasSize(3);
            assertThat(plan.command().get(0)).isEqualTo("sh");
            String body = plan.command().get(2);
            assertThat(body.indexOf("echo precompute")).isLessThan(
                body.indexOf(WorkspaceAbi.WORKSPACE_ROOT + "/" + WorkspaceAbi.RUNNER_SCRIPT_FILENAME)
            );
        }

        @Test
        @DisplayName("No shell-side cp of Pi config")
        void noCopyShim() {
            // baseUrl-pinned spec exercises the custom-provider extension, the only conditional input path.
            PiPlanSpec spec = new PiPlanSpec(
                LlmProvider.OPENAI,
                CredentialMode.API_KEY,
                "sk-test",
                "gpt-x",
                "https://gpu.example.com/api",
                null,
                true,
                600,
                PRACTICE,
                Map.of(),
                ""
            );
            assertThat(factory.build(spec).command().get(2)).doesNotContain(" cp ");
        }
    }
}
