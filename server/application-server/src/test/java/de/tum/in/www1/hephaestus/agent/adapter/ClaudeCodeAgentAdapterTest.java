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
        adapter = new ClaudeCodeAgentAdapter(new com.fasterxml.jackson.databind.ObjectMapper());
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
        @DisplayName("should build shell command using Node.js runner script")
        void shouldBuildCorrectCliFlags() {
            var spec = adapter.buildSandboxSpec(proxyRequest(null));
            String cmd = spec.command().get(2);
            // Command now delegates to runner script instead of inline claude CLI
            assertThat(cmd).contains("node /workspace/.run-claude.mjs");
            assertThat(cmd).doesNotContain("--no-session-persistence");
        }

        @Test
        @DisplayName("should inject runner script with claude CLI flags")
        void shouldInjectRunnerScriptWithFlags() {
            var spec = adapter.buildSandboxSpec(proxyRequest(null));
            assertThat(spec.inputFiles()).containsKey(".run-claude.mjs");
            String script = new String(spec.inputFiles().get(".run-claude.mjs"), StandardCharsets.UTF_8);
            assertThat(script).contains("--output-format");
            assertThat(script).contains("--json-schema");
            assertThat(script).contains("--dangerously-skip-permissions");
            assertThat(script).contains("--max-turns");
            assertThat(script).contains("--effort");
            assertThat(script).contains("--max-budget-usd");
            assertThat(script).contains("--verbose");
            // Self-correction: retries via --continue
            assertThat(script).contains("--continue");
            assertThat(script).contains("hasFindings");
        }

        @Test
        @DisplayName("should copy .analysis directory to output after agent finishes")
        void shouldCopyAnalysisToOutput() {
            var spec = adapter.buildSandboxSpec(proxyRequest(null));
            String cmd = spec.command().get(2);
            assertThat(cmd).contains("cp -r /workspace/.analysis");
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

        @Test
        @DisplayName("should inject CLAUDE.md from classpath")
        void shouldInjectClaudeMdFromClasspath() {
            var spec = adapter.buildSandboxSpec(proxyRequest(null));
            assertThat(spec.inputFiles()).containsKey("CLAUDE.md");
            String claudeMd = new String(spec.inputFiles().get("CLAUDE.md"), StandardCharsets.UTF_8);
            assertThat(claudeMd).isNotBlank();
            assertThat(claudeMd).contains("orchestrator");
        }

        @Test
        @DisplayName("should inject JSON schema for constrained decoding")
        void shouldInjectJsonSchema() {
            var spec = adapter.buildSandboxSpec(proxyRequest(null));
            assertThat(spec.inputFiles()).containsKey(".json-schema");
            String schema = new String(spec.inputFiles().get(".json-schema"), StandardCharsets.UTF_8);
            assertThat(schema).contains("findings");
            assertThat(schema).contains("evidence");
            assertThat(schema).contains("locations");
            assertThat(schema).contains("snippets");
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

        @Test
        @DisplayName("should extract direct findings JSON from --json-schema output")
        void shouldExtractDirectFindingsJson() {
            String directFindings =
                "{\"findings\":[{\"practiceSlug\":\"test\",\"title\":\"ok\",\"verdict\":\"POSITIVE\",\"severity\":\"INFO\",\"confidence\":0.9}]}";
            var sandboxResult = new SandboxResult(
                0,
                Map.of("result.json", directFindings.getBytes()),
                "done",
                false,
                Duration.ofSeconds(10)
            );
            AgentResult result = adapter.parseResult(sandboxResult);
            assertThat(result.success()).isTrue();
            assertThat(result.output().get("rawOutput").toString()).contains("findings");
            assertThat(result.output().get("rawOutput").toString()).contains("practiceSlug");
            // Direct findings object has no event wrapper, so no usage data
            assertThat(result.usage()).isNull();
        }

        @Test
        @DisplayName("should extract LLM usage from result event in array format")
        void shouldExtractLlmUsageFromArrayFormat() {
            String cliOutput = """
                [{"type":"system","message":"starting"},\
                {"type":"assistant","message":{"usage":{"input_tokens":1234,"output_tokens":567}}},\
                {"type":"assistant","message":{"usage":{"input_tokens":800,"output_tokens":300}}},\
                {"type":"result","result":"{\\"findings\\":[]}",\
                "total_cost_usd":0.15,"total_input_tokens":5000,"total_output_tokens":2000,\
                "model":"claude-sonnet-4-6","session_id":"abc123"}]""";
            var sandboxResult = new SandboxResult(
                0,
                Map.of("result.json", cliOutput.getBytes()),
                "done",
                false,
                Duration.ofSeconds(10)
            );
            AgentResult result = adapter.parseResult(sandboxResult);
            assertThat(result.success()).isTrue();
            assertThat(result.usage()).isNotNull();
            assertThat(result.usage().model()).isEqualTo("claude-sonnet-4-6");
            assertThat(result.usage().inputTokens()).isEqualTo(5000);
            assertThat(result.usage().outputTokens()).isEqualTo(2000);
            assertThat(result.usage().costUsd()).isEqualTo(0.15);
            assertThat(result.usage().totalCalls()).isEqualTo(2);
            // Not reported by Claude Code CLI
            assertThat(result.usage().reasoningTokens()).isNull();
            assertThat(result.usage().cacheReadTokens()).isNull();
            assertThat(result.usage().cacheWriteTokens()).isNull();
        }

        @Test
        @DisplayName("should return null usage when result.json is missing")
        void shouldReturnNullUsageWhenResultFileMissing() {
            var sandboxResult = new SandboxResult(0, Map.of(), "done", false, Duration.ofSeconds(10));
            AgentResult result = adapter.parseResult(sandboxResult);
            assertThat(result.usage()).isNull();
        }
    }

    @Nested
    @DisplayName("extractModelResponse")
    class ExtractModelResponse {

        @Test
        @DisplayName("should extract structured_output from result event in array format")
        void shouldExtractStructuredOutputFromArray() {
            String cliOutput = """
                [{"type":"system","message":"starting"},\
                {"type":"result","structured_output":{"findings":[{"practiceSlug":"sec","title":"ok","verdict":"POSITIVE","severity":"INFO","confidence":0.9}]}}]""";
            String result = adapter.extractModelResponse(cliOutput);
            assertThat(result).isNotNull();
            assertThat(result).contains("findings");
            assertThat(result).contains("practiceSlug");
        }

        @Test
        @DisplayName("should extract findings from result text field when structured_output absent")
        void shouldExtractFromResultText() {
            String cliOutput = """
                [{"type":"system","message":"starting"},\
                {"type":"result","result":"{\\"findings\\":[{\\"practiceSlug\\":\\"test\\",\\"title\\":\\"ok\\",\\"verdict\\":\\"POSITIVE\\",\\"severity\\":\\"INFO\\",\\"confidence\\":0.9}]}"}]""";
            String result = adapter.extractModelResponse(cliOutput);
            assertThat(result).isNotNull();
            assertThat(result).contains("findings");
        }

        @Test
        @DisplayName("should extract JSON from mixed text with markdown in result field")
        void shouldExtractJsonFromMixedText() {
            String cliOutput = """
                [{"type":"result","result":"Here is my analysis:\\n```json\\n{\\"findings\\":[{\\"practiceSlug\\":\\"test\\",\\"title\\":\\"found\\",\\"verdict\\":\\"NEGATIVE\\",\\"severity\\":\\"MAJOR\\",\\"confidence\\":0.8}]}\\n```"}]""";
            String result = adapter.extractModelResponse(cliOutput);
            assertThat(result).isNotNull();
            assertThat(result).contains("findings");
            assertThat(result).contains("NEGATIVE");
        }

        @Test
        @DisplayName("should handle single result object format")
        void shouldHandleSingleResultObject() {
            String cliOutput =
                "{\"type\":\"result\",\"structured_output\":{\"findings\":[{\"practiceSlug\":\"test\",\"title\":\"ok\",\"verdict\":\"POSITIVE\",\"severity\":\"INFO\",\"confidence\":0.9}]}}";
            String result = adapter.extractModelResponse(cliOutput);
            assertThat(result).isNotNull();
            assertThat(result).contains("findings");
        }

        @Test
        @DisplayName("should handle direct findings object (no event wrapper)")
        void shouldHandleDirectFindingsObject() {
            String cliOutput =
                "{\"findings\":[{\"practiceSlug\":\"test\",\"title\":\"ok\",\"verdict\":\"POSITIVE\",\"severity\":\"INFO\",\"confidence\":0.9}]}";
            String result = adapter.extractModelResponse(cliOutput);
            assertThat(result).isNotNull();
            assertThat(result).contains("findings");
        }

        @Test
        @DisplayName("should return null for empty array")
        void shouldReturnNullForEmptyArray() {
            assertThat(adapter.extractModelResponse("[]")).isNull();
        }

        @Test
        @DisplayName("should return null for array with no result event")
        void shouldReturnNullForArrayWithNoResultEvent() {
            String cliOutput =
                "[{\"type\":\"system\",\"message\":\"starting\"},{\"type\":\"assistant\",\"message\":\"thinking\"}]";
            assertThat(adapter.extractModelResponse(cliOutput)).isNull();
        }

        @Test
        @DisplayName("should return null for malformed JSON")
        void shouldReturnNullForMalformedJson() {
            assertThat(adapter.extractModelResponse("not json at all")).isNull();
        }

        @Test
        @DisplayName("should return null for result event with empty result text")
        void shouldReturnNullForEmptyResultText() {
            String cliOutput = "[{\"type\":\"result\",\"result\":\"\"}]";
            assertThat(adapter.extractModelResponse(cliOutput)).isNull();
        }

        @Test
        @DisplayName("should return null for result text without findings key")
        void shouldReturnNullForResultWithoutFindings() {
            String cliOutput = "[{\"type\":\"result\",\"result\":\"{\\\"summary\\\":\\\"all good\\\"}\"}]";
            assertThat(adapter.extractModelResponse(cliOutput)).isNull();
        }
    }

    @Nested
    @DisplayName("extractUsage")
    class ExtractUsage {

        @Test
        @DisplayName("should extract usage from result event in JSON array")
        void shouldExtractUsageFromResultEventInArray() {
            String cliOutput = """
                [{"type":"system","message":"starting"},\
                {"type":"assistant","message":{"usage":{"input_tokens":1234,"output_tokens":567}}},\
                {"type":"result","result":"done",\
                "total_cost_usd":0.42,"total_input_tokens":8000,"total_output_tokens":3000,\
                "model":"claude-sonnet-4-6","session_id":"sess-1"}]""";
            AgentResult.LlmUsage usage = adapter.extractUsage(cliOutput);
            assertThat(usage).isNotNull();
            assertThat(usage.model()).isEqualTo("claude-sonnet-4-6");
            assertThat(usage.inputTokens()).isEqualTo(8000);
            assertThat(usage.outputTokens()).isEqualTo(3000);
            assertThat(usage.costUsd()).isEqualTo(0.42);
            assertThat(usage.totalCalls()).isEqualTo(1);
        }

        @Test
        @DisplayName("should count assistant events as LLM calls")
        void shouldCountAssistantEventsAsCalls() {
            String cliOutput = """
                [{"type":"system","message":"starting"},\
                {"type":"assistant","message":{"usage":{"input_tokens":100,"output_tokens":50}}},\
                {"type":"assistant","message":{"usage":{"input_tokens":200,"output_tokens":100}}},\
                {"type":"assistant","message":{"usage":{"input_tokens":300,"output_tokens":150}}},\
                {"type":"result","result":"done",\
                "total_cost_usd":0.10,"total_input_tokens":600,"total_output_tokens":300,\
                "model":"claude-sonnet-4-6"}]""";
            AgentResult.LlmUsage usage = adapter.extractUsage(cliOutput);
            assertThat(usage).isNotNull();
            assertThat(usage.totalCalls()).isEqualTo(3);
        }

        @Test
        @DisplayName("should extract usage from single result object format")
        void shouldExtractUsageFromSingleResultObject() {
            String cliOutput =
                "{\"type\":\"result\",\"result\":\"done\",\"total_cost_usd\":0.05,\"total_input_tokens\":1000,\"total_output_tokens\":500,\"model\":\"claude-sonnet-4-6\"}";
            AgentResult.LlmUsage usage = adapter.extractUsage(cliOutput);
            assertThat(usage).isNotNull();
            assertThat(usage.model()).isEqualTo("claude-sonnet-4-6");
            assertThat(usage.inputTokens()).isEqualTo(1000);
            assertThat(usage.outputTokens()).isEqualTo(500);
            assertThat(usage.costUsd()).isEqualTo(0.05);
            assertThat(usage.totalCalls()).isEqualTo(1);
        }

        @Test
        @DisplayName("should return null for direct findings JSON (no event wrapper)")
        void shouldReturnNullForDirectFindingsJson() {
            String cliOutput =
                "{\"findings\":[{\"practiceSlug\":\"test\",\"title\":\"ok\",\"verdict\":\"POSITIVE\",\"severity\":\"INFO\",\"confidence\":0.9}]}";
            AgentResult.LlmUsage usage = adapter.extractUsage(cliOutput);
            assertThat(usage).isNull();
        }

        @Test
        @DisplayName("should return null for empty array")
        void shouldReturnNullForEmptyArray() {
            assertThat(adapter.extractUsage("[]")).isNull();
        }

        @Test
        @DisplayName("should return null for malformed JSON")
        void shouldReturnNullForMalformedJson() {
            assertThat(adapter.extractUsage("not json")).isNull();
        }

        @Test
        @DisplayName("should return null when result event has no usage fields")
        void shouldReturnNullWhenNoUsageFields() {
            String cliOutput = "[{\"type\":\"result\",\"result\":\"done\"}]";
            assertThat(adapter.extractUsage(cliOutput)).isNull();
        }

        @Test
        @DisplayName("should handle partial usage data (only cost)")
        void shouldHandlePartialUsageData() {
            String cliOutput = "[{\"type\":\"result\",\"result\":\"done\",\"total_cost_usd\":0.03}]";
            AgentResult.LlmUsage usage = adapter.extractUsage(cliOutput);
            assertThat(usage).isNotNull();
            assertThat(usage.costUsd()).isEqualTo(0.03);
            assertThat(usage.model()).isNull();
            assertThat(usage.inputTokens()).isNull();
            assertThat(usage.outputTokens()).isNull();
            assertThat(usage.totalCalls()).isEqualTo(1);
        }

        @Test
        @DisplayName("should handle partial usage data (only model)")
        void shouldHandlePartialUsageDataModelOnly() {
            String cliOutput = "[{\"type\":\"result\",\"result\":\"done\",\"model\":\"claude-opus-4-6\"}]";
            AgentResult.LlmUsage usage = adapter.extractUsage(cliOutput);
            assertThat(usage).isNotNull();
            assertThat(usage.model()).isEqualTo("claude-opus-4-6");
            assertThat(usage.inputTokens()).isNull();
            assertThat(usage.outputTokens()).isNull();
            assertThat(usage.costUsd()).isNull();
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
