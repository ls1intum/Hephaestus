package de.tum.cit.aet.hephaestus.agent.sandbox.spi;

import java.net.URI;
import java.util.Set;

/**
 * Network access policy for a sandboxed container.
 *
 * <p>When {@code internetAccess} is {@code false}, the container runs on a Docker
 * {@code --internal} network with zero external connectivity. The only reachable endpoint
 * is the LLM proxy at {@code llmProxyUrl}, accessed via app-server multi-homing on the job
 * network.
 *
 * <p>{@code llmProxyUrl} is validated at construction: when non-blank it must be an
 * absolute {@code http(s)} URL. Validation lives in the compact constructor so SPI-build-
 * time errors fail loud, including when bound from YAML via {@code @ConfigurationProperties}.
 */
public record NetworkPolicy(
    boolean internetAccess,
    String llmProxyUrl,
    String llmProxyToken,
    String llmProxyProviderPath
) {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    public NetworkPolicy {
        if (llmProxyUrl != null && !llmProxyUrl.isBlank()) {
            requireAbsoluteHttp(llmProxyUrl);
        }
    }

    private static void requireAbsoluteHttp(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("llmProxyUrl is not a valid URI: " + url, ex);
        }
        if (!uri.isAbsolute()) {
            throw new IllegalArgumentException("llmProxyUrl must be absolute: " + url);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new IllegalArgumentException("llmProxyUrl must use http or https: " + url);
        }
    }
}
