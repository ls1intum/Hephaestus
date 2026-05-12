package de.tum.in.www1.hephaestus.agent.runtime;

import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
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
 * <p>The caller passes a mutable {@code env} map; non-Azure API keys are written into it as a
 * side effect (Azure keys land in the shell prefix instead).
 */
public final class LlmProxyAuthShell {

    private static final String AZURE_API_VERSION = "2025-04-01-preview";

    private LlmProxyAuthShell() {}

    /**
     * @param mode       PROXY (use {@code $LLM_PROXY_*}), API_KEY (use the credential), or OAUTH (treated as API_KEY)
     * @param provider   the LLM provider; controls which env vars are exported
     * @param credential the API key in API_KEY/OAUTH modes; ignored in PROXY mode
     * @param env        mutable env map; non-Azure API keys are written here as a side effect
     * @return the shell prefix ending in {@code " && "} (empty if no exports needed)
     */
    public static String build(
        CredentialMode mode,
        LlmProvider provider,
        @Nullable String credential,
        Map<String, String> env
    ) {
        return switch (mode) {
            case PROXY -> proxyMode(provider);
            case API_KEY, OAUTH -> apiKeyMode(provider, credential, env);
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

    private static String apiKeyMode(LlmProvider provider, @Nullable String credential, Map<String, String> env) {
        if (credential == null) {
            throw new IllegalArgumentException("credential must not be null in API_KEY/OAUTH mode");
        }
        return switch (provider) {
            case AZURE_OPENAI -> "export AZURE_OPENAI_API_KEY=" +
            shellQuote(credential) +
            " AZURE_OPENAI_API_VERSION=\"" +
            AZURE_API_VERSION +
            "\"" +
            " && ";
            case OPENAI -> {
                env.put("OPENAI_API_KEY", credential);
                yield "";
            }
            case ANTHROPIC -> {
                env.put("ANTHROPIC_API_KEY", credential);
                yield "";
            }
        };
    }

    /** Single-quote a value for safe shell interpolation (escapes embedded single quotes). */
    static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
