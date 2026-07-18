package de.tum.cit.aet.hephaestus.integration.slack.connect;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.oauth.state.OAuthStateService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionStrategy;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.slack.connect.SlackOAuthClient.OAuthV2Access;
import de.tum.cit.aet.hephaestus.integration.slack.credentials.SlackCredentialProvider;
import de.tum.cit.aet.hephaestus.integration.slack.retention.SlackWorkspaceContentEraser;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Slack OAuth v2 connection strategy. Token-rotation apps are rejected at finalize because
 * token refresh is not yet implemented.
 *
 * <p>{@link #revoke} is a GDPR hard-erase on disconnect, symmetric with Outline: it uninstalls the bot
 * (best-effort OAuth token revoke while the token is still resolvable) <b>and</b> erases every Slack-owned
 * row for the workspace through {@link SlackWorkspaceContentEraser} — the same choke point workspace-purge
 * drives. Nothing ingested (message content, thread aggregates, per-channel consent registrations, per-person
 * opt-outs, mentor DM threads, derived conversation feedback) outlives the connection, so a later reconnect
 * starts from a clean slate rather than backfilling the disconnected consent gap.
 */
@ConditionalOnServerRole
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = false)
public class SlackConnectionStrategy implements ConnectionStrategy {

    private static final Logger log = LoggerFactory.getLogger(SlackConnectionStrategy.class);

    private static final String AUTHORIZE_URL = "https://slack.com/oauth/v2/authorize";

    // Exact Slack bot scopes used by current code and subscribed events. Channel reading still requires
    // workspace-admin channel activation and the per-member opt-out firewall.
    static final Set<String> DEFAULT_SCOPES = Set.of(
        "chat:write",
        "assistant:write",
        "im:history",
        "channels:history",
        "groups:history",
        "channels:read",
        "channels:join",
        "groups:read",
        "users:read"
    );

    private final OAuthStateService oauthStateService;
    private final SlackOAuthClient oauthClient;
    private final SlackCredentialProvider credentialProvider;
    private final SlackWorkspaceContentEraser workspaceContentEraser;
    private final String clientId;
    private final String scopes;
    private final String redirectUri;

    public SlackConnectionStrategy(
        OAuthStateService oauthStateService,
        SlackOAuthClient oauthClient,
        SlackCredentialProvider credentialProvider,
        SlackWorkspaceContentEraser workspaceContentEraser,
        @Value("${hephaestus.integration.slack.client-id:}") String clientId,
        @Value("${hephaestus.integration.slack.redirect-uri:}") String redirectUri
    ) {
        this.oauthStateService = oauthStateService;
        this.oauthClient = oauthClient;
        this.credentialProvider = credentialProvider;
        this.workspaceContentEraser = workspaceContentEraser;
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
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalStateException("Slack redirect URI must be configured");
        }
        String state = oauthStateService.issue(request.workspaceId(), IntegrationKind.SLACK, request.actorRef());
        StringBuilder url = new StringBuilder(AUTHORIZE_URL)
            .append('?')
            .append("client_id=")
            .append(enc(clientId))
            .append("&scope=")
            .append(enc(scopes))
            .append("&state=")
            .append(enc(state));
        url.append("&redirect_uri=").append(enc(redirectUri));
        return new ConnectInitiation.RedirectToVendor(URI.create(url.toString()), state);
    }

    @Override
    public ConnectFinalization finalizeConnect(IntegrationRef ref, Map<String, String> callbackParams) {
        String code = callbackParams == null ? null : callbackParams.get("code");
        if (code == null || code.isBlank()) {
            return new ConnectFinalization.Failed("missing code");
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            return new ConnectFinalization.Failed("Slack redirect URI must be configured");
        }
        OAuthV2Access r;
        try {
            r = oauthClient.exchangeCode(code, redirectUri());
        } catch (SlackOAuthException e) {
            log.warn("Slack OAuth exchange failed: workspaceId={}, error={}", ref.workspaceId(), e.getMessage());
            return new ConnectFinalization.Failed("slack oauth failed: " + e.getMessage());
        }
        if (r.expiresIn() != null || r.refreshToken() != null) {
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
            /* retentionDays */ null,
            Set.of()
        );
        return new ConnectFinalization.Completed(
            r.team().id(),
            new BearerToken(r.accessToken(), null),
            r.team().name(),
            config
        );
    }

    /**
     * Runs {@link Propagation#NOT_SUPPORTED} — this method holds NO transaction of its own, matching
     * {@code GitLabWebhookService#deregisterActiveWebhook}. The Slack token revoke below is an external
     * HTTP round-trip, and the disconnect path already holds the connection's {@code FOR UPDATE}
     * lifecycle lock on another connection; opening a transaction across the network call would pin a
     * second pooled DB connection idle-in-transaction for its duration. Each collaborator still gets a
     * transaction: the credential lookup and {@code SlackWorkspaceContentEraser#eraseWorkspace} are
     * {@code @Transactional} on their own beans, so the erase remains atomic in itself.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void revoke(IntegrationRef ref) {
        // 1) Best-effort vendor-side uninstall while the token is still resolvable (credentials are cleared by
        //    the caller only after this callback returns). A failed/absent token never blocks the local erase.
        var bundle = credentialProvider.resolve(ref);
        if (bundle.isPresent() && bundle.get() instanceof BearerToken bt) {
            try {
                boolean revoked = oauthClient.revoke(bt.token());
                log.info("Slack revoke for workspace={}: success={}", ref.workspaceId(), revoked);
            } catch (RuntimeException e) {
                log.warn(
                    "Slack revoke call failed for workspace={} (local erase still applied): {}",
                    ref.workspaceId(),
                    e.toString()
                );
            }
        } else {
            log.debug("Slack token revoke skipped: no bearer token for workspace={}", ref.workspaceId());
        }
        // 2) GDPR erase on disconnect (symmetric with Outline): drop every ingested Slack row + per-channel
        //    consent for the workspace so nothing outlives the connection and a later reconnect cannot silently
        //    backfill the disconnected consent gap. Opens its own transaction (see the propagation note
        //    above); if it fails, ConnectionService absorbs it and the local UNINSTALLED transition still lands.
        workspaceContentEraser.eraseWorkspace(ref.workspaceId());
        log.info(
            "slack.audit: revoke erase — cleared ingested Slack content + consent for workspace={}",
            ref.workspaceId()
        );
    }

    private String redirectUri() {
        return redirectUri == null ? "" : redirectUri;
    }

    private static String enc(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
