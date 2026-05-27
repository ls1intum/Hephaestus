package de.tum.cit.aet.hephaestus.integration.slack.connect;

import de.tum.cit.aet.hephaestus.integration.core.oauth.state.OAuthStateService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionStrategy;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Slack OAuth connection strategy.
 *
 * <p>{@link #initiate} builds the Slack v2 OAuth authorize URL with a state token
 * minted by {@link OAuthStateService} (HMAC-signed, single-use, CSRF-safe).
 *
 * <p>{@link #finalizeConnect} is a stub — the {@code oauth.v2.access} HTTP call
 * lands with a future {@code SlackOAuthClient} bean. Returns a clear
 * {@link ConnectFinalization.Failed} so callers don't claim success on a stub.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = false)
public class SlackConnectionStrategy implements ConnectionStrategy {

    private static final Logger log = LoggerFactory.getLogger(SlackConnectionStrategy.class);

    private static final String AUTHORIZE_URL = "https://slack.com/oauth/v2/authorize";
    /** Conservative default bot scopes for read-only ingest + reply. */
    private static final String DEFAULT_SCOPES = "app_mentions:read,channels:history,chat:write,team:read";

    private final OAuthStateService oauthStateService;
    private final String clientId;
    private final String scopes;
    private final String redirectUri;

    public SlackConnectionStrategy(
        OAuthStateService oauthStateService,
        @Value("${hephaestus.slack.client-id:}") String clientId,
        @Value("${hephaestus.slack.scopes:" + DEFAULT_SCOPES + "}") String scopes,
        @Value("${hephaestus.slack.redirect-uri:}") String redirectUri
    ) {
        this.oauthStateService = oauthStateService;
        this.clientId = clientId;
        this.scopes = scopes;
        this.redirectUri = redirectUri;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.SLACK;
    }

    @Override
    public ConnectInitiation initiate(InitiateRequest request) {
        String state = oauthStateService.issue(request.workspaceId(), IntegrationKind.SLACK);
        StringBuilder url = new StringBuilder(AUTHORIZE_URL)
            .append('?')
            .append("client_id=")
            .append(enc(clientId))
            .append("&scope=")
            .append(enc(scopes))
            .append("&state=")
            .append(enc(state));
        if (redirectUri != null && !redirectUri.isBlank()) {
            url.append("&redirect_uri=").append(enc(redirectUri));
        }
        return new ConnectInitiation.RedirectToVendor(URI.create(url.toString()), state);
    }

    @Override
    public ConnectFinalization finalizeConnect(IntegrationRef ref, Map<String, String> callbackParams) {
        // Stub: real implementation ships with the Slack OAuth client (#1204).
        log.warn(
            "Slack finalizeConnect called but OAuth code exchange not yet wired (workspace={}, params keys={})",
            ref.workspaceId(),
            callbackParams == null ? null : callbackParams.keySet()
        );
        return new ConnectFinalization.Failed("OAuth code exchange not yet wired");
    }

    @Override
    public void revoke(IntegrationRef ref) {
        // Slack auth.revoke is best-effort and ships with the Slack OAuth client (#1204).
        log.debug("Slack revoke stub for workspace={}", ref.workspaceId());
    }

    private static String enc(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
