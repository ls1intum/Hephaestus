package de.tum.in.www1.hephaestus.agent.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

@DisplayName("DirectLlmAgentAdapter")
class DirectLlmAgentAdapterTest extends BaseUnitTest {

    private DirectLlmAgentAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DirectLlmAgentAdapter();
    }

    @Test
    @DisplayName("should return DIRECT_LLM agent type")
    void shouldReturnCorrectAgentType() {
        assertThat(adapter.agentType()).isEqualTo(AgentType.DIRECT_LLM);
    }

    @Nested
    @DisplayName("buildSandboxSpec happy path")
    class BuildSandboxSpecHappyPath {

        private AgentSandboxSpec spec;

        @BeforeEach
        void setUp() {
            spec = adapter.buildSandboxSpec(proxyRequest("claude-sonnet-4-20250514"));
        }

        @Test
        @DisplayName("should set correct Docker image")
        void shouldSetCorrectImage() {
            assertThat(spec.image()).isEqualTo(DirectLlmAgentAdapter.IMAGE);
        }

        @Test
        @DisplayName("should build command that runs node")
        void shouldBuildCommandWithNode() {
            String cmd = spec.command().get(2);
            assertThat(cmd).contains("node /workspace/.call-llm.mjs");
        }

        @Test
        @DisplayName("should build command that creates output directory")
        void shouldBuildCommandWithMkdir() {
            String cmd = spec.command().get(2);
            assertThat(cmd).contains("mkdir -p " + DirectLlmAgentAdapter.OUTPUT_PATH);
        }

        @Test
        @DisplayName("should use sh -c wrapper")
        void shouldUseShCWrapper() {
            assertThat(spec.command()).hasSize(3);
            assertThat(spec.command().get(0)).isEqualTo("sh");
            assertThat(spec.command().get(1)).isEqualTo("-c");
        }

        @Test
        @DisplayName("should include MODEL_NAME in environment")
        void shouldIncludeModelNameInEnv() {
            assertThat(spec.environment()).containsEntry("MODEL_NAME", "claude-sonnet-4-20250514");
        }

        @Test
        @DisplayName("should inject prompt as .prompt input file")
        void shouldInjectPromptAsInputFile() {
            assertThat(spec.inputFiles()).containsKey(".prompt");
            assertThat(new String(spec.inputFiles().get(".prompt"), StandardCharsets.UTF_8)).isEqualTo(
                "Analyze this code"
            );
        }

        @Test
        @DisplayName("should inject script as .call-llm.mjs input file")
        void shouldInjectScriptAsInputFile() {
            assertThat(spec.inputFiles()).containsKey(".call-llm.mjs");
            byte[] scriptBytes = spec.inputFiles().get(".call-llm.mjs");
            assertThat(scriptBytes).isNotNull();
            assertThat(scriptBytes.length).isGreaterThan(0);
        }

        @Test
        @DisplayName("should set output path")
        void shouldSetOutputPath() {
            assertThat(spec.outputPath()).isEqualTo(DirectLlmAgentAdapter.OUTPUT_PATH);
        }

        @Test
        @DisplayName("should use null security profile")
        void shouldUseNullSecurityProfile() {
            assertThat(spec.securityProfile()).isNull();
        }
    }

    @Nested
    @DisplayName("buildSandboxSpec validation")
    class BuildSandboxSpecValidation {

        @Test
        @DisplayName("should throw when modelName is null")
        void shouldThrowWhenModelNameIsNull() {
            var request = proxyRequest(null);
            assertThatThrownBy(() -> adapter.buildSandboxSpec(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelName");
        }

        @Test
        @DisplayName("should throw when modelName is blank")
        void shouldThrowWhenModelNameIsBlank() {
            var request = proxyRequest("   ");
            assertThatThrownBy(() -> adapter.buildSandboxSpec(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelName");
        }

        @Test
        @DisplayName("should throw when modelName is empty string")
        void shouldThrowWhenModelNameIsEmpty() {
            var request = proxyRequest("");
            assertThatThrownBy(() -> adapter.buildSandboxSpec(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelName");
        }
    }

    @Nested
    @DisplayName("Proxy mode")
    class ProxyMode {

        private AgentSandboxSpec spec;

        @BeforeEach
        void setUp() {
            spec = adapter.buildSandboxSpec(proxyRequest("claude-sonnet-4-20250514"));
        }

        @Test
        @DisplayName("should configure network policy without internet by default")
        void shouldConfigureNetworkPolicyWithoutInternet() {
            assertThat(spec.networkPolicy()).isNotNull();
            assertThat(spec.networkPolicy().internetAccess()).isFalse();
        }

        @Test
        @DisplayName("should set proxy token in network policy")
        void shouldSetProxyTokenInNetworkPolicy() {
            assertThat(spec.networkPolicy().llmProxyToken()).isEqualTo("job-token-123");
        }

        @Test
        @DisplayName("should set provider path in network policy")
        void shouldSetProviderPathInNetworkPolicy() {
            assertThat(spec.networkPolicy().llmProxyProviderPath()).isEqualTo("anthropic");
        }

        @Test
        @DisplayName("should allow internet when allowInternet is true")
        void shouldAllowInternetWhenEnabled() {
            var request = new AgentAdapterRequest(
                AgentType.DIRECT_LLM,
                LlmProvider.ANTHROPIC,
                CredentialMode.PROXY,
                "claude-sonnet-4-20250514",
                "Analyze this code",
                null,
                "job-token-123",
                true,
                600
            );
            var internetSpec = adapter.buildSandboxSpec(request);
            assertThat(internetSpec.networkPolicy().internetAccess()).isTrue();
            assertThat(internetSpec.networkPolicy().llmProxyToken()).isEqualTo("job-token-123");
        }
    }

    @Nested
    @DisplayName("API_KEY mode")
    class ApiKeyMode {

        @Test
        @DisplayName("should enable internet in network policy")
        void shouldEnableInternet() {
            var spec = adapter.buildSandboxSpec(apiKeyRequest(LlmProvider.ANTHROPIC));
            assertThat(spec.networkPolicy().internetAccess()).isTrue();
            assertThat(spec.networkPolicy().llmProxyToken()).isNull();
        }

        @Test
        @DisplayName("should not set proxy-specific network fields")
        void shouldNotSetProxyFields() {
            var spec = adapter.buildSandboxSpec(apiKeyRequest(LlmProvider.OPENAI));
            assertThat(spec.networkPolicy().llmProxyUrl()).isNull();
            assertThat(spec.networkPolicy().llmProxyProviderPath()).isNull();
        }

        @Test
        @DisplayName("should still include MODEL_NAME in environment")
        void shouldIncludeModelNameInEnv() {
            var spec = adapter.buildSandboxSpec(apiKeyRequest(LlmProvider.ANTHROPIC));
            assertThat(spec.environment()).containsEntry("MODEL_NAME", "gpt-4o");
        }
    }

    @Nested
    @DisplayName("OAUTH mode")
    class OAuthMode {

        @Test
        @DisplayName("should enable internet in network policy")
        void shouldEnableInternet() {
            var spec = adapter.buildSandboxSpec(oauthRequest(LlmProvider.OPENAI));
            assertThat(spec.networkPolicy().internetAccess()).isTrue();
        }

        @Test
        @DisplayName("should not set proxy token in network policy")
        void shouldNotSetProxyToken() {
            var spec = adapter.buildSandboxSpec(oauthRequest(LlmProvider.ANTHROPIC));
            assertThat(spec.networkPolicy().llmProxyToken()).isNull();
        }
    }

    @Nested
    @DisplayName("Script content validation")
    class ScriptContentValidation {

        private String script;

        @BeforeEach
        void setUp() {
            var spec = adapter.buildSandboxSpec(proxyRequest("claude-sonnet-4-20250514"));
            script = new String(spec.inputFiles().get(".call-llm.mjs"), StandardCharsets.UTF_8);
        }

        @Test
        @DisplayName("should import http module")
        void shouldImportHttpModule() {
            assertThat(script).contains("import http from 'http'");
        }

        @Test
        @DisplayName("should read prompt from /workspace/.prompt")
        void shouldReadPromptFile() {
            assertThat(script).contains("readFileSync('/workspace/.prompt'");
        }

        @Test
        @DisplayName("should write result to result.json")
        void shouldWriteResultJson() {
            assertThat(script).contains("result.json");
        }

        @Test
        @DisplayName("should set max_tokens in request body")
        void shouldSetMaxTokens() {
            assertThat(script).contains("max_tokens: 16384");
        }

        @Test
        @DisplayName("should request JSON response format")
        void shouldRequestJsonFormat() {
            assertThat(script).contains("response_format");
            assertThat(script).contains("json_object");
        }

        @Test
        @DisplayName("should use chat completions endpoint")
        void shouldUseChatCompletionsEndpoint() {
            assertThat(script).contains("/chat/completions");
        }

        @Test
        @DisplayName("should reference LLM_PROXY_URL env var")
        void shouldReferenceLlmProxyUrl() {
            assertThat(script).contains("process.env.LLM_PROXY_URL");
        }

        @Test
        @DisplayName("should reference LLM_PROXY_TOKEN env var")
        void shouldReferenceLlmProxyToken() {
            assertThat(script).contains("process.env.LLM_PROXY_TOKEN");
        }

        @Test
        @DisplayName("should reference MODEL_NAME env var")
        void shouldReferenceModelName() {
            assertThat(script).contains("process.env.MODEL_NAME");
        }

        @Test
        @DisplayName("should set Authorization header with Bearer token")
        void shouldSetAuthorizationHeader() {
            assertThat(script).contains("'Authorization': 'Bearer ' + proxyToken");
        }

        @Test
        @DisplayName("should configure timeout for HTTP request")
        void shouldConfigureTimeout() {
            assertThat(script).contains("timeout: 300000");
        }

        @Test
        @DisplayName("should use POST method")
        void shouldUsePostMethod() {
            assertThat(script).contains("method: 'POST'");
        }
    }

    @Nested
    @DisplayName("parseResult (default implementation)")
    class ParseResult {

        @Test
        @DisplayName("should return success when exit code is 0")
        void shouldReturnSuccessOnZeroExitCode() {
            var sandboxResult = new SandboxResult(
                0,
                Map.of("result.json", "{\"findings\":[]}".getBytes()),
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
        @DisplayName("should return raw output as string from result.json")
        void shouldReturnRawOutputAsString() {
            String jsonContent = "{\"findings\":[{\"title\":\"test finding\"}]}";
            var sandboxResult = new SandboxResult(
                0,
                Map.of("result.json", jsonContent.getBytes(StandardCharsets.UTF_8)),
                "done",
                false,
                Duration.ofSeconds(10)
            );
            AgentResult result = adapter.parseResult(sandboxResult);
            assertThat(result.output().get("rawOutput")).isEqualTo(jsonContent);
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

        @Test
        @DisplayName("should return failure when exit code is 0 but timed out")
        void shouldReturnFailureWhenTimedOut() {
            var sandboxResult = new SandboxResult(0, Map.of(), "killed", true, Duration.ofSeconds(300));
            AgentResult result = adapter.parseResult(sandboxResult);
            assertThat(result.success()).isFalse();
            assertThat(result.output()).containsEntry("timedOut", true);
        }
    }

    // ── Test helpers ──

    private AgentAdapterRequest proxyRequest(String modelName) {
        return new AgentAdapterRequest(
            AgentType.DIRECT_LLM,
            LlmProvider.ANTHROPIC,
            CredentialMode.PROXY,
            modelName,
            "Analyze this code",
            null,
            "job-token-123",
            false,
            600
        );
    }

    private AgentAdapterRequest apiKeyRequest(LlmProvider provider) {
        return new AgentAdapterRequest(
            AgentType.DIRECT_LLM,
            provider,
            CredentialMode.API_KEY,
            "gpt-4o",
            "Analyze this code",
            "sk-api-key-test",
            null,
            true,
            600
        );
    }

    private AgentAdapterRequest oauthRequest(LlmProvider provider) {
        return new AgentAdapterRequest(
            AgentType.DIRECT_LLM,
            provider,
            CredentialMode.OAUTH,
            "gpt-4o",
            "Analyze this code",
            "oauth-token-xyz",
            null,
            true,
            600
        );
    }
}
