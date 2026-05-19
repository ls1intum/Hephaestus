package de.tum.cit.aet.hephaestus.testconfig;

import java.util.Map;

/**
 * Tuple of LLM credentials read from the environment. The {@code HEPHAESTUS_LIVE_LLM_API_KEY}
 * variable is the JUnit gate for {@link LiveLlmTest}; this record exposes optional overrides
 * for the endpoint and model so the TUM gateway can be swapped for any OpenAI-compatible
 * provider without touching test code.
 *
 * <p>Defaults match the TUM AET ASE gateway running gpt-oss-120b on a chat-completions API.
 *
 * @param baseUrl   OpenAI-compatible base URL; Pi SDK appends {@code /v1/chat/completions}
 * @param apiKey    bearer token sent as {@code Authorization: Bearer <key>}
 * @param model     model id passed to the runner (forwarded as {@code defaultModel})
 */
public record LiveLlmCredentials(String baseUrl, String apiKey, String model) {
    private static final String DEFAULT_BASE_URL = "https://gpu.ase.cit.tum.de/api";
    private static final String DEFAULT_MODEL = "openai/gpt-oss-120b";

    /**
     * Load credentials from the environment. Caller must already be JUnit-gated via
     * {@link LiveLlmTest}; we throw rather than skip so a misconfigured override surfaces loudly.
     */
    public static LiveLlmCredentials fromEnv() {
        String apiKey = System.getenv("HEPHAESTUS_LIVE_LLM_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "HEPHAESTUS_LIVE_LLM_API_KEY missing. The @LiveLlmTest annotation should have " +
                    "gated this test — did you call fromEnv() outside an annotated test?"
            );
        }
        String baseUrl = orDefault(System.getenv("HEPHAESTUS_LIVE_LLM_BASE_URL"), DEFAULT_BASE_URL);
        String model = orDefault(System.getenv("HEPHAESTUS_LIVE_LLM_MODEL"), DEFAULT_MODEL);
        return new LiveLlmCredentials(baseUrl, apiKey, model);
    }

    /** Env vars the Pi SDK reads natively for {@code openai-completions} provider routing. */
    public Map<String, String> asProcessEnv() {
        return Map.of("OPENAI_API_KEY", apiKey, "OPENAI_BASE_URL", baseUrl);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
