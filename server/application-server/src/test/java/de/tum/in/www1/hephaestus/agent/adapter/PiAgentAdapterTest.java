package de.tum.in.www1.hephaestus.agent.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.adapter.spi.AgentAdapterRequest;
import de.tum.in.www1.hephaestus.agent.adapter.spi.AgentSandboxSpec;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxResult;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PiAgentAdapter")
class PiAgentAdapterTest extends BaseUnitTest {

    private PiAgentAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PiAgentAdapter(new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    @DisplayName("should return PI agent type")
    void shouldReturnCorrectAgentType() {
        assertThat(adapter.agentType()).isEqualTo(AgentType.PI);
    }

    @Nested
    @DisplayName("Proxy mode — Azure OpenAI")
    class ProxyModeAzure {

        private AgentSandboxSpec spec;

        @BeforeEach
        void setUp() {
            spec = adapter.buildSandboxSpec(proxyRequest(LlmProvider.AZURE_OPENAI, "gpt-5.4-mini"));
        }

        @Test
        @DisplayName("should set correct Docker image")
        void shouldSetCorrectImage() {
            assertThat(spec.image()).isEqualTo(PiAgentAdapter.IMAGE);
        }

        @Test
        @DisplayName("should bridge proxy URL to AZURE_OPENAI_BASE_URL")
        void shouldBridgeProxyUrl() {
            String cmd = spec.command().get(2);
            assertThat(cmd).contains("AZURE_OPENAI_BASE_URL=\"$LLM_PROXY_URL/openai\"");
        }

        @Test
        @DisplayName("should bridge proxy token to AZURE_OPENAI_API_KEY")
        void shouldBridgeProxyToken() {
            String cmd = spec.command().get(2);
            assertThat(cmd).contains("AZURE_OPENAI_API_KEY=\"$LLM_PROXY_TOKEN\"");
        }

        @Test
        @DisplayName("should set API version for Azure")
        void shouldSetApiVersion() {
            String cmd = spec.command().get(2);
            assertThat(cmd).contains("AZURE_OPENAI_API_VERSION=\"2025-04-01-preview\"");
        }

        @Test
        @DisplayName("should configure network policy without internet")
        void shouldConfigureNetworkPolicyWithoutInternet() {
            assertThat(spec.networkPolicy()).isNotNull();
            assertThat(spec.networkPolicy().internetAccess()).isFalse();
            assertThat(spec.networkPolicy().llmProxyToken()).isEqualTo("job-token-123");
        }
    }

    @Nested
    @DisplayName("Proxy mode — OpenAI")
    class ProxyModeOpenAI {

        @Test
        @DisplayName("should bridge proxy URL to OPENAI_BASE_URL")
        void shouldBridgeProxyUrl() {
            var spec = adapter.buildSandboxSpec(proxyRequest(LlmProvider.OPENAI, "gpt-4o"));
            String cmd = spec.command().get(2);
            assertThat(cmd).contains("OPENAI_BASE_URL=\"$LLM_PROXY_URL\"");
            assertThat(cmd).contains("OPENAI_API_KEY=\"$LLM_PROXY_TOKEN\"");
        }
    }

    @Nested
    @DisplayName("Proxy mode — Anthropic")
    class ProxyModeAnthropic {

        @Test
        @DisplayName("should bridge proxy URL to ANTHROPIC_BASE_URL")
        void shouldBridgeProxyUrl() {
            var spec = adapter.buildSandboxSpec(proxyRequest(LlmProvider.ANTHROPIC, "claude-sonnet-4-20250514"));
            String cmd = spec.command().get(2);
            assertThat(cmd).contains("ANTHROPIC_BASE_URL=\"$LLM_PROXY_URL\"");
            assertThat(cmd).contains("ANTHROPIC_API_KEY=\"$LLM_PROXY_TOKEN\"");
        }
    }

    @Nested
    @DisplayName("API_KEY mode")
    class ApiKeyMode {

        @Test
        @DisplayName("should set AZURE_OPENAI_API_KEY directly for Azure")
        void shouldSetAzureApiKeyDirectly() {
            var spec = adapter.buildSandboxSpec(apiKeyRequest(LlmProvider.AZURE_OPENAI));
            assertThat(spec.environment()).containsEntry("AZURE_OPENAI_API_KEY", "sk-test-key");
        }

        @Test
        @DisplayName("should set OPENAI_API_KEY directly for OpenAI")
        void shouldSetOpenaiApiKeyDirectly() {
            var spec = adapter.buildSandboxSpec(apiKeyRequest(LlmProvider.OPENAI));
            assertThat(spec.environment()).containsEntry("OPENAI_API_KEY", "sk-test-key");
        }

        @Test
        @DisplayName("should set ANTHROPIC_API_KEY directly for Anthropic")
        void shouldSetAnthropicApiKeyDirectly() {
            var spec = adapter.buildSandboxSpec(apiKeyRequest(LlmProvider.ANTHROPIC));
            assertThat(spec.environment()).containsEntry("ANTHROPIC_API_KEY", "sk-test-key");
        }

        @Test
        @DisplayName("should enable internet in network policy")
        void shouldEnableInternet() {
            var spec = adapter.buildSandboxSpec(apiKeyRequest(LlmProvider.AZURE_OPENAI));
            assertThat(spec.networkPolicy().internetAccess()).isTrue();
            assertThat(spec.networkPolicy().llmProxyToken()).isNull();
        }
    }

    @Nested
    @DisplayName("Common behavior")
    class CommonBehavior {

        @Test
        @DisplayName("should inject Pi settings.json")
        void shouldInjectSettingsJson() {
            var spec = adapter.buildSandboxSpec(proxyRequest(LlmProvider.AZURE_OPENAI, "gpt-5.4-mini"));
            assertThat(spec.inputFiles()).containsKey(".pi/settings.json");
            String settings = new String(spec.inputFiles().get(".pi/settings.json"), StandardCharsets.UTF_8);
            assertThat(settings).contains("azure-openai-responses");
            assertThat(settings).contains("gpt-5.4-mini");
        }

        @Test
        @DisplayName("should inject AGENTS.md from classpath")
        void shouldInjectAgentsMdFromClasspath() {
            var spec = adapter.buildSandboxSpec(proxyRequest(LlmProvider.AZURE_OPENAI, null));
            assertThat(spec.inputFiles()).containsKey(".pi/AGENTS.md");
            String agentsMd = new String(spec.inputFiles().get(".pi/AGENTS.md"), StandardCharsets.UTF_8);
            assertThat(agentsMd).isNotBlank();
            assertThat(agentsMd).contains("findings");
        }

        @Test
        @DisplayName("should inject runner script with pi CLI invocation")
        void shouldInjectRunnerScript() {
            var spec = adapter.buildSandboxSpec(proxyRequest(LlmProvider.AZURE_OPENAI, null));
            assertThat(spec.inputFiles()).containsKey(".run-pi.mjs");
            String script = new String(spec.inputFiles().get(".run-pi.mjs"), StandardCharsets.UTF_8);
            assertThat(script).contains("spawnSync('pi'");
            assertThat(script).contains("--session-dir");
            assertThat(script).contains("--tools");
            assertThat(script).contains("write"); // write tool for file output
            assertThat(script).contains("readFileSync('/workspace/.prompt'"); // reads prompt from file
            assertThat(script).doesNotContain("--no-session");
            assertThat(script).contains("'-c'"); // retry via session continuation
            assertThat(script).contains("checkResult"); // checks write-tool output
        }

        @Test
        @DisplayName("should use sh -c wrapper")
        void shouldUseShCWrapper() {
            var spec = adapter.buildSandboxSpec(proxyRequest(LlmProvider.AZURE_OPENAI, null));
            assertThat(spec.command()).hasSize(3);
            assertThat(spec.command().get(0)).isEqualTo("sh");
            assertThat(spec.command().get(1)).isEqualTo("-c");
        }

        @Test
        @DisplayName("should set output path")
        void shouldSetOutputPath() {
            var spec = adapter.buildSandboxSpec(proxyRequest(LlmProvider.AZURE_OPENAI, null));
            assertThat(spec.outputPath()).isEqualTo(PiAgentAdapter.OUTPUT_PATH);
        }

        @Test
        @DisplayName("should set TMPDIR to exec-allowed path")
        void shouldSetTmpdir() {
            var spec = adapter.buildSandboxSpec(proxyRequest(LlmProvider.AZURE_OPENAI, null));
            assertThat(spec.environment()).containsEntry("TMPDIR", "/home/agent/.local/tmp");
        }

        @Test
        @DisplayName("should set PI_CODING_AGENT_DIR env var")
        void shouldSetPiConfigDir() {
            var spec = adapter.buildSandboxSpec(proxyRequest(LlmProvider.AZURE_OPENAI, null));
            assertThat(spec.environment()).containsEntry("PI_CODING_AGENT_DIR", "/workspace/.pi");
        }

        @Test
        @DisplayName("should run precompute step before agent")
        void shouldRunPrecomputeStep() {
            var spec = adapter.buildSandboxSpec(proxyRequest(LlmProvider.AZURE_OPENAI, null));
            String cmd = spec.command().get(2);
            assertThat(cmd).contains("node /workspace/.run-pi.mjs");
        }
    }

    @Nested
    @DisplayName("Settings JSON generation")
    class SettingsJson {

        @Test
        @DisplayName("should map AZURE_OPENAI to azure-openai-responses provider")
        void shouldMapAzureProvider() {
            String settings = new String(
                adapter.buildSettingsJson(proxyRequest(LlmProvider.AZURE_OPENAI, "gpt-5.4-mini")),
                StandardCharsets.UTF_8
            );
            assertThat(settings).contains("azure-openai-responses");
        }

        @Test
        @DisplayName("should map OPENAI to openai provider")
        void shouldMapOpenaiProvider() {
            String settings = new String(
                adapter.buildSettingsJson(proxyRequest(LlmProvider.OPENAI, "gpt-4o")),
                StandardCharsets.UTF_8
            );
            assertThat(settings).contains("\"openai\"");
        }

        @Test
        @DisplayName("should map ANTHROPIC to anthropic provider")
        void shouldMapAnthropicProvider() {
            String settings = new String(
                adapter.buildSettingsJson(proxyRequest(LlmProvider.ANTHROPIC, "claude-sonnet-4-20250514")),
                StandardCharsets.UTF_8
            );
            assertThat(settings).contains("\"anthropic\"");
        }

        @Test
        @DisplayName("should omit model when null")
        void shouldOmitModelWhenNull() {
            String settings = new String(
                adapter.buildSettingsJson(proxyRequest(LlmProvider.AZURE_OPENAI, null)),
                StandardCharsets.UTF_8
            );
            assertThat(settings).doesNotContain("defaultModel");
        }

        @Test
        @DisplayName("should include compaction config")
        void shouldIncludeCompactionConfig() {
            String settings = new String(
                adapter.buildSettingsJson(proxyRequest(LlmProvider.AZURE_OPENAI, null)),
                StandardCharsets.UTF_8
            );
            assertThat(settings).contains("compaction");
            assertThat(settings).contains("reserveTokens");
        }
    }

    @Nested
    @DisplayName("parseResult")
    class ParseResult {

        @Test
        @DisplayName("should return success when exit code is 0")
        void shouldReturnSuccessOnZeroExitCode() {
            String findings =
                "{\"findings\":[{\"practiceSlug\":\"test\",\"title\":\"ok\",\"verdict\":\"POSITIVE\",\"severity\":\"INFO\",\"confidence\":0.9}]}";
            var sandboxResult = new SandboxResult(
                0,
                Map.of("result.json", findings.getBytes()),
                "done",
                false,
                Duration.ofSeconds(10)
            );
            AgentResult result = adapter.parseResult(sandboxResult);
            assertThat(result.success()).isTrue();
            assertThat(result.output()).containsKey("rawOutput");
            assertThat(result.output()).containsEntry("exitCode", 0);
            assertThat(result.output()).containsEntry("timedOut", false);
        }

        @Test
        @DisplayName("should return failure on non-zero exit code")
        void shouldReturnFailureOnNonZeroExitCode() {
            var sandboxResult = new SandboxResult(1, Map.of(), "error", false, Duration.ofSeconds(5));
            AgentResult result = adapter.parseResult(sandboxResult);
            assertThat(result.success()).isFalse();
            assertThat(result.output()).containsEntry("exitCode", 1);
        }

        @Test
        @DisplayName("should return failure on timeout")
        void shouldReturnFailureOnTimeout() {
            var sandboxResult = new SandboxResult(137, Map.of(), "killed", true, Duration.ofSeconds(600));
            AgentResult result = adapter.parseResult(sandboxResult);
            assertThat(result.success()).isFalse();
            assertThat(result.output()).containsEntry("timedOut", true);
        }

        @Test
        @DisplayName("should handle missing result.json gracefully")
        void shouldHandleMissingResultFile() {
            var sandboxResult = new SandboxResult(0, Map.of(), "done", false, Duration.ofSeconds(10));
            AgentResult result = adapter.parseResult(sandboxResult);
            assertThat(result.success()).isTrue();
            assertThat(result.output()).doesNotContainKey("rawOutput");
        }

        @Test
        @DisplayName("should extract findings from direct JSON")
        void shouldExtractDirectFindings() {
            String json =
                "{\"findings\":[{\"practiceSlug\":\"sec\",\"title\":\"ok\",\"verdict\":\"POSITIVE\",\"severity\":\"INFO\",\"confidence\":0.9}]}";
            var sandboxResult = new SandboxResult(
                0,
                Map.of("result.json", json.getBytes()),
                "done",
                false,
                Duration.ofSeconds(10)
            );
            AgentResult result = adapter.parseResult(sandboxResult);
            assertThat(result.success()).isTrue();
            assertThat(result.output().get("rawOutput").toString()).contains("findings");
        }

        @Test
        @DisplayName("should extract findings from mixed text with JSON")
        void shouldExtractFindingsFromMixedText() {
            String mixedText =
                "Here is my analysis:\n```json\n{\"findings\":[{\"practiceSlug\":\"test\",\"title\":\"found\",\"verdict\":\"NEGATIVE\",\"severity\":\"MAJOR\",\"confidence\":0.8}]}\n```";
            var sandboxResult = new SandboxResult(
                0,
                Map.of("result.json", mixedText.getBytes()),
                "done",
                false,
                Duration.ofSeconds(10)
            );
            AgentResult result = adapter.parseResult(sandboxResult);
            assertThat(result.output().get("rawOutput").toString()).contains("findings");
            assertThat(result.output().get("rawOutput").toString()).contains("NEGATIVE");
        }

        @Test
        @DisplayName("should return null usage (Pi does not report usage)")
        void shouldReturnNullUsage() {
            String json = "{\"findings\":[]}";
            var sandboxResult = new SandboxResult(
                0,
                Map.of("result.json", json.getBytes()),
                "done",
                false,
                Duration.ofSeconds(10)
            );
            AgentResult result = adapter.parseResult(sandboxResult);
            assertThat(result.usage()).isNull();
        }

        @Test
        @DisplayName("should sanitize Swift string interpolation escapes")
        void shouldSanitizeSwiftEscapes() {
            String json =
                "{\"findings\":[{\"practiceSlug\":\"test\",\"title\":\"ok\",\"verdict\":\"POSITIVE\",\"severity\":\"INFO\",\"confidence\":0.9,\"reasoning\":\"Text(\\\"\\(weather.temp)°\\\")\"}]}";
            var sandboxResult = new SandboxResult(
                0,
                Map.of("result.json", json.getBytes()),
                "done",
                false,
                Duration.ofSeconds(10)
            );
            AgentResult result = adapter.parseResult(sandboxResult);
            assertThat(result.success()).isTrue();
            assertThat(result.output()).containsKey("rawOutput");
        }

        @Test
        @DisplayName("should preserve valid JSON escapes during sanitization")
        void shouldPreserveValidEscapes() {
            String json =
                "{\"findings\":[{\"practiceSlug\":\"test\",\"title\":\"line1\\nline2\",\"verdict\":\"POSITIVE\",\"severity\":\"INFO\",\"confidence\":0.9}]}";
            var sandboxResult = new SandboxResult(
                0,
                Map.of("result.json", json.getBytes()),
                "done",
                false,
                Duration.ofSeconds(10)
            );
            AgentResult result = adapter.parseResult(sandboxResult);
            assertThat(result.output().get("rawOutput").toString()).contains("line1\\nline2");
        }
    }

    @Nested
    @DisplayName("Azure deployment map")
    class AzureDeploymentMap {

        @Test
        @DisplayName("should set deployment map in env for Azure proxy mode")
        void shouldSetDeploymentMapForAzure() {
            var spec = adapter.buildSandboxSpec(proxyRequest(LlmProvider.AZURE_OPENAI, "gpt-5.4-mini"));
            assertThat(spec.environment()).containsEntry(
                "AZURE_OPENAI_DEPLOYMENT_NAME_MAP",
                "gpt-5.4-mini=gpt-5.4-mini,gpt-5.2=gpt-5.4-mini"
            );
        }

        @Test
        @DisplayName("should not set deployment map for non-Azure providers")
        void shouldNotSetDeploymentMapForNonAzure() {
            var spec = adapter.buildSandboxSpec(proxyRequest(LlmProvider.ANTHROPIC, "claude-sonnet-4-20250514"));
            assertThat(spec.environment()).doesNotContainKey("AZURE_OPENAI_DEPLOYMENT_NAME_MAP");
        }

        @Test
        @DisplayName("should use default deployment name when model is null")
        void shouldUseDefaultDeploymentForNullModel() {
            var spec = adapter.buildSandboxSpec(proxyRequest(LlmProvider.AZURE_OPENAI, null));
            assertThat(spec.environment().get("AZURE_OPENAI_DEPLOYMENT_NAME_MAP")).contains("gpt-5.4-mini");
        }
    }

    @Nested
    @DisplayName("Prompt injection")
    class PromptInjection {

        @Test
        @DisplayName("should inject prompt from request as .prompt file")
        void shouldInjectPromptAsFile() {
            var spec = adapter.buildSandboxSpec(proxyRequest(LlmProvider.AZURE_OPENAI, null));
            assertThat(spec.inputFiles()).containsKey(".prompt");
            assertThat(new String(spec.inputFiles().get(".prompt"), StandardCharsets.UTF_8)).isEqualTo(
                "Review this PR"
            );
        }
    }

    // ── Test helpers ──

    private AgentAdapterRequest proxyRequest(LlmProvider provider, String modelName) {
        return new AgentAdapterRequest(
            AgentType.PI,
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

    private AgentAdapterRequest apiKeyRequest(LlmProvider provider) {
        return new AgentAdapterRequest(
            AgentType.PI,
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
