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
    class NetworkPolicyContract {

        @Test
        void proxyForwardsToken() {
            var policy = factory.build(proxySpec(LlmProvider.AZURE_OPENAI, null)).networkPolicy();
            assertThat(policy.internetAccess()).isFalse();
            assertThat(policy.llmProxyToken()).isEqualTo("job-token-123");
        }

        @Test
        void apiKeyAllowsInternet() {
            var policy = factory.build(apiKeySpec(LlmProvider.OPENAI)).networkPolicy();
            assertThat(policy.internetAccess()).isTrue();
            assertThat(policy.llmProxyToken()).isNull();
        }
    }

    @Nested
    class InputFiles {

        @Test
        void loadsClasspathResources() {
            var inputs = factory.build(proxySpec(LlmProvider.AZURE_OPENAI, null)).inputFiles();
            assertThat(inputs.get(WorkspaceAbi.ORCHESTRATOR_PATH)).isNotNull();
            // Anchor on a stable schema field rather than the reported-unit noun: pi-orchestrator.md is
            // human-owned prose (still says "findings" until the human aligns it to "observations"), so a
            // "observations"/"findings" sentinel would be brittle. suggestedDiffNotes is part of the
            // observation schema and is not renamed.
            assertThat(new String(inputs.get(WorkspaceAbi.ORCHESTRATOR_PATH), StandardCharsets.UTF_8)).contains(
                "suggestedDiffNotes"
            );
            assertThat(inputs.get(WorkspaceAbi.RUNNER_SCRIPT_FILENAME)).isNotEmpty();
        }

        @Test
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
                Map.of(WorkspaceAbi.CONTEXT_PREFIX + "metadata.json", payload),
                ""
            );
            assertThat(factory.build(spec).inputFiles()).containsKey(WorkspaceAbi.CONTEXT_PREFIX + "metadata.json");
        }
    }

    @Nested
    class SettingsJson {

        @ParameterizedTest(name = "{0} → defaultProvider {1}")
        @CsvSource({ "AZURE_OPENAI, azure-openai-responses", "OPENAI, openai", "ANTHROPIC, anthropic" })
        void mapsProvider(LlmProvider provider, String expected) throws Exception {
            byte[] json = factory.buildPiSettingsJson(provider, "some-model", false);
            JsonNode root = objectMapper.readTree(new String(json, StandardCharsets.UTF_8));
            assertThat(root.path("defaultProvider").asString()).isEqualTo(expected);
            assertThat(root.path("defaultModel").asString()).isEqualTo("some-model");
            assertThat(root.path("transport").asString()).isEqualTo("sse");
        }

        @Test
        void omitsModelAndIncludesCompaction() throws Exception {
            byte[] json = factory.buildPiSettingsJson(LlmProvider.OPENAI, null, false);
            JsonNode root = objectMapper.readTree(new String(json, StandardCharsets.UTF_8));
            assertThat(root.has("defaultModel")).isFalse();
            assertThat(root.path("compaction").path("enabled").asBoolean()).isTrue();
            assertThat(root.path("compaction").path("reserveTokens").asInt()).isEqualTo(16384);
        }
    }

    @Nested
    class Environment {

        @Test
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
        @DisplayName("budget floor applies just above the minimum timeout — stays positive and under the hard kill")
        void budget_floorAppliesAtMinimumTimeout() {
            // Smallest spec PiPlanSpec accepts (timeoutSeconds > TIMEOUT_BUFFER_SECONDS=60). The computed
            // budget (1s) is below MIN_BUDGET_MS, so the floor branch fires — this exercises the otherwise
            // untested Math.max floor. The floor must stay positive AND strictly under the hard-kill deadline.
            int timeoutSeconds = PiRuntimeFactory.TIMEOUT_BUFFER_SECONDS + 1;
            PiPlanSpec spec = new PiPlanSpec(
                LlmProvider.OPENAI,
                CredentialMode.PROXY,
                null,
                null,
                null,
                "job-token-123",
                false,
                timeoutSeconds,
                PRACTICE,
                Map.of(),
                ""
            );
            long budgetMs = Long.parseLong(factory.build(spec).environment().get("AGENT_BUDGET_MS"));
            long hardTimeoutMs = (long) timeoutSeconds * 1_000L;
            assertThat(budgetMs).isEqualTo(PiRuntimeFactory.MIN_BUDGET_MS).isPositive().isLessThan(hardTimeoutMs);
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
        void uvThreadpoolNotForced() {
            var env = factory.build(proxySpec(LlmProvider.OPENAI, null)).environment();
            assertThat(env).doesNotContainKey("UV_THREADPOOL_SIZE");
        }

        @Test
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
    }

    @Nested
    class CustomProvider {

        @Test
        @DisplayName("no .pi/extensions/ files are written — runner-script owns provider registration")
        void noExtensionFilesEmitted() {
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
            assertThat(factory.build(spec).inputFiles().keySet())
                .as(
                    "provider extension files are no longer emitted; the runner script's " +
                        "modelRegistry.registerProvider() call is the single source of truth"
                )
                .noneMatch(k -> k.startsWith(WorkspaceAbi.PI_AGENT_PREFIX + "extensions/"));
        }

        @Test
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
            assertThat(settings.path("defaultProvider").asString()).isEqualTo("hephaestus");
            assertThat(settings.path("defaultModel").asString()).isEqualTo("gpt-x");
        }

        @Test
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
            // runner-script's registerProvider() call matches AND what the gateway expects on the wire.
            assertThat(settings.path("defaultProvider").asString()).isEqualTo("hephaestus");
            assertThat(settings.path("defaultModel").asString()).isEqualTo("openai/gpt-oss-120b");
        }

        @Test
        void noBaseUrlUsesBuiltInProvider() throws Exception {
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
            JsonNode settings = objectMapper.readTree(
                new String(
                    factory.build(spec).inputFiles().get(WorkspaceAbi.PI_AGENT_PREFIX + "settings.json"),
                    StandardCharsets.UTF_8
                )
            );
            assertThat(settings.path("defaultProvider").asString()).isEqualTo("openai");
        }

        @Test
        @DisplayName("AZURE_OPENAI with baseUrl uses azure-openai-responses (Azure has its own routing)")
        void azureNeverUsesHephaestusProvider() throws Exception {
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
            JsonNode settings = objectMapper.readTree(
                new String(
                    factory.build(spec).inputFiles().get(WorkspaceAbi.PI_AGENT_PREFIX + "settings.json"),
                    StandardCharsets.UTF_8
                )
            );
            assertThat(settings.path("defaultProvider").asString()).isEqualTo("azure-openai-responses");
        }
    }

    @Nested
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
