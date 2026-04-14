package de.tum.in.www1.hephaestus.agent.adapter.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AgentAdapterRequest")
class AgentAdapterRequestTest extends BaseUnitTest {

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should reject null agentType")
        void shouldRejectNullAgentType() {
            assertThatThrownBy(() ->
                new AgentAdapterRequest(
                    null,
                    LlmProvider.ANTHROPIC,
                    CredentialMode.PROXY,
                    null,
                    "prompt",
                    null,
                    "token",
                    false,
                    600
                )
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null llmProvider")
        void shouldRejectNullLlmProvider() {
            assertThatThrownBy(() ->
                new AgentAdapterRequest(
                    AgentType.CLAUDE_CODE,
                    null,
                    CredentialMode.PROXY,
                    null,
                    "prompt",
                    null,
                    "token",
                    false,
                    600
                )
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null credentialMode")
        void shouldRejectNullCredentialMode() {
            assertThatThrownBy(() ->
                new AgentAdapterRequest(
                    AgentType.CLAUDE_CODE,
                    LlmProvider.ANTHROPIC,
                    null,
                    null,
                    "prompt",
                    null,
                    "token",
                    false,
                    600
                )
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null prompt")
        void shouldRejectNullPrompt() {
            assertThatThrownBy(() ->
                new AgentAdapterRequest(
                    AgentType.CLAUDE_CODE,
                    LlmProvider.ANTHROPIC,
                    CredentialMode.PROXY,
                    null,
                    null,
                    null,
                    "token",
                    false,
                    600
                )
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject blank prompt")
        void shouldRejectBlankPrompt() {
            assertThatThrownBy(() ->
                new AgentAdapterRequest(
                    AgentType.CLAUDE_CODE,
                    LlmProvider.ANTHROPIC,
                    CredentialMode.PROXY,
                    null,
                    "   ",
                    null,
                    "token",
                    false,
                    600
                )
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should reject zero timeout")
        void shouldRejectZeroTimeout() {
            assertThatThrownBy(() ->
                new AgentAdapterRequest(
                    AgentType.CLAUDE_CODE,
                    LlmProvider.ANTHROPIC,
                    CredentialMode.PROXY,
                    null,
                    "prompt",
                    null,
                    "token",
                    false,
                    0
                )
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should reject negative timeout")
        void shouldRejectNegativeTimeout() {
            assertThatThrownBy(() ->
                new AgentAdapterRequest(
                    AgentType.CLAUDE_CODE,
                    LlmProvider.ANTHROPIC,
                    CredentialMode.PROXY,
                    null,
                    "prompt",
                    null,
                    "token",
                    false,
                    -1
                )
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should require jobToken in PROXY mode")
        void shouldRequireJobTokenInProxyMode() {
            assertThatThrownBy(() ->
                new AgentAdapterRequest(
                    AgentType.CLAUDE_CODE,
                    LlmProvider.ANTHROPIC,
                    CredentialMode.PROXY,
                    null,
                    "prompt",
                    null,
                    null,
                    false,
                    600
                )
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jobToken");
        }

        @Test
        @DisplayName("should reject blank jobToken in PROXY mode")
        void shouldRejectBlankJobTokenInProxyMode() {
            assertThatThrownBy(() ->
                new AgentAdapterRequest(
                    AgentType.CLAUDE_CODE,
                    LlmProvider.ANTHROPIC,
                    CredentialMode.PROXY,
                    null,
                    "prompt",
                    null,
                    "   ",
                    false,
                    600
                )
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jobToken");
        }

        @Test
        @DisplayName("should reject blank credential in API_KEY mode")
        void shouldRejectBlankCredentialInApiKeyMode() {
            assertThatThrownBy(() ->
                new AgentAdapterRequest(
                    AgentType.CLAUDE_CODE,
                    LlmProvider.ANTHROPIC,
                    CredentialMode.API_KEY,
                    null,
                    "prompt",
                    "   ",
                    null,
                    true,
                    600
                )
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credential");
        }

        @Test
        @DisplayName("should require credential in API_KEY mode")
        void shouldRequireCredentialInApiKeyMode() {
            assertThatThrownBy(() ->
                new AgentAdapterRequest(
                    AgentType.CLAUDE_CODE,
                    LlmProvider.ANTHROPIC,
                    CredentialMode.API_KEY,
                    null,
                    "prompt",
                    null,
                    null,
                    true,
                    600
                )
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credential");
        }

        @Test
        @DisplayName("should require credential in OAUTH mode")
        void shouldRequireCredentialInOAuthMode() {
            assertThatThrownBy(() ->
                new AgentAdapterRequest(
                    AgentType.CLAUDE_CODE,
                    LlmProvider.ANTHROPIC,
                    CredentialMode.OAUTH,
                    null,
                    "prompt",
                    null,
                    null,
                    true,
                    600
                )
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credential");
        }
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("should accept valid proxy request")
        void shouldAcceptValidProxyRequest() {
            var request = new AgentAdapterRequest(
                AgentType.CLAUDE_CODE,
                LlmProvider.ANTHROPIC,
                CredentialMode.PROXY,
                "claude-sonnet-4-20250514",
                "Review this PR",
                null,
                "job-token-123",
                false,
                600
            );
            assertThat(request.agentType()).isEqualTo(AgentType.CLAUDE_CODE);
            assertThat(request.modelName()).isEqualTo("claude-sonnet-4-20250514");
        }

        @Test
        @DisplayName("should accept valid OAuth request")
        void shouldAcceptValidOAuthRequest() {
            var request = new AgentAdapterRequest(
                AgentType.CLAUDE_CODE,
                LlmProvider.ANTHROPIC,
                CredentialMode.OAUTH,
                null,
                "Review this PR",
                "sk-ant-oat01-token",
                null,
                true,
                300
            );
            assertThat(request.credential()).isEqualTo("sk-ant-oat01-token");
            assertThat(request.modelName()).isNull();
        }

        @Test
        @DisplayName("should accept valid API key request")
        void shouldAcceptValidApiKeyRequest() {
            var request = new AgentAdapterRequest(
                AgentType.PI,
                LlmProvider.OPENAI,
                CredentialMode.API_KEY,
                "o3",
                "Fix this bug",
                "sk-proj-abc123",
                null,
                true,
                600
            );
            assertThat(request.credential()).isEqualTo("sk-proj-abc123");
        }
    }
}
