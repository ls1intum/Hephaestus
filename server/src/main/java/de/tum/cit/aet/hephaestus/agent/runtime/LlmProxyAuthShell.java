package de.tum.cit.aet.hephaestus.agent.runtime;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import java.util.Map;
import org.springframework.lang.Nullable;

/**
 * Build the shell {@code export ... && } prefix that prepares LLM credentials inside the Pi
 * sandbox container. State-free; reused verbatim by every Pi-based agent.
 *
 * <p>{@code PROXY} mode forwards the sandbox-injected {@code $LLM_PROXY_URL} / {@code $LLM_PROXY_TOKEN}
 * to the provider env vars. {@code API_KEY} / {@code OAUTH} go through {@code export} for Azure
 * (the sandbox security policy blocks {@code AZURE_*} env vars to prevent accidental leakage) and
 * through the env map for the other providers.
 *
 * <p>For {@code OPENAI} / {@code ANTHROPIC} in API_KEY/OAUTH mode with a non-blank {@code baseUrl}
 * override, the routing is achieved by emitting a custom Pi provider named {@code hephaestus}
 * (see {@link PiRuntimeFactory#buildExtensionFile}). Pi does NOT read {@code OPENAI_BASE_URL} or
 * {@code ANTHROPIC_BASE_URL} natively, so a bare env-var export silently fails — we must register
 * a provider via {@code pi.registerProvider("hephaestus", ...)} and point {@code defaultProvider}
 * at it. The required env vars for that provider are {@code PI_HEPHAESTUS_BASE_URL},
 * {@code PI_HEPHAESTUS_API_KEY}, and {@code PI_HEPHAESTUS_MODEL}; this class writes them when
 * {@code baseUrl} is non-blank. {@code OPENAI_API_KEY} / {@code ANTHROPIC_API_KEY} are also set
 * for backwards compatibility with code paths that don't traverse the extension (Pi's built-in
 * provider sometimes reads them as a fallback).
 *
 * <p>The caller passes a mutable {@code env} map; non-Azure API keys are written into it as a
 * side effect (Azure keys land in the shell prefix instead).
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
        Map<String, String> env
    ) {
        return switch (mode) {
            case PROXY -> proxyMode(provider);
            case API_KEY, OAUTH -> apiKeyMode(provider, credential, baseUrl, modelName, env);
        };
    }

    private static String proxyMode(LlmProvider provider) {
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
            case OPENAI -> "export OPENAI_BASE_URL=\"$LLM_PROXY_URL\"" +
            " OPENAI_API_KEY=\"$LLM_PROXY_TOKEN\"" +
            " && ";
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
                env.put("OPENAI_API_KEY", credential);
                if (hasBaseUrl) {
                    env.put("PI_HEPHAESTUS_BASE_URL", baseUrl);
                    env.put("PI_HEPHAESTUS_API_KEY", credential);
                    if (modelName != null && !modelName.isBlank()) {
                        env.put("PI_HEPHAESTUS_MODEL", modelName);
                    }
                }
                yield "";
            }
            case ANTHROPIC -> {
                env.put("ANTHROPIC_API_KEY", credential);
                if (hasBaseUrl) {
                    env.put("PI_HEPHAESTUS_BASE_URL", baseUrl);
                    env.put("PI_HEPHAESTUS_API_KEY", credential);
                    if (modelName != null && !modelName.isBlank()) {
                        env.put("PI_HEPHAESTUS_MODEL", modelName);
                    }
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
