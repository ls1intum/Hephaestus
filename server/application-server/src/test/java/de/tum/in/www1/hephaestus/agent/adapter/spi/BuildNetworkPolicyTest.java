package de.tum.in.www1.hephaestus.agent.adapter.spi;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.NetworkPolicy;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AgentAdapter.buildNetworkPolicy")
class BuildNetworkPolicyTest extends BaseUnitTest {

    @Nested
    @DisplayName("PROXY mode")
    class ProxyMode {

        @Test
        @DisplayName("should disable internet when allowInternet is false")
        void shouldDisableInternetWhenNotAllowed() {
            var request = request(CredentialMode.PROXY, false, null, "job-token-123");
            NetworkPolicy policy = AgentAdapter.buildNetworkPolicy(request);

            assertThat(policy.internetAccess()).isFalse();
            assertThat(policy.llmProxyToken()).isEqualTo("job-token-123");
            assertThat(policy.llmProxyUrl()).isNull();
        }

        @Test
        @DisplayName("should enable internet when allowInternet is true")
        void shouldEnableInternetWhenAllowed() {
            var request = request(CredentialMode.PROXY, true, null, "job-token-456");
            NetworkPolicy policy = AgentAdapter.buildNetworkPolicy(request);

            assertThat(policy.internetAccess()).isTrue();
            assertThat(policy.llmProxyToken()).isEqualTo("job-token-456");
            assertThat(policy.llmProxyUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("API_KEY mode")
    class ApiKeyMode {

        @Test
        @DisplayName("should always enable internet regardless of allowInternet flag")
        void shouldAlwaysEnableInternet() {
            // allowInternet=false is intentionally ignored in direct modes — the container
            // must reach the LLM provider directly. Validation in AgentConfigService
            // prevents this combination, but the policy builder is correct to force true.
            var request = request(CredentialMode.API_KEY, false, "sk-key", null);
            NetworkPolicy policy = AgentAdapter.buildNetworkPolicy(request);

            assertThat(policy.internetAccess()).isTrue();
            assertThat(policy.llmProxyToken()).isNull();
            assertThat(policy.llmProxyUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("OAUTH mode")
    class OAuthMode {

        @Test
        @DisplayName("should always enable internet regardless of allowInternet flag")
        void shouldAlwaysEnableInternet() {
            var request = request(CredentialMode.OAUTH, false, "oauth-token", null);
            NetworkPolicy policy = AgentAdapter.buildNetworkPolicy(request);

            assertThat(policy.internetAccess()).isTrue();
            assertThat(policy.llmProxyToken()).isNull();
            assertThat(policy.llmProxyUrl()).isNull();
        }
    }

    private AgentAdapterRequest request(
        CredentialMode mode,
        boolean allowInternet,
        String credential,
        String jobToken
    ) {
        return new AgentAdapterRequest(
            AgentType.CLAUDE_CODE,
            LlmProvider.ANTHROPIC,
            mode,
            "claude-sonnet-4-20250514",
            "test prompt",
            credential,
            jobToken,
            allowInternet,
            600
        );
    }
}
