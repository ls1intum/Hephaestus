package de.tum.in.www1.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ProviderProxyConfig")
class ProviderProxyConfigTest extends BaseUnitTest {

    private static final LlmProxyProperties DEFAULT_PROPS = new LlmProxyProperties(
        "https://api.anthropic.com",
        "https://api.openai.com",
        "Authorization",
        true
    );

    @Nested
    @DisplayName("Anthropic provider")
    class AnthropicProvider {

        private final ProviderProxyConfig config = ProviderProxyConfig.forProvider(
            LlmProvider.ANTHROPIC,
            DEFAULT_PROPS
        );

        @Test
        @DisplayName("should use x-api-key header")
        void shouldUseXApiKeyHeader() {
            assertThat(config.authHeaderName()).isEqualTo("x-api-key");
        }

        @Test
        @DisplayName("should not use Bearer prefix")
        void shouldNotUseBearerPrefix() {
            assertThat(config.useBearerPrefix()).isFalse();
        }

        @Test
        @DisplayName("should format auth value as plain key")
        void shouldFormatAuthValueAsPlainKey() {
            assertThat(config.formatAuthValue("sk-ant-123")).isEqualTo("sk-ant-123");
        }

        @Test
        @DisplayName("should use correct upstream URL")
        void shouldUseCorrectUpstreamUrl() {
            assertThat(config.upstreamBaseUrl()).isEqualTo("https://api.anthropic.com");
        }
    }

    @Nested
    @DisplayName("OpenAI provider")
    class OpenAIProvider {

        private final ProviderProxyConfig config = ProviderProxyConfig.forProvider(LlmProvider.OPENAI, DEFAULT_PROPS);

        @Test
        @DisplayName("should use Authorization header")
        void shouldUseAuthorizationHeader() {
            assertThat(config.authHeaderName()).isEqualTo("Authorization");
        }

        @Test
        @DisplayName("should use Bearer prefix")
        void shouldUseBearerPrefix() {
            assertThat(config.useBearerPrefix()).isTrue();
        }

        @Test
        @DisplayName("should format auth value with Bearer prefix")
        void shouldFormatAuthValueWithBearer() {
            assertThat(config.formatAuthValue("sk-123")).isEqualTo("Bearer sk-123");
        }

        @Test
        @DisplayName("should use correct upstream URL")
        void shouldUseCorrectUpstreamUrl() {
            assertThat(config.upstreamBaseUrl()).isEqualTo("https://api.openai.com");
        }
    }

    @Nested
    @DisplayName("Custom upstream URLs")
    class CustomUpstreamUrls {

        @Test
        @DisplayName("should use custom upstream URLs from properties")
        void shouldUseCustomUrls() {
            var props = new LlmProxyProperties(
                "https://custom-anthropic.example.com",
                "https://azure.openai.com",
                "Authorization",
                true
            );
            var anthropicConfig = ProviderProxyConfig.forProvider(LlmProvider.ANTHROPIC, props);
            var openaiConfig = ProviderProxyConfig.forProvider(LlmProvider.OPENAI, props);

            assertThat(anthropicConfig.upstreamBaseUrl()).isEqualTo("https://custom-anthropic.example.com");
            assertThat(openaiConfig.upstreamBaseUrl()).isEqualTo("https://azure.openai.com");
        }
    }

    @Nested
    @DisplayName("Azure OpenAI configuration")
    class AzureOpenAI {

        private final ProviderProxyConfig config = ProviderProxyConfig.forProvider(
            LlmProvider.OPENAI,
            new LlmProxyProperties("https://api.anthropic.com", "https://myresource.openai.azure.com", "api-key", false)
        );

        @Test
        @DisplayName("should use api-key header for Azure")
        void shouldUseApiKeyHeader() {
            assertThat(config.authHeaderName()).isEqualTo("api-key");
        }

        @Test
        @DisplayName("should not use Bearer prefix for Azure")
        void shouldNotUseBearerPrefix() {
            assertThat(config.useBearerPrefix()).isFalse();
        }

        @Test
        @DisplayName("should format auth value as plain key for Azure")
        void shouldFormatAsPlainKey() {
            assertThat(config.formatAuthValue("mykey123")).isEqualTo("mykey123");
        }
    }

    @Nested
    @DisplayName("buildUpstreamUrl")
    class BuildUpstreamUrl {

        @Test
        @DisplayName("simple base URL without query params")
        void simpleBaseUrl() {
            assertThat(
                LlmProxyController.buildUpstreamUrl("https://api.openai.com", "/v1/chat/completions", null)
            ).isEqualTo("https://api.openai.com/v1/chat/completions");
        }

        @Test
        @DisplayName("base URL with embedded query params (Azure)")
        void baseUrlWithQueryParams() {
            assertThat(
                LlmProxyController.buildUpstreamUrl(
                    "https://res.openai.azure.com/openai/deployments/gpt-4o?api-version=2024-12-01",
                    "/chat/completions",
                    null
                )
            ).isEqualTo(
                "https://res.openai.azure.com/openai/deployments/gpt-4o/chat/completions?api-version=2024-12-01"
            );
        }

        @Test
        @DisplayName("base URL query params merged with incoming query")
        void mergedQueryParams() {
            assertThat(
                LlmProxyController.buildUpstreamUrl(
                    "https://res.openai.azure.com?api-version=2024-12-01",
                    "/chat/completions",
                    "stream=true"
                )
            ).isEqualTo("https://res.openai.azure.com/chat/completions?api-version=2024-12-01&stream=true");
        }

        @Test
        @DisplayName("incoming query only (no base query)")
        void incomingQueryOnly() {
            assertThat(
                LlmProxyController.buildUpstreamUrl("https://api.openai.com", "/v1/chat/completions", "user=test")
            ).isEqualTo("https://api.openai.com/v1/chat/completions?user=test");
        }

        @Test
        @DisplayName("empty subpath")
        void emptySubpath() {
            assertThat(LlmProxyController.buildUpstreamUrl("https://api.anthropic.com", "", null)).isEqualTo(
                "https://api.anthropic.com"
            );
        }
    }
}
