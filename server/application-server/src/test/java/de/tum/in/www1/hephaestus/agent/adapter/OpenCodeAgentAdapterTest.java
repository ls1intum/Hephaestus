package de.tum.in.www1.hephaestus.agent.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.adapter.spi.AgentAdapterRequest;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxResult;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OpenCodeAgentAdapter")
class OpenCodeAgentAdapterTest extends BaseUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OpenCodeAgentAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OpenCodeAgentAdapter(objectMapper);
    }

    @Test
    @DisplayName("should return OPENCODE agent type")
    void shouldReturnCorrectAgentType() {
        assertThat(adapter.agentType()).isEqualTo(AgentType.OPENCODE);
    }

    @Nested
    @DisplayName("Proxy mode")
    class ProxyMode {

        @Test
        @DisplayName("should set correct Docker image")
        void shouldSetCorrectImage() {
            var spec = adapter.buildSandboxSpec(proxyRequest(LlmProvider.ANTHROPIC, "claude-sonnet-4-20250514"));
            assertThat(spec.image()).isEqualTo(OpenCodeAgentAdapter.IMAGE);
        }

        @Test
        @DisplayName("should generate config with built-in provider prefix")
        void shouldGenerateProxyConfig() {
            var spec = adapter.buildSandboxSpec(proxyRequest(LlmProvider.ANTHROPIC, "claude-sonnet-4-20250514"));
            String config = new String(spec.inputFiles().get("opencode.json"), StandardCharsets.UTF_8);
            // Uses built-in provider prefix — env vars are aliased in the shell command
            assertThat(config).contains("\"anthropic/claude-sonnet-4-20250514\"");
            assertThat(config).contains("\"share\"");
            assertThat(config).contains("\"disabled\"");
        }

        @Test
        @DisplayName("should configure network policy without internet")
        void shouldConfigureNetworkPolicyWithoutInternet() {
            var spec = adapter.buildSandboxSpec(proxyRequest(LlmProvider.ANTHROPIC, "claude-sonnet-4-20250514"));
            assertThat(spec.networkPolicy().internetAccess()).isFalse();
            assertThat(spec.networkPolicy().llmProxyToken()).isEqualTo("job-token-123");
        }

        @Test
        @DisplayName("should allow internet in proxy mode when allowInternet is true")
        void shouldAllowInternetInProxyModeWhenEnabled() {
            var request = new AgentAdapterRequest(
                AgentType.OPENCODE,
                LlmProvider.ANTHROPIC,
                CredentialMode.PROXY,
                "claude-sonnet-4-20250514",
                "Review the code",
                null,
                "job-token-123",
                true,
                600
            );
            var spec = adapter.buildSandboxSpec(request);
            assertThat(spec.networkPolicy().internetAccess()).isTrue();
            assertThat(spec.networkPolicy().llmProxyToken()).isEqualTo("job-token-123");
        }
    }

    @Nested
    @DisplayName("API_KEY mode with Anthropic")
    class ApiKeyModeAnthropic {

        @Test
        @DisplayName("should set ANTHROPIC_API_KEY env var")
        void shouldSetAnthropicApiKey() {
            var spec = adapter.buildSandboxSpec(apiKeyRequest(LlmProvider.ANTHROPIC));
            assertThat(spec.environment()).containsEntry("ANTHROPIC_API_KEY", "sk-ant-api03-test");
        }

        @Test
        @DisplayName("should generate config with anthropic model prefix and no provider field")
        void shouldGenerateAnthropicConfig() {
            var spec = adapter.buildSandboxSpec(apiKeyRequest(LlmProvider.ANTHROPIC));
            String config = new String(spec.inputFiles().get("opencode.json"), StandardCharsets.UTF_8);
            assertThat(config).contains("\"anthropic/");
            assertThat(config).doesNotContain("hephaestus");
            // provider field must not be a string (OpenCode rejects it)
            assertThat(config).doesNotContain("\"provider\"");
        }

        @Test
        @DisplayName("should enable internet in network policy")
        void shouldEnableInternet() {
            var spec = adapter.buildSandboxSpec(apiKeyRequest(LlmProvider.ANTHROPIC));
            assertThat(spec.networkPolicy().internetAccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("API_KEY mode with OpenAI")
    class ApiKeyModeOpenai {

        @Test
        @DisplayName("should set OPENAI_API_KEY env var")
        void shouldSetOpenaiApiKey() {
            var spec = adapter.buildSandboxSpec(apiKeyRequest(LlmProvider.OPENAI));
            assertThat(spec.environment()).containsEntry("OPENAI_API_KEY", "sk-proj-test-key");
        }

        @Test
        @DisplayName("should generate config with openai model prefix and no provider field")
        void shouldGenerateOpenaiConfig() {
            var spec = adapter.buildSandboxSpec(apiKeyRequest(LlmProvider.OPENAI));
            String config = new String(spec.inputFiles().get("opencode.json"), StandardCharsets.UTF_8);
            assertThat(config).contains("\"openai/");
            // provider field must not be present in direct mode
            assertThat(config).doesNotContain("\"provider\"");
        }
    }

    @Nested
    @DisplayName("OAUTH mode")
    class OAuthMode {

        @Test
        @DisplayName("should set provider-specific env var")
        void shouldSetProviderEnvVar() {
            var spec = adapter.buildSandboxSpec(oauthRequest(LlmProvider.ANTHROPIC));
            assertThat(spec.environment()).containsEntry("ANTHROPIC_API_KEY", "oauth-token-abc");
        }

        @Test
        @DisplayName("should enable internet in network policy")
        void shouldEnableInternet() {
            var spec = adapter.buildSandboxSpec(oauthRequest(LlmProvider.OPENAI));
            assertThat(spec.networkPolicy().internetAccess()).isTrue();
        }

        @Test
        @DisplayName("should generate direct-mode config with openai model prefix")
        void shouldGenerateDirectConfig() {
            var spec = adapter.buildSandboxSpec(oauthRequest(LlmProvider.OPENAI));
            String config = new String(spec.inputFiles().get("opencode.json"), StandardCharsets.UTF_8);
            assertThat(config).contains("\"openai/");
            assertThat(config).doesNotContain("hephaestus");
            assertThat(config).doesNotContain("\"provider\"");
        }
    }

    @Nested
    @DisplayName("Common behavior")
    class CommonBehavior {

        @Test
        @DisplayName("should inject prompt as input file")
        void shouldInjectPromptAsInputFile() {
            var spec = adapter.buildSandboxSpec(proxyRequest(LlmProvider.ANTHROPIC, "claude-sonnet-4-20250514"));
            assertThat(spec.inputFiles()).containsKey(".prompt");
            assertThat(new String(spec.inputFiles().get(".prompt"), StandardCharsets.UTF_8)).isEqualTo(
                "Review the code"
            );
        }

        @Test
        @DisplayName("should inject opencode.json config file")
        void shouldInjectConfigFile() {
            var spec = adapter.buildSandboxSpec(proxyRequest(LlmProvider.ANTHROPIC, "claude-sonnet-4-20250514"));
            assertThat(spec.inputFiles()).containsKey("opencode.json");
        }

        @Test
        @DisplayName("should build shell command with Node.js wrapper and proxy env aliases")
        void shouldBuildCorrectCliFlags() {
            var spec = adapter.buildSandboxSpec(proxyRequest(LlmProvider.ANTHROPIC, "claude-sonnet-4-20250514"));
            String cmd = spec.command().get(2);
            // Uses Node.js wrapper to invoke opencode (avoids shell variable issues with large prompts)
            assertThat(cmd).contains("node /workspace/.run-opencode.mjs");
            // In proxy mode, aliases LLM_PROXY_URL/TOKEN to provider-specific env vars
            assertThat(cmd).contains("export ANTHROPIC_BASE_URL=$LLM_PROXY_URL");
            assertThat(cmd).contains("export ANTHROPIC_API_KEY=$LLM_PROXY_TOKEN");
        }

        @Test
        @DisplayName("should set output path")
        void shouldSetOutputPath() {
            var spec = adapter.buildSandboxSpec(proxyRequest(LlmProvider.ANTHROPIC, "claude-sonnet-4-20250514"));
            assertThat(spec.outputPath()).isEqualTo(OpenCodeAgentAdapter.OUTPUT_PATH);
        }

        @Test
        @DisplayName("should use null security profile for default")
        void shouldUseNullSecurityProfile() {
            var spec = adapter.buildSandboxSpec(proxyRequest(LlmProvider.ANTHROPIC, "claude-sonnet-4-20250514"));
            assertThat(spec.securityProfile()).isNull();
        }
    }

    @Nested
    @DisplayName("buildConfigJson")
    class BuildConfigJson {

        @Test
        @DisplayName("should produce valid JSON with provider-prefixed model name")
        void shouldProduceValidJsonWithModel() {
            var request = proxyRequest(LlmProvider.ANTHROPIC, "claude-sonnet-4-20250514");
            String json = new String(adapter.buildConfigJson(request), StandardCharsets.UTF_8);
            // Built-in provider prefix is prepended to model name
            assertThat(json).contains("\"anthropic/claude-sonnet-4-20250514\"");
            assertThat(json).contains("\"share\"");
            assertThat(json).contains("\"disabled\"");
            assertThat(json).contains("\"autoupdate\"");
        }

        @Test
        @DisplayName("should reject null model name")
        void shouldRejectNullModelName() {
            var request = proxyRequest(LlmProvider.ANTHROPIC, null);
            assertThatThrownBy(() -> adapter.buildConfigJson(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelName");
        }

        @Test
        @DisplayName("should reject blank model name")
        void shouldRejectBlankModelName() {
            var request = proxyRequest(LlmProvider.ANTHROPIC, "   ");
            assertThatThrownBy(() -> adapter.buildConfigJson(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelName");
        }

        @Test
        @DisplayName("should safely escape special characters in model name (JSON injection prevention)")
        void shouldEscapeSpecialCharsInModelName() {
            var request = proxyRequest(LlmProvider.ANTHROPIC, "model\": {}, \"exploit\": \"true");
            byte[] configBytes = adapter.buildConfigJson(request);
            String json = new String(configBytes, StandardCharsets.UTF_8);
            // Must produce valid JSON despite injection attempt
            assertThatCode(() -> objectMapper.readTree(json)).doesNotThrowAnyException();
            // The injection attempt must be escaped, not interpreted as JSON structure
            assertThat(json).doesNotContain("\"exploit\"");
        }
    }

    @Nested
    @DisplayName("extractTextFromNdjson")
    class ExtractTextFromNdjson {

        @Test
        @DisplayName("should extract text content from NDJSON streaming output")
        void shouldExtractTextFromNdjson() {
            String ndjson = """
                {"type":"step_start","timestamp":123,"part":{"type":"step-start"}}
                {"type":"text","timestamp":124,"part":{"type":"text","text":"{\\"findings\\":[{\\"title\\":\\"test\\"}]}"}}
                {"type":"step_finish","timestamp":125,"part":{"type":"step-finish"}}""";
            String result = adapter.extractTextFromNdjson(ndjson);
            assertThat(result).contains("findings");
            assertThat(result).contains("test");
        }

        @Test
        @DisplayName("should return plain JSON as-is when not NDJSON")
        void shouldReturnPlainJsonAsIs() {
            String plainJson = "{\"findings\":[{\"title\":\"test\"}]}";
            String result = adapter.extractTextFromNdjson(plainJson);
            assertThat(result).isEqualTo(plainJson);
        }

        @Test
        @DisplayName("should concatenate multiple text events")
        void shouldConcatenateMultipleTextEvents() {
            String ndjson = """
                {"type":"text","timestamp":1,"part":{"type":"text","text":"hello "}}
                {"type":"text","timestamp":2,"part":{"type":"text","text":"world"}}""";
            String result = adapter.extractTextFromNdjson(ndjson);
            assertThat(result).isEqualTo("hello world");
        }

        @Test
        @DisplayName("should return null for null or blank input")
        void shouldReturnNullForBlankInput() {
            assertThat(adapter.extractTextFromNdjson(null)).isNull();
            assertThat(adapter.extractTextFromNdjson("")).isNull();
            assertThat(adapter.extractTextFromNdjson("  ")).isNull();
        }

        @Test
        @DisplayName("should return null when no text events found")
        void shouldReturnNullWhenNoTextEvents() {
            String ndjson = """
                {"type":"step_start","timestamp":123,"part":{"type":"step-start"}}
                {"type":"step_finish","timestamp":125,"part":{"type":"step-finish"}}""";
            String result = adapter.extractTextFromNdjson(ndjson);
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("parseResult")
    class ParseResult {

        @Test
        @DisplayName("should extract text from NDJSON in result.json")
        void shouldExtractTextFromNdjsonResult() {
            String ndjson =
                "{\"type\":\"step_start\",\"part\":{}}\n" +
                "{\"type\":\"text\",\"part\":{\"text\":\"{\\\"findings\\\":[{\\\"title\\\":\\\"test\\\"}]}\"}}\n" +
                "{\"type\":\"step_finish\",\"part\":{}}";
            var sandboxResult = new SandboxResult(
                0,
                Map.of("result.json", ndjson.getBytes()),
                "done",
                false,
                Duration.ofSeconds(10)
            );
            AgentResult result = adapter.parseResult(sandboxResult);
            assertThat(result.success()).isTrue();
            assertThat(result.output().get("rawOutput").toString()).contains("findings");
            assertThat(result.output().get("rawOutput").toString()).doesNotContain("step_start");
        }

        @Test
        @DisplayName("should return success when exit code is 0")
        void shouldReturnSuccessOnZeroExitCode() {
            var sandboxResult = new SandboxResult(
                0,
                Map.of("result.json", "{\"output\":\"done\"}".getBytes()),
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
            assertThat(result.output()).containsEntry("timedOut", false);
        }

        @Test
        @DisplayName("should return failure on timeout")
        void shouldReturnFailureOnTimeout() {
            var sandboxResult = new SandboxResult(137, Map.of(), "killed", true, Duration.ofSeconds(600));
            AgentResult result = adapter.parseResult(sandboxResult);
            assertThat(result.success()).isFalse();
            assertThat(result.output()).containsEntry("timedOut", true);
            assertThat(result.output()).containsEntry("exitCode", 137);
        }

        @Test
        @DisplayName("should handle missing result.json gracefully")
        void shouldHandleMissingResultFile() {
            var sandboxResult = new SandboxResult(0, Map.of(), "done", false, Duration.ofSeconds(10));
            AgentResult result = adapter.parseResult(sandboxResult);
            assertThat(result.success()).isTrue();
            assertThat(result.output()).doesNotContainKey("rawOutput");
            assertThat(result.output()).containsEntry("exitCode", 0);
            assertThat(result.output()).containsEntry("timedOut", false);
        }
    }

    // ── Test helpers ──

    private AgentAdapterRequest proxyRequest(LlmProvider provider, String modelName) {
        return new AgentAdapterRequest(
            AgentType.OPENCODE,
            provider,
            CredentialMode.PROXY,
            modelName,
            "Review the code",
            null,
            "job-token-123",
            false,
            600
        );
    }

    private AgentAdapterRequest apiKeyRequest(LlmProvider provider) {
        String credential = provider == LlmProvider.OPENAI ? "sk-proj-test-key" : "sk-ant-api03-test";
        return new AgentAdapterRequest(
            AgentType.OPENCODE,
            provider,
            CredentialMode.API_KEY,
            "gpt-4o",
            "Review the code",
            credential,
            null,
            true,
            600
        );
    }

    private AgentAdapterRequest oauthRequest(LlmProvider provider) {
        return new AgentAdapterRequest(
            AgentType.OPENCODE,
            provider,
            CredentialMode.OAUTH,
            "gpt-4o",
            "Review the code",
            "oauth-token-abc",
            null,
            true,
            600
        );
    }
}
