package de.tum.in.www1.hephaestus.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("LlmProxyAuthShell")
class LlmProxyAuthShellTest extends BaseUnitTest {

    @Nested
    @DisplayName("PROXY mode")
    class ProxyMode {

        @ParameterizedTest(name = "{0} bridges $LLM_PROXY_URL/_TOKEN")
        @CsvSource(
            {
                "AZURE_OPENAI, AZURE_OPENAI_BASE_URL=\"$LLM_PROXY_URL/openai\", AZURE_OPENAI_API_KEY=\"$LLM_PROXY_TOKEN\"",
                "OPENAI,       OPENAI_BASE_URL=\"$LLM_PROXY_URL\",              OPENAI_API_KEY=\"$LLM_PROXY_TOKEN\"",
                "ANTHROPIC,    ANTHROPIC_BASE_URL=\"$LLM_PROXY_URL\",           ANTHROPIC_API_KEY=\"$LLM_PROXY_TOKEN\"",
            }
        )
        void bridgesProxyEnv(LlmProvider provider, String expectedBaseUrl, String expectedApiKey) {
            Map<String, String> env = new HashMap<>();
            String script = LlmProxyAuthShell.build(CredentialMode.PROXY, provider, null, env);
            assertThat(script).contains(expectedBaseUrl).contains(expectedApiKey).endsWith(" && ");
            assertThat(env).isEmpty();
        }

        @Test
        @DisplayName("Azure proxy sets the 2025-04-01-preview API version")
        void azureSetsApiVersion() {
            Map<String, String> env = new HashMap<>();
            String script = LlmProxyAuthShell.build(CredentialMode.PROXY, LlmProvider.AZURE_OPENAI, null, env);
            assertThat(script).contains("AZURE_OPENAI_API_VERSION=\"2025-04-01-preview\"");
        }
    }

    @Nested
    @DisplayName("API_KEY / OAUTH mode")
    class ApiKey {

        @Test
        @DisplayName("Azure key flows through shell export, not env map (sandbox AZURE_* prefix filter)")
        void azureKeyViaShellExport() {
            Map<String, String> env = new HashMap<>();
            String script = LlmProxyAuthShell.build(
                CredentialMode.API_KEY,
                LlmProvider.AZURE_OPENAI,
                "secret-key",
                env
            );
            assertThat(env).doesNotContainKey("AZURE_OPENAI_API_KEY");
            assertThat(script).contains("AZURE_OPENAI_API_KEY='secret-key'").endsWith(" && ");
        }

        @ParameterizedTest(name = "{0} key placed in env map")
        @CsvSource({ "OPENAI, OPENAI_API_KEY", "ANTHROPIC, ANTHROPIC_API_KEY" })
        void nonAzureKeyInEnvMap(LlmProvider provider, String envVar) {
            Map<String, String> env = new HashMap<>();
            String script = LlmProxyAuthShell.build(CredentialMode.API_KEY, provider, "sk-test", env);
            assertThat(env).containsEntry(envVar, "sk-test");
            assertThat(script).isEmpty();
        }

        @Test
        @DisplayName("OAUTH mode treats credential as API key (Pi has no OAuth mode)")
        void oauthBehavesAsApiKey() {
            Map<String, String> env = new HashMap<>();
            String script = LlmProxyAuthShell.build(CredentialMode.OAUTH, LlmProvider.OPENAI, "sk-oauth", env);
            assertThat(env).containsEntry("OPENAI_API_KEY", "sk-oauth");
            assertThat(script).isEmpty();
        }

        @Test
        @DisplayName("null credential in API_KEY mode throws")
        void nullCredentialThrows() {
            Map<String, String> env = new HashMap<>();
            assertThatThrownBy(() ->
                LlmProxyAuthShell.build(CredentialMode.API_KEY, LlmProvider.OPENAI, null, env)
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("single-quoted credentials escape embedded apostrophes")
        void shellQuotingEscapesApostrophes() {
            String quoted = LlmProxyAuthShell.shellQuote("hello'world");
            assertThat(quoted).isEqualTo("'hello'\\''world'");
        }

        @Test
        @DisplayName("OPENAI baseUrl override exports PI_HEPHAESTUS_* env vars (routes via custom provider)")
        void openaiBaseUrlExported() {
            // Pi does NOT read OPENAI_BASE_URL natively. The base URL is consumed by the
            // hephaestus provider extension via PI_HEPHAESTUS_BASE_URL / PI_HEPHAESTUS_API_KEY.
            Map<String, String> env = new HashMap<>();
            LlmProxyAuthShell.build(
                CredentialMode.API_KEY,
                LlmProvider.OPENAI,
                "sk-test",
                "https://gpu.example.com/api",
                "gpt-test",
                env
            );
            assertThat(env)
                .containsEntry("OPENAI_API_KEY", "sk-test")
                .containsEntry("PI_HEPHAESTUS_BASE_URL", "https://gpu.example.com/api")
                .containsEntry("PI_HEPHAESTUS_API_KEY", "sk-test")
                .containsEntry("PI_HEPHAESTUS_MODEL", "gpt-test");
        }

        @Test
        @DisplayName("ANTHROPIC baseUrl override exports PI_HEPHAESTUS_* env vars")
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
                .containsEntry("ANTHROPIC_API_KEY", "sk-test")
                .containsEntry("PI_HEPHAESTUS_BASE_URL", "https://proxy.example.com")
                .containsEntry("PI_HEPHAESTUS_API_KEY", "sk-test")
                .containsEntry("PI_HEPHAESTUS_MODEL", "claude-test");
        }

        @Test
        @DisplayName("blank baseUrl is treated as unset (no PI_HEPHAESTUS_* vars written)")
        void blankBaseUrlSkipped() {
            Map<String, String> env = new HashMap<>();
            LlmProxyAuthShell.build(CredentialMode.API_KEY, LlmProvider.OPENAI, "sk-test", "   ", null, env);
            assertThat(env)
                .doesNotContainKey("PI_HEPHAESTUS_BASE_URL")
                .doesNotContainKey("PI_HEPHAESTUS_API_KEY")
                .doesNotContainKey("PI_HEPHAESTUS_MODEL");
        }

        @Test
        @DisplayName("baseUrl null in API_KEY mode does not write any PI_HEPHAESTUS_* var")
        void nullBaseUrlOmitsHephaestusVars() {
            Map<String, String> env = new HashMap<>();
            LlmProxyAuthShell.build(CredentialMode.API_KEY, LlmProvider.OPENAI, "sk-test", null, null, env);
            assertThat(env)
                .containsEntry("OPENAI_API_KEY", "sk-test")
                .doesNotContainKey("PI_HEPHAESTUS_BASE_URL")
                .doesNotContainKey("PI_HEPHAESTUS_API_KEY");
        }

        @Test
        @DisplayName("PROXY mode never writes PI_HEPHAESTUS_* — proxy URL comes from $LLM_PROXY_URL")
        void proxyModeDoesNotWriteHephaestusVars() {
            Map<String, String> env = new HashMap<>();
            LlmProxyAuthShell.build(
                CredentialMode.PROXY,
                LlmProvider.OPENAI,
                null,
                "https://ignored.example.com",
                "ignored-model",
                env
            );
            assertThat(env)
                .doesNotContainKey("PI_HEPHAESTUS_BASE_URL")
                .doesNotContainKey("PI_HEPHAESTUS_API_KEY")
                .doesNotContainKey("PI_HEPHAESTUS_MODEL");
        }
    }
}
