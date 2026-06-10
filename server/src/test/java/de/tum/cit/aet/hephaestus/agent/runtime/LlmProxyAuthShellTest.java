package de.tum.cit.aet.hephaestus.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LlmProxyAuthShellTest extends BaseUnitTest {

    @Nested
    class ProxyMode {

        @ParameterizedTest(name = "{0} bridges $LLM_PROXY_URL/_TOKEN")
        @CsvSource(
            {
                "AZURE_OPENAI, AZURE_OPENAI_BASE_URL=\"$LLM_PROXY_URL/openai\", AZURE_OPENAI_API_KEY=\"$LLM_PROXY_TOKEN\"",
                "ANTHROPIC,    ANTHROPIC_BASE_URL=\"$LLM_PROXY_URL\",           ANTHROPIC_API_KEY=\"$LLM_PROXY_TOKEN\"",
            }
        )
        void bridgesProxyEnv(LlmProvider provider, String expectedBaseUrl, String expectedApiKey) {
            Map<String, String> env = new HashMap<>();
            String script = LlmProxyAuthShell.build(CredentialMode.PROXY, provider, null, null, null, env);
            assertThat(script).contains(expectedBaseUrl).contains(expectedApiKey).endsWith(" && ");
            assertThat(env).isEmpty();
        }

        @Test
        void azureSetsApiVersion() {
            Map<String, String> env = new HashMap<>();
            String script = LlmProxyAuthShell.build(
                CredentialMode.PROXY,
                LlmProvider.AZURE_OPENAI,
                null,
                null,
                null,
                env
            );
            assertThat(script).contains("AZURE_OPENAI_API_VERSION=\"2025-04-01-preview\"");
        }

        @Test
        @DisplayName("OpenAI routes the native openai-completions provider through the proxy")
        void openaiChatCompletionsOverProxy() {
            Map<String, String> env = new HashMap<>();
            String script = LlmProxyAuthShell.build(
                CredentialMode.PROXY,
                LlmProvider.OPENAI,
                null,
                null,
                "openai/gpt-oss-120b",
                env
            );
            // The provider points at the proxy; the token (not the real key) is the auth — the proxy swaps it.
            assertThat(script)
                .contains("PI_HEPHAESTUS_BASE_URL=\"$LLM_PROXY_URL\"")
                .contains("PI_HEPHAESTUS_API_KEY=\"$LLM_PROXY_TOKEN\"")
                .doesNotContain("OPENAI_API_KEY")
                .endsWith(" && ");
            assertThat(env).containsEntry("PI_HEPHAESTUS_MODEL", "openai/gpt-oss-120b");
        }
    }

    @Nested
    class ApiKey {

        @Test
        void azureKeyViaShellExport() {
            Map<String, String> env = new HashMap<>();
            String script = LlmProxyAuthShell.build(
                CredentialMode.API_KEY,
                LlmProvider.AZURE_OPENAI,
                "secret-key",
                null,
                null,
                env
            );
            assertThat(env).doesNotContainKey("AZURE_OPENAI_API_KEY");
            assertThat(script).contains("AZURE_OPENAI_API_KEY='secret-key'").endsWith(" && ");
        }

        @ParameterizedTest(name = "{0} key placed in env map")
        @CsvSource({ "OPENAI, OPENAI_API_KEY", "ANTHROPIC, ANTHROPIC_API_KEY" })
        void nonAzureKeyInEnvMap(LlmProvider provider, String envVar) {
            Map<String, String> env = new HashMap<>();
            String script = LlmProxyAuthShell.build(CredentialMode.API_KEY, provider, "sk-test", null, null, env);
            assertThat(env).containsEntry(envVar, "sk-test");
            assertThat(script).isEmpty();
        }

        @Test
        void nullCredentialThrows() {
            Map<String, String> env = new HashMap<>();
            assertThatThrownBy(() ->
                LlmProxyAuthShell.build(CredentialMode.API_KEY, LlmProvider.OPENAI, null, null, null, env)
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("single-quoted credentials escape embedded apostrophes")
        void shellQuotingEscapesApostrophes() {
            String quoted = LlmProxyAuthShell.shellQuote("hello'world");
            assertThat(quoted).isEqualTo("'hello'\\''world'");
        }

        @Test
        void openaiBaseUrlExported() {
            // Direct mode with a gateway base URL: the hephaestus provider extension reads the creds.
            // Critically OPENAI_API_KEY must NOT also be set — Pi's built-in provider would auto-activate
            // against api.openai.com and win resolution (silent 401 against the wrong host).
            Map<String, String> env = new HashMap<>();
            LlmProxyAuthShell.build(
                CredentialMode.API_KEY,
                LlmProvider.OPENAI,
                "sk-test",
                "https://gpu.example.com/api",
                "openai/gpt-oss-120b",
                env
            );
            assertThat(env)
                .doesNotContainKey("OPENAI_API_KEY")
                .containsEntry("PI_HEPHAESTUS_BASE_URL", "https://gpu.example.com/api")
                .containsEntry("PI_HEPHAESTUS_API_KEY", "sk-test")
                // Full id including any provider/ prefix — TUM-style gateways need it on the wire.
                .containsEntry("PI_HEPHAESTUS_MODEL", "openai/gpt-oss-120b");
        }

        @Test
        void anthropicBaseUrlExported() {
            Map<String, String> env = new HashMap<>();
            LlmProxyAuthShell.build(
                CredentialMode.API_KEY,
                LlmProvider.ANTHROPIC,
                "sk-test",
                "https://proxy.example.com",
                "claude-test",
                env
            );
            assertThat(env)
                .doesNotContainKey("ANTHROPIC_API_KEY")
                .containsEntry("PI_HEPHAESTUS_BASE_URL", "https://proxy.example.com")
                .containsEntry("PI_HEPHAESTUS_API_KEY", "sk-test")
                .containsEntry("PI_HEPHAESTUS_MODEL", "claude-test");
        }

        @Test
        void blankBaseUrlSkipped() {
            Map<String, String> env = new HashMap<>();
            LlmProxyAuthShell.build(CredentialMode.API_KEY, LlmProvider.OPENAI, "sk-test", "   ", null, env);
            assertThat(env)
                .doesNotContainKey("PI_HEPHAESTUS_BASE_URL")
                .doesNotContainKey("PI_HEPHAESTUS_API_KEY")
                .doesNotContainKey("PI_HEPHAESTUS_MODEL");
        }

        @Test
        void nullBaseUrlOmitsHephaestusVars() {
            Map<String, String> env = new HashMap<>();
            LlmProxyAuthShell.build(CredentialMode.API_KEY, LlmProvider.OPENAI, "sk-test", null, null, env);
            assertThat(env)
                .containsEntry("OPENAI_API_KEY", "sk-test")
                .doesNotContainKey("PI_HEPHAESTUS_BASE_URL")
                .doesNotContainKey("PI_HEPHAESTUS_API_KEY");
        }
    }
}
