package de.tum.cit.aet.hephaestus.integration.slack.connect;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.oauth.state.OAuthStateService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionStrategy;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.slack.connect.SlackOAuthClient.OAuthV2Access;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
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
 * <p>{@link #finalizeConnect} POSTs the authorisation {@code code} back to Slack via
 * {@link SlackOAuthClient#exchangeCode}, then hands the resulting bot token + team
 * metadata back to {@code OAuthCallbackService} as a {@link ConnectFinalization.Completed}.
 * Token-rotation apps (Slack returns {@code expires_in} + {@code refresh_token}) are
 * rejected — rotation lands later (see #1198). The {@code SlackConfig} is initialised
 * with the {@code teamId} / {@code teamName} only; the workspace admin sets the
 * notification channel via the dedicated PATCH endpoint after install completes.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = false)
public class SlackConnectionStrategy implements ConnectionStrategy {

    private static final Logger log = LoggerFactory.getLogger(SlackConnectionStrategy.class);

    private static final String AUTHORIZE_URL = "https://slack.com/oauth/v2/authorize";

    /**
     * Locked scope set for Slack installs. Drops {@code app_mentions:read} and
     * {@code channels:history} (the leaderboard task only posts; it does not read history)
     * and adds {@code users:read.email} so reviewer Slack handles can be resolved by email.
     */
    static final Set<String> DEFAULT_SCOPES = Set.of(
        "chat:write",
        "chat:write.public",
        "team:read",
        "users:read",
        "users:read.email"
    );

    private final OAuthStateService oauthStateService;
    private final SlackOAuthClient oauthClient;
    private final String clientId;
    private final String scopes;
    private final String redirectUri;

    public SlackConnectionStrategy(
        OAuthStateService oauthStateService,
        SlackOAuthClient oauthClient,
        @Value("${hephaestus.integration.slack.client-id:}") String clientId,
        @Value("${hephaestus.integration.slack.redirect-uri:}") String redirectUri
    ) {
        this.oauthStateService = oauthStateService;
        this.oauthClient = oauthClient;
        this.clientId = clientId;
        this.scopes = String.join(",", DEFAULT_SCOPES);
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
        String code = callbackParams == null ? null : callbackParams.get("code");
        if (code == null || code.isBlank()) {
            return new ConnectFinalization.Failed("missing code");
        }
        OAuthV2Access r;
        try {
            r = oauthClient.exchangeCode(code, redirectUri());
        } catch (SlackOAuthException e) {
            log.warn("Slack OAuth exchange failed: workspaceId={}, error={}", ref.workspaceId(), e.getMessage());
            return new ConnectFinalization.Failed("slack oauth failed: " + e.getMessage());
        }
        if (r.expiresIn() != null || r.refreshToken() != null) {
            // Token rotation requires a separate refresh path. Reject loudly rather than
            // dropping the rotation fields on the floor and letting the bot token expire silently.
            return new ConnectFinalization.Failed("Token rotation not yet supported");
        }
        if (r.team() == null || r.team().id() == null) {
            return new ConnectFinalization.Failed("oauth response missing team");
        }
        if (r.accessToken() == null || r.accessToken().isBlank()) {
            return new ConnectFinalization.Failed("oauth response missing access_token");
        }
        ConnectionConfig.SlackConfig config = new ConnectionConfig.SlackConfig(
            r.team().id(),
            r.team().name(),
            /* notificationChannelId */ null,
            /* teamLabel */ null,
            Set.of()
        );
        return new ConnectFinalization.Completed(
            r.team().id(),
            new BearerToken(r.accessToken(), null),
            r.team().name(),
            config
        );
    }

    @Override
    public void revoke(IntegrationRef ref) {
        // Slack auth.revoke is best-effort and ships with token-rotation support (#1198).
        log.debug("Slack revoke stub for workspace={}", ref.workspaceId());
    }

    private String redirectUri() {
        return redirectUri == null ? "" : redirectUri;
    }

    private static String enc(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
