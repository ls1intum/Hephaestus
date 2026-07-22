package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import java.net.URI;
import java.net.URISyntaxException;
import org.jspecify.annotations.Nullable;

/**
 * Audit snapshot of a workspace's "bring your own" LLM connection (#1368) — "who rotated the key",
 * "who repointed the endpoint". Same redaction discipline as {@code agent.config.AgentConfigSnapshot}:
 *
 * <ul>
 *   <li>{@code WorkspaceLlmConnection.apiKey} is encrypted at rest, but its getter returns plaintext, so
 *       snapshotting the entity would write the key in the clear into a table that cannot be edited
 *       afterwards. {@link #llmApiKeySet} records only whether a key is present.</li>
 *   <li>{@code baseUrl} is free text and a gateway URL may legitimately carry userinfo or an API key in
 *       the query string; {@link #of} strips both (and any fragment) before the value is snapshotted.</li>
 * </ul>
 *
 * <p>Enforced by {@code ConfigAuditSnapshotArchTest}.
 */
record WorkspaceLlmConnectionSnapshot(
    String slug,
    String displayName,
    @Nullable String baseUrl,
    String apiProtocol,
    LlmAuthMode authMode,
    boolean llmApiKeySet,
    boolean enabled
) implements ConfigAuditSnapshot {
    static WorkspaceLlmConnectionSnapshot of(WorkspaceLlmConnection c) {
        return new WorkspaceLlmConnectionSnapshot(
            c.getSlug(),
            c.getDisplayName(),
            credentialFreeBaseUrl(c.getBaseUrl()),
            c.getApiProtocol(),
            c.getAuthMode(),
            c.getApiKey() != null && !c.getApiKey().isBlank(),
            c.isEnabled()
        );
    }

    /**
     * Reduces a base URL to scheme, host, port and path — same treatment as
     * {@code AgentConfigSnapshot#credentialFreeBaseUrl}. Unparseable input collapses to a marker rather
     * than being passed through, so a malformed value cannot smuggle a credential past this.
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
