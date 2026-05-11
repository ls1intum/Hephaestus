package de.tum.in.www1.hephaestus.agent.practice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxResult;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("PiPracticeAgent")
class PiPracticeAgentTest extends BaseUnitTest {

    private PiPracticeAgent agent;

    @BeforeEach
    void setUp() {
        agent = new PiPracticeAgent(new ObjectMapper(), PiPracticeAgent.DEFAULT_IMAGE);
    }

    @Nested
    @DisplayName("buildSandboxSpec — proxy mode")
    class ProxyMode {

        @ParameterizedTest(name = "{0} bridges $LLM_PROXY_URL/_TOKEN to provider env vars")
        @CsvSource(
            {
                "AZURE_OPENAI, AZURE_OPENAI_BASE_URL=\"$LLM_PROXY_URL/openai\", AZURE_OPENAI_API_KEY=\"$LLM_PROXY_TOKEN\"",
                "OPENAI,       OPENAI_BASE_URL=\"$LLM_PROXY_URL\",              OPENAI_API_KEY=\"$LLM_PROXY_TOKEN\"",
                "ANTHROPIC,    ANTHROPIC_BASE_URL=\"$LLM_PROXY_URL\",           ANTHROPIC_API_KEY=\"$LLM_PROXY_TOKEN\"",
            }
        )
        void bridgesProxyEnv(LlmProvider provider, String expectedBaseUrl, String expectedApiKey) {
            String cmd = agent.buildSandboxSpec(proxyRequest(provider, null)).command().get(2);
            assertThat(cmd).contains(expectedBaseUrl).contains(expectedApiKey);
        }

        @Test
        @DisplayName("Azure proxy sets the 2025-04-01-preview API version")
        void azureSetsApiVersion() {
            String cmd = agent.buildSandboxSpec(proxyRequest(LlmProvider.AZURE_OPENAI, null)).command().get(2);
            assertThat(cmd).contains("AZURE_OPENAI_API_VERSION=\"2025-04-01-preview\"");
        }

        @Test
        @DisplayName("policy: internet disabled, jobToken propagated")
        void networkPolicyContract() {
            var policy = agent.buildSandboxSpec(proxyRequest(LlmProvider.AZURE_OPENAI, null)).networkPolicy();
            assertThat(policy.internetAccess()).isFalse();
            assertThat(policy.llmProxyToken()).isEqualTo("job-token-123");
        }
    }

    @Nested
    @DisplayName("buildSandboxSpec — API_KEY mode")
    class ApiKeyMode {

        @Test
        @DisplayName("Azure key goes through shell export, not the env map (avoids AZURE_* prefix filter)")
        void azureKeyViaShellExport() {
            var spec = agent.buildSandboxSpec(apiKeyRequest(LlmProvider.AZURE_OPENAI));
            assertThat(spec.environment()).doesNotContainKey("AZURE_OPENAI_API_KEY");
            assertThat(spec.command().get(spec.command().size() - 1)).contains("AZURE_OPENAI_API_KEY=");
        }

        @ParameterizedTest(name = "{0}: {1} placed in env map")
        @CsvSource({ "OPENAI, OPENAI_API_KEY", "ANTHROPIC, ANTHROPIC_API_KEY" })
        void nonAzureKeyInEnvMap(LlmProvider provider, String envVar) {
            assertThat(agent.buildSandboxSpec(apiKeyRequest(provider)).environment()).containsEntry(
                envVar,
                "sk-test-key"
            );
        }

        @Test
        @DisplayName("policy: internet enabled, jobToken null")
        void networkPolicyContract() {
            var policy = agent.buildSandboxSpec(apiKeyRequest(LlmProvider.AZURE_OPENAI)).networkPolicy();
            assertThat(policy.internetAccess()).isTrue();
            assertThat(policy.llmProxyToken()).isNull();
        }
    }

    @Nested
    @DisplayName("buildSandboxSpec — workspace contract")
    class Workspace {

        @Test
        @DisplayName("AGENTS.md classpath resource resolves and contains the schema marker")
        void injectsAgentsMdFromClasspath() {
            byte[] agentsMd = agent
                .buildSandboxSpec(proxyRequest(LlmProvider.AZURE_OPENAI, null))
                .inputFiles()
                .get(".pi/AGENTS.md");
            assertThat(agentsMd).isNotNull();
            assertThat(new String(agentsMd, StandardCharsets.UTF_8)).contains("findings");
        }

        @Test
        @DisplayName("runner script resource resolves and budget env var is set")
        void injectsRunnerScriptAndBudget() {
            var spec = agent.buildSandboxSpec(proxyRequest(LlmProvider.AZURE_OPENAI, null));
            assertThat(spec.inputFiles().get(".run-pi.mjs")).isNotEmpty();
            assertThat(spec.environment()).containsKey("AGENT_BUDGET_MS");
        }
    }

    @Nested
    @DisplayName("buildSettingsJson")
    class SettingsJson {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource(
            { "AZURE_OPENAI, azure-openai-responses", "OPENAI,       \"openai\"", "ANTHROPIC,    \"anthropic\"" }
        )
        void mapsProvider(LlmProvider provider, String expectedProviderToken) {
            String json = new String(agent.buildSettingsJson(proxyRequest(provider, "m")), StandardCharsets.UTF_8);
            assertThat(json).contains(expectedProviderToken);
        }

        @Test
        @DisplayName("omits defaultModel when modelName is null and includes compaction config")
        void omitsModelAndIncludesCompaction() {
            String json = new String(
                agent.buildSettingsJson(proxyRequest(LlmProvider.AZURE_OPENAI, null)),
                StandardCharsets.UTF_8
            );
            assertThat(json).doesNotContain("defaultModel").contains("compaction").contains("reserveTokens");
        }
    }

    @Nested
    @DisplayName("Azure deployment map")
    class AzureDeploymentMap {

        @Test
        @DisplayName("set for Azure proxy with explicit model")
        void setForAzure() {
            assertThat(
                agent.buildSandboxSpec(proxyRequest(LlmProvider.AZURE_OPENAI, "gpt-5.4-mini")).environment()
            ).containsEntry("AZURE_OPENAI_DEPLOYMENT_NAME_MAP", "gpt-5.4-mini=gpt-5.4-mini,gpt-5.2=gpt-5.4-mini");
        }

        @ParameterizedTest(name = "absent for {0}")
        @EnumSource(value = LlmProvider.class, names = { "OPENAI", "ANTHROPIC" })
        void notSetForNonAzure(LlmProvider provider) {
            assertThat(agent.buildSandboxSpec(proxyRequest(provider, null)).environment()).doesNotContainKey(
                "AZURE_OPENAI_DEPLOYMENT_NAME_MAP"
            );
        }
    }

    @Nested
    @DisplayName("parseResult")
    class ParseResult {

        @Test
        @DisplayName("missing result.json returns success without rawOutput")
        void missingResultFile() {
            var result = agent.parseResult(new SandboxResult(0, Map.of(), "done", false, Duration.ofSeconds(10)));
            assertThat(result.success()).isTrue();
            assertThat(result.output()).doesNotContainKey("rawOutput");
        }

        @Test
        @DisplayName("non-zero exit with no output → failure")
        void failureBecomesFailure() {
            var result = agent.parseResult(new SandboxResult(1, Map.of(), "x", false, Duration.ofSeconds(5)));
            assertThat(result.success()).isFalse();
        }

        @Test
        @DisplayName("rebuilds rawOutput from review-state.json when result.json is absent")
        void rebuildsFromReviewState() {
            String reviewState = """
                {"findings":[{"practiceSlug":"x","title":"t","verdict":"NEGATIVE","severity":"MAJOR",
                "confidence":0.9,"evidence":{"locations":[],"snippets":[]},"reasoning":"r","guidance":"g",
                "suggestedDiffNotes":[]}],"delivery":{"mrNote":"please fix"}}""";
            var result = agent.parseResult(
                new SandboxResult(
                    1,
                    Map.of("review-state.json", reviewState.getBytes(StandardCharsets.UTF_8)),
                    "runner failed",
                    false,
                    Duration.ofSeconds(10)
                )
            );
            String raw = result.output().get("rawOutput").toString();
            assertThat(raw).contains("\"x\"").contains("please fix");
        }

        @Test
        @DisplayName("extracts findings JSON from mixed-text output (markdown fence)")
        void extractsJsonFromMixedText() {
            String mixed =
                "Here:\n```json\n{\"findings\":[{\"practiceSlug\":\"t\",\"title\":\"a\"," +
                "\"verdict\":\"NEGATIVE\",\"severity\":\"MAJOR\",\"confidence\":0.8}]}\n```";
            var result = agent.parseResult(
                new SandboxResult(0, Map.of("result.json", mixed.getBytes()), "done", false, Duration.ofSeconds(10))
            );
            assertThat(result.output().get("rawOutput").toString()).contains("findings").contains("NEGATIVE");
        }

        @Test
        @DisplayName("usage + runner-debug surfaced when artifacts present")
        void surfacesUsageAndRunnerDebug() {
            String findings =
                "{\"findings\":[{\"practiceSlug\":\"t\",\"title\":\"x\",\"verdict\":\"POSITIVE\"," +
                "\"severity\":\"INFO\",\"confidence\":0.9}]}";
            String usage =
                "{\"model\":\"m\",\"inputTokens\":10,\"outputTokens\":5,\"cacheReadTokens\":20," +
                "\"costUsd\":0.12,\"totalCalls\":2}";
            String debug = "{\"attempts\":[],\"usageTotals\":{\"totalCalls\":2}}";
            var result = agent.parseResult(
                new SandboxResult(
                    0,
                    Map.of(
                        "result.json",
                        findings.getBytes(),
                        "usage.json",
                        usage.getBytes(),
                        "runner-debug.json",
                        debug.getBytes()
                    ),
                    "done",
                    false,
                    Duration.ofSeconds(10)
                )
            );
            assertThat(result.usage()).isNotNull();
            assertThat(result.usage().model()).isEqualTo("m");
            assertThat(result.usage().totalCalls()).isEqualTo(2);
            assertThat(result.usage().inputTokens()).isEqualTo(10);
            assertThat(result.usage().costUsd()).isEqualTo(0.12);
            assertThat(result.output()).containsKey("runnerDebug");
        }

        @Test
        @DisplayName("sanitizes Swift `\\(...)` interpolation but preserves valid \\n / \\t escapes")
        void sanitizesSwiftEscapes() {
            String json =
                "{\"findings\":[{\"practiceSlug\":\"t\",\"title\":\"line1\\nline2\"," +
                "\"verdict\":\"POSITIVE\",\"severity\":\"INFO\",\"confidence\":0.9," +
                "\"reasoning\":\"Text(\\\"\\(weather.temp)°\\\")\"}]}";
            var result = agent.parseResult(
                new SandboxResult(0, Map.of("result.json", json.getBytes()), "done", false, Duration.ofSeconds(10))
            );
            assertThat(result.success()).isTrue();
            assertThat(result.output().get("rawOutput").toString()).contains("line1\\nline2");
        }

        @Test
        @DisplayName("watchdog-killed marker is surfaced into output")
        void surfacesWatchdogState() {
            String marker = "{\"budgetMs\":540000,\"elapsedMs\":570000,\"reason\":\"x\"}";
            var result = agent.parseResult(
                new SandboxResult(
                    3,
                    Map.of("watchdog-killed.json", marker.getBytes(StandardCharsets.UTF_8)),
                    "killed",
                    false,
                    Duration.ofSeconds(570)
                )
            );
            assertThat(result.output()).containsKey("watchdogKilled");
        }
    }

    private PracticeAgentRequest proxyRequest(LlmProvider provider, String modelName) {
        return new PracticeAgentRequest(
            provider,
            CredentialMode.PROXY,
            modelName,
            "Review this PR",
            null,
            "job-token-123",
            false,
            600
        );
    }

    private PracticeAgentRequest apiKeyRequest(LlmProvider provider) {
        return new PracticeAgentRequest(
            provider,
            CredentialMode.API_KEY,
            null,
            "Review this PR",
            "sk-test-key",
            null,
            true,
            600
        );
    }
}
