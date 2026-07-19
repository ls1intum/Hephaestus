package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import java.net.URI;
import java.net.URISyntaxException;
import org.jspecify.annotations.Nullable;

/**
 * Audit snapshot of an agent config — "who swapped the model", "who rotated the key".
 *
 * <p><b>Carries no credential material.</b> Two ways in, both closed:
 *
 * <ul>
 *   <li>{@code AgentConfig.llmApiKey} is encrypted at rest, but its getter returns plaintext, so
 *       snapshotting the entity would write the key in the clear into a table that cannot be edited
 *       afterwards. {@link #llmApiKeySet} records only whether a key is present — which is what an
 *       auditor actually asks ("was a credential added or removed here?").</li>
 *   <li>{@code llmBaseUrl} is free text and a gateway URL may legitimately carry userinfo
 *       ({@code https://svc:pw@host/v1}), which would be a cleartext credential in the same
 *       unerasable column — and one no name-based guard would spot, since "llmBaseUrl" reads
 *       innocuous. {@link #of} strips it (RFC 3986 §3.2.1 deprecates userinfo precisely because it
 *       leaks into logs and stores).</li>
 * </ul>
 *
 * <p>Enforced by {@code ConfigAuditSnapshotArchTest} and {@code AgentConfigSnapshotTest}.
 */
record AgentConfigSnapshot(
    String name,
    boolean enabled,
    @Nullable LlmProvider llmProvider,
    @Nullable String modelName,
    @Nullable String modelVersion,
    @Nullable String llmBaseUrl,
    @Nullable CredentialMode credentialMode,
    boolean llmApiKeySet,
    int timeoutSeconds,
    int maxConcurrentJobs,
    boolean allowInternet
) implements ConfigAuditSnapshot {
    static AgentConfigSnapshot of(AgentConfig c) {
        return new AgentConfigSnapshot(
            c.getName(),
            c.isEnabled(),
            c.getLlmProvider(),
            c.getModelName(),
            c.getModelVersion(),
            credentialFreeBaseUrl(c.getLlmBaseUrl()),
            c.getCredentialMode(),
            c.getLlmApiKey() != null && !c.getLlmApiKey().isBlank(),
            c.getTimeoutSeconds(),
            c.getMaxConcurrentJobs(),
            c.isAllowInternet()
        );
    }

    /**
     * Reduces a base URL to scheme, host, port and path. Both places a credential rides in a URL are
     * dropped: {@code user:password@}, and the query string — which is where the major LLM gateways put
     * their key ({@code ?key=}, {@code ?subscription-key=}). Unparseable input collapses to a marker
     * rather than being passed through, so a malformed value cannot smuggle one past either.
     */
    private static @Nullable String credentialFreeBaseUrl(@Nullable String url) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = new URI(url);
            if (uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null) {
                return url;
            }
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null).toString();
        } catch (URISyntaxException e) {
            return "<unparseable>";
        }
    }
}
