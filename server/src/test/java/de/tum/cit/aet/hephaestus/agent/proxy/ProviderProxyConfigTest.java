package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProviderProxyConfigTest extends BaseUnitTest {

    private static final LlmProxyProperties DEFAULT_PROPS = new LlmProxyProperties(
        "https://api.anthropic.com",
        "https://api.openai.com",
        "Authorization",
        true,
        "",
        "api-key",
        false,
        true
    );

    @Nested
    class AnthropicProvider {

        private final ProviderProxyConfig config = ProviderProxyConfig.forProvider(
            LlmProvider.ANTHROPIC,
            DEFAULT_PROPS
        );

        @Test
        void shouldUseXApiKeyHeader() {
            assertThat(config.authHeaderName()).isEqualTo("x-api-key");
        }

        @Test
        void shouldNotUseBearerPrefix() {
            assertThat(config.useBearerPrefix()).isFalse();
        }

        @Test
        void shouldFormatAuthValueAsPlainKey() {
            assertThat(config.formatAuthValue("sk-ant-123")).isEqualTo("sk-ant-123");
        }

        @Test
        void shouldUseCorrectUpstreamUrl() {
            assertThat(config.upstreamBaseUrl()).isEqualTo("https://api.anthropic.com");
        }
    }

    @Nested
    class OpenAIProvider {

        private final ProviderProxyConfig config = ProviderProxyConfig.forProvider(LlmProvider.OPENAI, DEFAULT_PROPS);

        @Test
        void shouldUseAuthorizationHeader() {
            assertThat(config.authHeaderName()).isEqualTo("Authorization");
        }

        @Test
        void shouldUseBearerPrefix() {
            assertThat(config.useBearerPrefix()).isTrue();
        }

        @Test
        void shouldFormatAuthValueWithBearer() {
            assertThat(config.formatAuthValue("sk-123")).isEqualTo("Bearer sk-123");
        }

        @Test
        void shouldUseCorrectUpstreamUrl() {
            assertThat(config.upstreamBaseUrl()).isEqualTo("https://api.openai.com");
        }
    }

    @Nested
    class CustomUpstreamUrls {

        @Test
        void shouldUseCustomUrls() {
            var props = new LlmProxyProperties(
                "https://custom-anthropic.example.com",
                "https://azure.openai.com",
                "Authorization",
                true,
                "",
                "api-key",
                false,
                true
            );
            var anthropicConfig = ProviderProxyConfig.forProvider(LlmProvider.ANTHROPIC, props);
            var openaiConfig = ProviderProxyConfig.forProvider(LlmProvider.OPENAI, props);

            assertThat(anthropicConfig.upstreamBaseUrl()).isEqualTo("https://custom-anthropic.example.com");
            assertThat(openaiConfig.upstreamBaseUrl()).isEqualTo("https://azure.openai.com");
        }
    }

    @Nested
    class AzureOpenAI {

        private final ProviderProxyConfig config = ProviderProxyConfig.forProvider(
            LlmProvider.AZURE_OPENAI,
            new LlmProxyProperties(
                "https://api.anthropic.com",
                "https://api.openai.com",
                "Authorization",
                true,
                "https://myresource.openai.azure.com",
                "api-key",
                false,
                true
            )
        );

        @Test
        void shouldUseApiKeyHeader() {
            assertThat(config.authHeaderName()).isEqualTo("api-key");
        }

        @Test
        void shouldNotUseBearerPrefix() {
            assertThat(config.useBearerPrefix()).isFalse();
        }

        @Test
        void shouldFormatAsPlainKey() {
            assertThat(config.formatAuthValue("mykey123")).isEqualTo("mykey123");
        }
    }

    @Nested
    class BuildUpstreamUrl {

        @Test
        void simpleBaseUrl() {
            assertThat(
                LlmProxyController.buildUpstreamUrl("https://api.openai.com", "/v1/chat/completions", null)
            ).isEqualTo("https://api.openai.com/v1/chat/completions");
        }

        @Test
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
        void incomingQueryOnly() {
            assertThat(
                LlmProxyController.buildUpstreamUrl("https://api.openai.com", "/v1/chat/completions", "user=test")
            ).isEqualTo("https://api.openai.com/v1/chat/completions?user=test");
        }

        @Test
        void emptySubpath() {
            assertThat(LlmProxyController.buildUpstreamUrl("https://api.anthropic.com", "", null)).isEqualTo(
                "https://api.anthropic.com"
            );
        }
    }
}
