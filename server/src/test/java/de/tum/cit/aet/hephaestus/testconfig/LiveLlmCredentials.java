package de.tum.cit.aet.hephaestus.testconfig;

import java.util.Map;

/**
 * Tuple of LLM credentials read from the environment. The {@code HEPHAESTUS_LIVE_LLM_API_KEY}
 * variable is the JUnit gate for {@link LiveLlmTest}. Endpoint, key, and model must all be
 * supplied explicitly so the repository never carries environment-specific defaults.
 *
 * @param baseUrl   OpenAI-compatible base URL; Pi SDK appends {@code /v1/chat/completions}
 * @param apiKey    bearer token sent as {@code Authorization: Bearer <key>}
 * @param model     model id passed to the runner (forwarded as {@code defaultModel})
 */
public record LiveLlmCredentials(String baseUrl, String apiKey, String model) {
    /**
     * Load credentials from the environment. Caller must already be JUnit-gated via
     * {@link LiveLlmTest}; we throw rather than skip so a misconfigured override surfaces loudly.
     */
    public static LiveLlmCredentials fromEnv() {
        return from(System.getenv());
    }

    static LiveLlmCredentials from(Map<String, String> environment) {
        String baseUrl = environment.get("HEPHAESTUS_LIVE_LLM_BASE_URL");
        String apiKey = environment.get("HEPHAESTUS_LIVE_LLM_API_KEY");
        String model = environment.get("HEPHAESTUS_LIVE_LLM_MODEL");
        if (isBlank(baseUrl) || isBlank(apiKey) || isBlank(model)) {
            throw new IllegalStateException(
                "Live LLM tests require HEPHAESTUS_LIVE_LLM_BASE_URL, " +
                    "HEPHAESTUS_LIVE_LLM_API_KEY, and HEPHAESTUS_LIVE_LLM_MODEL"
            );
        }
        return new LiveLlmCredentials(baseUrl, apiKey, model);
    }

    /** Env vars the Pi SDK reads natively for {@code openai-completions} provider routing. */
    public Map<String, String> asProcessEnv() {
        return Map.of("OPENAI_API_KEY", apiKey, "OPENAI_BASE_URL", baseUrl);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
