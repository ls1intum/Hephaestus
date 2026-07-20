package de.tum.cit.aet.hephaestus.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.mentor.MentorRunnerProfile;
import de.tum.cit.aet.hephaestus.agent.practice.PracticeRunnerProfile;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

    private PiPlanSpec spec(String apiProtocol, String modelId, boolean allowInternet) {
        return new PiPlanSpec(
            apiProtocol,
            modelId,
            200000,
            8192,
            false,
            null,
            "job-token-123",
            allowInternet,
            600,
            PRACTICE,
            Map.of(),
            ""
        );
    }

    private PiPlanSpec spec(PiRunnerProfile profile) {
        return new PiPlanSpec(
            "openai-completions",
            "gpt-5.4-mini",
            null,
            null,
            false,
            null,
            "job-token-123",
            false,
            600,
            profile,
            Map.of(),
            ""
        );
    }

    @Nested
    class NetworkPolicyContract {

        @Test
        void alwaysForwardsToken() {
            var policy = factory.build(spec("azure-openai-responses", "gpt-5.4-mini", false)).networkPolicy();
            assertThat(policy.internetAccess()).isFalse();
            assertThat(policy.llmProxyToken()).isEqualTo("job-token-123");
        }

        @Test
        void allowInternetReflectsSpec() {
            var policy = factory.build(spec("openai-completions", "gpt-x", true)).networkPolicy();
            assertThat(policy.internetAccess()).isTrue();
            assertThat(policy.llmProxyToken()).isEqualTo("job-token-123");
        }
    }

    @Nested
    class InputFiles {

        @Test
        void loadsClasspathResources() {
            var inputs = factory.build(spec("azure-openai-responses", "gpt-5.4-mini", false)).inputFiles();
            assertThat(inputs.get(SandboxLayout.ORCHESTRATOR_PATH)).isNotNull();
            assertThat(new String(inputs.get(SandboxLayout.ORCHESTRATOR_PATH), StandardCharsets.UTF_8)).contains(
                "findings"
            );
            assertThat(inputs.get(SandboxLayout.RUNNER_SCRIPT_FILENAME)).isNotEmpty();
        }

        @Test
        @DisplayName(
            "stages the runner's relative-import sidecars beside the runner (pi-finding-normalize.mjs, pi-provider.mjs)"
        )
        void stagesRunnerSidecars() {
            var inputs = factory.build(spec("openai-completions", "m", false)).inputFiles();
            // pi-runner.mjs imports both sidecars relatively; without staging them the sandbox exits 1
            // with ERR_MODULE_NOT_FOUND and no detection runs.
            for (String sidecar : PRACTICE.sidecarScripts()) {
                assertThat(inputs).containsKey(sidecar);
                assertThat(inputs.get(sidecar)).isNotEmpty();
            }
            assertThat(PRACTICE.sidecarScripts()).contains("pi-finding-normalize.mjs", "pi-provider.mjs");
        }

        @Test
        void promptDigest_isStableAcrossBuilds_andIndependentOfModel() {
            // The prompt-version provenance (issue #1363): same scaffolding → same digest, regardless of the
            // model/workspace the run used — an evaluation groups runs by this value. settings.json and
            // pi-provider.json are deliberately excluded from the digested scaffolding.
            String first = factory.build(spec("openai-completions", "model-a", false)).promptDigest();
            String second = factory.build(spec("openai-completions", "model-b", false)).promptDigest();

            assertThat(first).matches("[0-9a-f]{64}").isEqualTo(second);
        }

        @Test
        void promptDigest_matchesScaffoldingBytes() {
            // The digest is exactly the root digest over orchestrator + runner + sidecars — recomputable
            // from the plan's own input files, so a replay can verify it.
            var plan = factory.build(spec("openai-completions", "m", false));
            var scaffolding = new java.util.LinkedHashMap<String, byte[]>();
            scaffolding.put(SandboxLayout.ORCHESTRATOR_PATH, plan.inputFiles().get(SandboxLayout.ORCHESTRATOR_PATH));
            scaffolding.put(
                SandboxLayout.RUNNER_SCRIPT_FILENAME,
                plan.inputFiles().get(SandboxLayout.RUNNER_SCRIPT_FILENAME)
            );
            for (String sidecar : PRACTICE.sidecarScripts()) {
                scaffolding.put(sidecar, plan.inputFiles().get(sidecar));
            }

            assertThat(plan.promptDigest()).isEqualTo(ProvenanceDigest.rootDigestHex(scaffolding));
        }

        @Test
        void extraInputsMerge() {
            byte[] payload = "deadbeef".getBytes(StandardCharsets.UTF_8);
            PiPlanSpec spec = new PiPlanSpec(
                "openai-completions",
                "gpt-x",
                null,
                null,
                false,
                null,
                "job-token-123",
                true,
                600,
                PRACTICE,
                Map.of(SandboxLayout.CONTEXT_PREFIX + "metadata.json", payload),
                ""
            );
            assertThat(factory.build(spec).inputFiles()).containsKey(SandboxLayout.CONTEXT_PREFIX + "metadata.json");
        }
    }

    @Nested
    class SettingsJson {

        @Test
        void alwaysUsesHephaestusProvider() throws Exception {
            byte[] json = factory.buildPiSettingsJson("some-model");
            JsonNode root = objectMapper.readTree(new String(json, StandardCharsets.UTF_8));
            assertThat(root.path("defaultProvider").asString()).isEqualTo("hephaestus");
            assertThat(root.path("defaultModel").asString()).isEqualTo("some-model");
            assertThat(root.path("transport").asString()).isEqualTo("sse");
        }

        @Test
        void omitsModelAndIncludesCompaction() throws Exception {
            byte[] json = factory.buildPiSettingsJson(null);
            JsonNode root = objectMapper.readTree(new String(json, StandardCharsets.UTF_8));
            assertThat(root.has("defaultModel")).isFalse();
            assertThat(root.path("compaction").path("enabled").asBoolean()).isTrue();
            assertThat(root.path("compaction").path("reserveTokens").asInt()).isEqualTo(16384);
        }
    }

    @Nested
    class ProviderConfigJson {

        @Test
        void capturesResolvedBehaviour() throws Exception {
            PiPlanSpec spec = new PiPlanSpec(
                "openai-completions",
                "gpt-oss-120b",
                131072,
                4096,
                true,
                "anthropic",
                "job-token-123",
                false,
                600,
                PRACTICE,
                Map.of(),
                ""
            );
            byte[] json = factory.buildProviderConfigJson(spec);
            JsonNode root = objectMapper.readTree(new String(json, StandardCharsets.UTF_8));

            assertThat(root.path("apiProtocol").asString()).isEqualTo("openai-completions");
            assertThat(root.path("modelId").asString()).isEqualTo("gpt-oss-120b");
            assertThat(root.path("contextWindow").asInt()).isEqualTo(131072);
            assertThat(root.path("maxOutputTokens").asInt()).isEqualTo(4096);
            assertThat(root.path("supportsReasoning").asBoolean()).isTrue();
            assertThat(root.path("cacheControlFormat").asString()).isEqualTo("anthropic");
            // The credential NEVER lands here — only non-secret behaviour.
            assertThat(new String(json, StandardCharsets.UTF_8)).doesNotContain("job-token-123");
        }

        @Test
        void omitsNullCapabilityFields() throws Exception {
            byte[] json = factory.buildProviderConfigJson(spec("anthropic-messages", "claude", false));
            JsonNode root = objectMapper.readTree(new String(json, StandardCharsets.UTF_8));
            assertThat(root.has("contextWindow")).isTrue(); // spec() helper sets 200000
            assertThat(root.has("cacheControlFormat")).isFalse();
        }

        @Test
        void writtenAtWorkspaceRootFilename() {
            var inputs = factory.build(spec("openai-completions", "m", false)).inputFiles();
            assertThat(inputs).containsKey(SandboxLayout.PROVIDER_CONFIG_FILENAME);
        }
    }

    @Nested
    class Environment {

        @Test
        void budget_leavesGraceUnderSpecTimeout() {
            var pspec = spec("azure-openai-responses", "gpt-5.4-mini", false);
            String budget = factory.build(pspec).environment().get("AGENT_BUDGET_MS");
            assertThat(budget).as("AGENT_BUDGET_MS must be present").isNotNull();
            long budgetMs = Long.parseLong(budget);
            long hardTimeoutMs = (long) pspec.timeoutSeconds() * 1_000L;
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
                "openai-completions",
                "gpt-x",
                null,
                null,
                false,
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
            var env = factory.build(spec("azure-openai-responses", "gpt-5.4-mini", false)).environment();
            assertThat(env)
                .containsEntry("HOME", "/home/agent")
                .containsEntry("TMPDIR", "/home/agent/.local/tmp")
                .containsEntry("PI_CODING_AGENT_DIR", SandboxLayout.PI_AGENT_DIR);
        }

        @Test
        void uvThreadpoolNotForced() {
            var env = factory.build(spec("openai-completions", "gpt-x", false)).environment();
            assertThat(env).doesNotContainKey("UV_THREADPOOL_SIZE");
        }

        @Test
        @DisplayName(
            "No shell-side credential export — LLM_PROXY_URL/TOKEN come from NetworkPolicy, not AGENT_BUDGET_MS env"
        )
        void noLegacyProviderEnvVars() {
            var env = factory.build(spec("openai-completions", "gpt-x", false)).environment();
            assertThat(env).doesNotContainKeys(
                "PI_HEPHAESTUS_BASE_URL",
                "PI_HEPHAESTUS_API_KEY",
                "PI_HEPHAESTUS_MODEL",
                "AZURE_OPENAI_API_KEY",
                "AZURE_OPENAI_BASE_URL",
                "AZURE_OPENAI_DEPLOYMENT_NAME_MAP",
                "OPENAI_API_KEY",
                "ANTHROPIC_API_KEY"
            );
        }
    }

    @Nested
    class CommandAssembly {

        @Test
        @DisplayName("Practice profile contributes --no-warnings and no per-process env")
        void nodeFlagsForPractice() {
            String body = factory.build(spec("openai-completions", "gpt-x", false)).command().get(2);
            int nodeIdx = body.indexOf("node ");
            int scriptIdx = body.indexOf(SandboxLayout.RUNNER_SCRIPT_FILENAME);
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
            String body = factory.build(spec(MENTOR)).command().get(2);
            int nodeIdx = body.indexOf("node ");
            int scriptIdx = body.indexOf(SandboxLayout.RUNNER_SCRIPT_FILENAME);
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
            PiPlanSpec base = spec("azure-openai-responses", "gpt-5.4-mini", false);
            byte[] practiceBytes = factory.build(base).inputFiles().get(SandboxLayout.RUNNER_SCRIPT_FILENAME);
            byte[] mentorBytes = factory.build(spec(MENTOR)).inputFiles().get(SandboxLayout.RUNNER_SCRIPT_FILENAME);
            assertThat(mentorBytes).isNotEqualTo(practiceBytes);
        }

        @Test
        void shCommandWithPrecomputeOrder() {
            PiPlanSpec spec = new PiPlanSpec(
                "openai-completions",
                "gpt-x",
                null,
                null,
                false,
                null,
                "job-token-123",
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
                body.indexOf(SandboxLayout.WORKSPACE_ROOT + "/" + SandboxLayout.RUNNER_SCRIPT_FILENAME)
            );
        }

        @Test
        @DisplayName("No shell-side cp/export of Pi config — provider.json + settings.json are the whole contract")
        void noCopyShim() {
            assertThat(factory.build(spec("openai-completions", "gpt-x", false)).command().get(2))
                .doesNotContain(" cp ")
                .doesNotContain("export ");
        }
    }
}
