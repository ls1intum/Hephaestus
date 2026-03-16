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

@DisplayName("ClaudeCodeAgentAdapter")
class ClaudeCodeAgentAdapterTest extends BaseUnitTest {

    private ClaudeCodeAgentAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ClaudeCodeAgentAdapter();
    }

    @Test
    @DisplayName("should return CLAUDE_CODE agent type")
    void shouldReturnCorrectAgentType() {
        assertThat(adapter.agentType()).isEqualTo(AgentType.CLAUDE_CODE);
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
        @DisplayName("should set correct Docker image")
        void shouldSetCorrectImage() {
            assertThat(spec.image()).isEqualTo(ClaudeCodeAgentAdapter.IMAGE);
        }

        @Test
        @DisplayName("should bridge proxy URL via shell export")
        void shouldBridgeProxyUrl() {
            String cmd = spec.command().get(2);
            assertThat(cmd).contains("ANTHROPIC_BASE_URL=\"$LLM_PROXY_URL\"");
        }

        @Test
        @DisplayName("should bridge proxy token via shell export")
        void shouldBridgeProxyToken() {
            String cmd = spec.command().get(2);
            assertThat(cmd).contains("ANTHROPIC_API_KEY=\"$LLM_PROXY_TOKEN\"");
        }

        @Test
        @DisplayName("should clear competing auth tokens")
        void shouldClearCompetingAuthTokens() {
            String cmd = spec.command().get(2);
            assertThat(cmd).contains("ANTHROPIC_AUTH_TOKEN=''");
            assertThat(cmd).contains("CLAUDE_CODE_OAUTH_TOKEN=''");
        }

        @Test
        @DisplayName("should set model env var when provided")
        void shouldSetModelEnvVar() {
            assertThat(spec.environment()).containsEntry("ANTHROPIC_MODEL", "claude-sonnet-4-20250514");
        }

        @Test
        @DisplayName("should configure network policy without internet")
        void shouldConfigureNetworkPolicyWithoutInternet() {
            assertThat(spec.networkPolicy()).isNotNull();
            assertThat(spec.networkPolicy().internetAccess()).isFalse();
            assertThat(spec.networkPolicy().llmProxyToken()).isEqualTo("job-token-123");
        }

        @Test
        @DisplayName("should allow internet in proxy mode when allowInternet is true")
        void shouldAllowInternetInProxyModeWhenEnabled() {
            var request = new AgentAdapterRequest(
                AgentType.CLAUDE_CODE,
                LlmProvider.ANTHROPIC,
                CredentialMode.PROXY,
                "claude-sonnet-4-20250514",
                "Review this PR",
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
        @DisplayName("should set ANTHROPIC_API_KEY directly")
        void shouldSetApiKeyDirectly() {
            var spec = adapter.buildSandboxSpec(apiKeyRequest());
            assertThat(spec.environment()).containsEntry("ANTHROPIC_API_KEY", "sk-ant-api03-key");
        }

        @Test
        @DisplayName("should clear OAuth tokens in shell")
        void shouldClearOAuthTokens() {
            var spec = adapter.buildSandboxSpec(apiKeyRequest());
            String cmd = spec.command().get(2);
            assertThat(cmd).contains("ANTHROPIC_AUTH_TOKEN=''");
            assertThat(cmd).contains("CLAUDE_CODE_OAUTH_TOKEN=''");
        }

        @Test
        @DisplayName("should enable internet in network policy")
        void shouldEnableInternet() {
            var spec = adapter.buildSandboxSpec(apiKeyRequest());
            assertThat(spec.networkPolicy().internetAccess()).isTrue();
            assertThat(spec.networkPolicy().llmProxyToken()).isNull();
        }
    }

    @Nested
    @DisplayName("OAUTH mode")
    class OAuthMode {

        @Test
        @DisplayName("should set CLAUDE_CODE_OAUTH_TOKEN")
        void shouldSetOAuthToken() {
            var spec = adapter.buildSandboxSpec(oauthRequest());
            assertThat(spec.environment()).containsEntry("CLAUDE_CODE_OAUTH_TOKEN", "sk-ant-oat01-oauth");
        }

        @Test
        @DisplayName("should clear API key in shell")
        void shouldClearApiKey() {
            var spec = adapter.buildSandboxSpec(oauthRequest());
            String cmd = spec.command().get(2);
            assertThat(cmd).contains("ANTHROPIC_API_KEY=''");
            assertThat(cmd).contains("ANTHROPIC_AUTH_TOKEN=''");
        }

        @Test
        @DisplayName("should enable internet in network policy")
        void shouldEnableInternet() {
            var spec = adapter.buildSandboxSpec(oauthRequest());
            assertThat(spec.networkPolicy().internetAccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("Common behavior")
    class CommonBehavior {

        @Test
        @DisplayName("should inject prompt as input file")
        void shouldInjectPromptAsInputFile() {
            var spec = adapter.buildSandboxSpec(proxyRequest(null));
            assertThat(spec.inputFiles()).containsKey(".prompt");
            assertThat(new String(spec.inputFiles().get(".prompt"), StandardCharsets.UTF_8)).isEqualTo(
                "Review this PR"
            );
        }

        @Test
        @DisplayName("should build shell command with claude CLI flags")
        void shouldBuildCorrectCliFlags() {
            var spec = adapter.buildSandboxSpec(proxyRequest(null));
            String cmd = spec.command().get(2);
            assertThat(cmd).contains("claude -p \"$PROMPT\"");
            assertThat(cmd).contains("--output-format json");
            assertThat(cmd).contains("--dangerously-skip-permissions");
            // --max-turns is NOT a valid Claude Code CLI flag (verified against v2.1.76)
            assertThat(cmd).doesNotContain("--max-turns");
        }

        @Test
        @DisplayName("should set output path")
        void shouldSetOutputPath() {
            var spec = adapter.buildSandboxSpec(proxyRequest(null));
            assertThat(spec.outputPath()).isEqualTo(ClaudeCodeAgentAdapter.OUTPUT_PATH);
        }

        @Test
        @DisplayName("should use sh -c wrapper")
        void shouldUseShCWrapper() {
            var spec = adapter.buildSandboxSpec(proxyRequest(null));
            assertThat(spec.command()).hasSize(3);
            assertThat(spec.command().get(0)).isEqualTo("sh");
            assertThat(spec.command().get(1)).isEqualTo("-c");
        }

        @Test
        @DisplayName("should omit model env var when null")
        void shouldOmitModelWhenNull() {
            var spec = adapter.buildSandboxSpec(proxyRequest(null));
            assertThat(spec.environment()).doesNotContainKey("ANTHROPIC_MODEL");
        }

        @Test
        @DisplayName("should use null security profile for default")
        void shouldUseNullSecurityProfile() {
            var spec = adapter.buildSandboxSpec(proxyRequest(null));
            assertThat(spec.securityProfile()).isNull();
        }
    }

    @Nested
    @DisplayName("parseResult")
    class ParseResult {

        @Test
        @DisplayName("should return success when exit code is 0")
        void shouldReturnSuccessOnZeroExitCode() {
            var sandboxResult = new SandboxResult(
                0,
                Map.of("result.json", "{\"result\":\"ok\"}".getBytes()),
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

    private AgentAdapterRequest proxyRequest(String modelName) {
        return new AgentAdapterRequest(
            AgentType.CLAUDE_CODE,
            LlmProvider.ANTHROPIC,
            CredentialMode.PROXY,
            modelName,
            "Review this PR",
            null,
            "job-token-123",
            false,
            600
        );
    }

    private AgentAdapterRequest apiKeyRequest() {
        return new AgentAdapterRequest(
            AgentType.CLAUDE_CODE,
            LlmProvider.ANTHROPIC,
            CredentialMode.API_KEY,
            null,
            "Review this PR",
            "sk-ant-api03-key",
            null,
            true,
            600
        );
    }

    private AgentAdapterRequest oauthRequest() {
        return new AgentAdapterRequest(
            AgentType.CLAUDE_CODE,
            LlmProvider.ANTHROPIC,
            CredentialMode.OAUTH,
            null,
            "Review this PR",
            "sk-ant-oat01-oauth",
            null,
            true,
            600
        );
    }
}
