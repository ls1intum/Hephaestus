package de.tum.in.www1.hephaestus.agent.proxy;

import de.tum.in.www1.hephaestus.agent.LlmProvider;

/**
 * Provider-specific proxy configuration: auth header format and upstream base URL.
 *
 * <p>Keeps proxy-specific concerns out of the {@link LlmProvider} enum (SRP).
 *
 * @param authHeaderName  the HTTP header carrying the API key ({@code x-api-key} or {@code Authorization})
 * @param useBearerPrefix whether the auth value needs a {@code Bearer } prefix
 * @param upstreamBaseUrl the upstream provider base URL (no trailing slash)
 */
record ProviderProxyConfig(String authHeaderName, boolean useBearerPrefix, String upstreamBaseUrl) {
    static ProviderProxyConfig forProvider(LlmProvider provider, LlmProxyProperties properties) {
        return switch (provider) {
            case ANTHROPIC -> new ProviderProxyConfig("x-api-key", false, properties.anthropicUpstreamUrl());
            case OPENAI -> new ProviderProxyConfig(
                properties.openaiAuthHeader(),
                properties.openaiUseBearerPrefix(),
                properties.openaiUpstreamUrl()
            );
        };
    }

    /** Format the API key into the correct header value. */
    String formatAuthValue(String apiKey) {
        return useBearerPrefix ? "Bearer " + apiKey : apiKey;
    }
}
