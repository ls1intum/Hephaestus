package de.tum.cit.aet.hephaestus.agent.runtime;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Build the shell {@code export ... && } prefix that prepares LLM credentials inside the Pi
 * sandbox container. State-free; reused verbatim by every Pi-based agent.
 *
 * <p>Azure keys go through {@code export} (the sandbox security policy strips {@code AZURE_*}
 * env vars). Non-Azure keys land in the {@code env} map the caller passes in.
 *
 * <p>For OpenAI / Anthropic with a non-blank {@code baseUrl} override, the runner registers a
 * custom Pi provider named {@code hephaestus} on the ModelRegistry — Pi does not honour
 * {@code OPENAI_BASE_URL} / {@code ANTHROPIC_BASE_URL} natively. This class writes the
 * {@code PI_HEPHAESTUS_BASE_URL} / {@code _API_KEY} / {@code _MODEL} env vars the runner reads.
 * {@code OPENAI_API_KEY} / {@code ANTHROPIC_API_KEY} must NOT be set on this path or Pi's
 * built-in provider auto-activates against api.openai.com / api.anthropic.com.
 */
public final class LlmProxyAuthShell {

    private static final String AZURE_API_VERSION = "2025-04-01-preview";

    private LlmProxyAuthShell() {}

    /**
     * Variant that writes the {@code PI_HEPHAESTUS_*} env vars for the custom provider
     * extension when {@code baseUrl} is non-blank in API_KEY/OAUTH mode for {@code OPENAI} /
     * {@code ANTHROPIC}. Azure does not get this treatment: its deployment routing is
     * already handled via {@code AZURE_OPENAI_DEPLOYMENT_NAME_MAP}, and Pi's
     * {@code azure-openai-responses} provider mints the effective URL from the env-var-driven
     * base.
     *
     * <p>PROXY mode ignores {@code baseUrl} / {@code modelName} — its base URL is resolved from
     * the sandbox-injected {@code $LLM_PROXY_URL}.
     */
    public static String build(
        CredentialMode mode,
        LlmProvider provider,
        @Nullable String credential,
        @Nullable String baseUrl,
        @Nullable String modelName,
        boolean openaiUseChatCompletions,
        Map<String, String> env
    ) {
        return switch (mode) {
            case PROXY -> proxyMode(provider, modelName, openaiUseChatCompletions, env);
            case API_KEY, OAUTH -> apiKeyMode(provider, credential, baseUrl, modelName, env);
        };
    }

    /**
     * Every provider here talks to the in-app LLM proxy ({@code $LLM_PROXY_URL}); the proxy injects the
     * real key ({@code $LLM_PROXY_TOKEN} → the stored key) and forwards to the operator-configured
     * upstream. The container needs no internet and never sees the key.
     *
     * <p>For OPENAI, {@code openaiUseChatCompletions=true} routes through Pi's native
     * {@code openai-completions} provider (the universal {@code /chat/completions} format — OpenAI, vLLM,
     * Open WebUI) via the {@code PI_HEPHAESTUS_*} vars the runner registers; {@code false} uses Pi's
     * built-in provider (the Responses API), for upstreams that implement {@code /responses}.
     */
    private static String proxyMode(
        LlmProvider provider,
        @Nullable String modelName,
        boolean openaiUseChatCompletions,
        Map<String, String> env
    ) {
        return switch (provider) {
            case AZURE_OPENAI ->
                // Pi appends /responses to AZURE_OPENAI_BASE_URL — must end at /openai (not /openai/v1)
                // for the 2025-04-01-preview api-version.
                "export AZURE_OPENAI_BASE_URL=\"$LLM_PROXY_URL/openai\"" +
                " AZURE_OPENAI_API_KEY=\"$LLM_PROXY_TOKEN\"" +
                " AZURE_OPENAI_API_VERSION=\"" +
                AZURE_API_VERSION +
                "\"" +
                " && ";
            case OPENAI -> {
                if (openaiUseChatCompletions) {
                    // Native openai-completions provider over the proxy. PI_HEPHAESTUS_API_KEY carries the
                    // per-job proxy token (NOT the real key); the proxy swaps it for the stored key.
                    if (modelName != null && !modelName.isBlank()) {
                        env.put("PI_HEPHAESTUS_MODEL", modelName);
                    }
                    yield "export PI_HEPHAESTUS_BASE_URL=\"$LLM_PROXY_URL\"" +
                    " PI_HEPHAESTUS_API_KEY=\"$LLM_PROXY_TOKEN\"" +
                    " && ";
                }
                yield "export OPENAI_BASE_URL=\"$LLM_PROXY_URL\"" + " OPENAI_API_KEY=\"$LLM_PROXY_TOKEN\"" + " && ";
            }
            case ANTHROPIC -> "export ANTHROPIC_BASE_URL=\"$LLM_PROXY_URL\"" +
            " ANTHROPIC_API_KEY=\"$LLM_PROXY_TOKEN\"" +
            " && ";
        };
    }

    private static String apiKeyMode(
        LlmProvider provider,
        @Nullable String credential,
        @Nullable String baseUrl,
        @Nullable String modelName,
        Map<String, String> env
    ) {
        if (credential == null) {
            throw new IllegalArgumentException("credential must not be null in API_KEY/OAUTH mode");
        }
        boolean hasBaseUrl = baseUrl != null && !baseUrl.isBlank();
        return switch (provider) {
            case AZURE_OPENAI -> "export AZURE_OPENAI_API_KEY=" +
            shellQuote(credential) +
            " AZURE_OPENAI_API_VERSION=\"" +
            AZURE_API_VERSION +
            "\"" +
            " && ";
            case OPENAI -> {
                if (hasBaseUrl) {
                    // ONLY export the hephaestus-extension env. Setting OPENAI_API_KEY alongside
                    // would auto-activate Pi's built-in OpenAI provider against api.openai.com,
                    // which wins resolution and silently bypasses the custom gateway. Confirmed
                    // empirically: with both keys present, Pi sent the TUM gateway key to OpenAI
                    // and got a 401. The hephaestus provider extension reads its own creds.
                    env.put("PI_HEPHAESTUS_BASE_URL", baseUrl);
                    env.put("PI_HEPHAESTUS_API_KEY", credential);
                    if (modelName != null && !modelName.isBlank()) {
                        // Pass the model id verbatim. Pi's resolver finds it by exact string
                        // match against the extension's models[].id, and downstream gateways
                        // (e.g. TUM GPU) expect the full `openai/<model>` form on the wire —
                        // stripping the prefix breaks the upstream request.
                        env.put("PI_HEPHAESTUS_MODEL", modelName);
                    }
                } else {
                    env.put("OPENAI_API_KEY", credential);
                }
                yield "";
            }
            case ANTHROPIC -> {
                if (hasBaseUrl) {
                    env.put("PI_HEPHAESTUS_BASE_URL", baseUrl);
                    env.put("PI_HEPHAESTUS_API_KEY", credential);
                    if (modelName != null && !modelName.isBlank()) {
                        env.put("PI_HEPHAESTUS_MODEL", modelName);
                    }
                } else {
                    env.put("ANTHROPIC_API_KEY", credential);
                }
                yield "";
            }
        };
    }

    /** Single-quote a value for safe shell interpolation (escapes embedded single quotes). */
    static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
