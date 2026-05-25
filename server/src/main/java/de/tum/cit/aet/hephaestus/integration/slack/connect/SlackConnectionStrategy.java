package de.tum.cit.aet.hephaestus.integration.slack.connect;

import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.CredentialBundle;
import de.tum.cit.aet.hephaestus.integration.spi.ConnectionStrategy;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.oauth.state.OAuthStateService;
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
 * lands with the {@code SlackOAuthClient} bean in a follow-up. For #1198 we surface
 * a clear {@link ConnectFinalization.Failed} so end-to-end tests can wire through
 * the redirect path without claiming success on a half-baked exchange.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = true)
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
        StringBuilder url = new StringBuilder(AUTHORIZE_URL).append('?')
            .append("client_id=").append(enc(clientId))
            .append("&scope=").append(enc(scopes))
            .append("&state=").append(enc(state));
        if (redirectUri != null && !redirectUri.isBlank()) {
            url.append("&redirect_uri=").append(enc(redirectUri));
        }
        return new ConnectInitiation.RedirectToVendor(URI.create(url.toString()), state);
    }

    @Override
    public ConnectFinalization finalizeConnect(IntegrationRef ref, Map<String, String> callbackParams) {
        // TODO(#1198 next slice): wire a SlackOAuthClient that POSTs to
        // https://slack.com/api/oauth.v2.access with client_id + client_secret + code,
        // then returns ConnectFinalization.Completed(team_id, BearerToken(bot_token), team_name).
        log.warn("Slack finalizeConnect called but OAuth code exchange not yet wired (workspace={}, params keys={})",
            ref.workspaceId(), callbackParams == null ? null : callbackParams.keySet());
        return new ConnectFinalization.Failed("OAuth code exchange not yet wired");
    }

    @Override
    public ValidationResult validate(IntegrationRef ref, CredentialBundle credentials) {
        // Honest: auth.test probe ships with the Slack OAuth client (#1204).
        return new ValidationResult.Failed("Slack auth.test probe not wired");
    }

    @Override
    public void revoke(IntegrationRef ref) {
        // TODO(#1198 next slice): call https://slack.com/api/auth.revoke best-effort.
        log.debug("Slack revoke stub for workspace={}", ref.workspaceId());
    }

    private static String enc(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
