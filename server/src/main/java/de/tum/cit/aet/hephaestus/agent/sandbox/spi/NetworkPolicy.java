package de.tum.cit.aet.hephaestus.agent.sandbox.spi;

import java.net.URI;
import java.util.Set;

/**
 * Network access policy for a sandboxed container.
 *
 * <p>When {@code internetAccess} is {@code false}, the container runs on a Docker
 * {@code --internal} network with zero external connectivity. The only reachable
 * endpoints are the LLM proxy and the optional git proxy, both accessed via app-server
 * multi-homing on the job network.
 *
 * @param internetAccess       whether the container may reach the public internet
 * @param llmProxyUrl          full URL to the LLM proxy endpoint (injected as env var);
 *                             null means LLM access disabled
 * @param llmProxyToken        job-scoped authentication token for the LLM proxy
 * @param llmProxyProviderPath provider path segment appended to the LLM proxy base URL
 *                             (e.g., "anthropic", "openai")
 * @param gitProxyUrl          full URL to the git proxy endpoint (when present, the
 *                             container's git client routes through this URL with a
 *                             scoped credential per the Anthropic Claude Code git-proxy
 *                             pattern); null means containers use their own git credentials
 *
 * <p>Validation: when {@code llmProxyUrl} or {@code gitProxyUrl} is non-null, it MUST be
 * an absolute http(s) URL. Non-absolute or non-http(s) URLs throw
 * {@code IllegalArgumentException} at construction time. Validation lives in the compact
 * constructor so SPI-build-time errors fail loud (vs. Hibernate Validator's {@code @URL}
 * which only fires at the controller boundary).
 *
 * <p>The four-argument constructor delegates to the canonical five-arg form with a null
 * {@code gitProxyUrl} for backward compatibility with the ~48 existing callsites. New
 * code prefers the five-arg form so the gitProxyUrl is explicit.
 *
 * <p>{@code llmProxyUrl} is kept as a {@code String} (not {@code java.net.URI}) for this
 * epic; migrating to typed URI is a follow-up sweep (~48 callsites). The validation in
 * the compact constructor catches the same bad inputs either way.
 */
public record NetworkPolicy(
    boolean internetAccess,
    String llmProxyUrl,
    String llmProxyToken,
    String llmProxyProviderPath,
    String gitProxyUrl
) {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    /** Backward-compat constructor: callsites that don't set gitProxyUrl. */
    public NetworkPolicy(boolean internetAccess, String llmProxyUrl, String llmProxyToken, String llmProxyProviderPath) {
        this(internetAccess, llmProxyUrl, llmProxyToken, llmProxyProviderPath, null);
    }

    public NetworkPolicy {
        if (llmProxyUrl != null && !llmProxyUrl.isBlank()) {
            requireAbsoluteHttp(llmProxyUrl, "llmProxyUrl");
        }
        if (gitProxyUrl != null && !gitProxyUrl.isBlank()) {
            requireAbsoluteHttp(gitProxyUrl, "gitProxyUrl");
        }
    }

    private static void requireAbsoluteHttp(String url, String fieldName) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(fieldName + " is not a valid URI: " + url, ex);
        }
        if (!uri.isAbsolute()) {
            throw new IllegalArgumentException(fieldName + " must be absolute: " + url);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new IllegalArgumentException(
                fieldName + " must use http or https scheme: " + url
            );
        }
    }
}
