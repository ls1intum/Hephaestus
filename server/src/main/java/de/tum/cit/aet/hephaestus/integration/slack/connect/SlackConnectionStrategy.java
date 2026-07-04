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
 * Slack OAuth v2 connection strategy. Token-rotation apps are rejected at finalize because
 * token refresh is not yet implemented.
 */
@ConditionalOnServerRole
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = false)
public class SlackConnectionStrategy implements ConnectionStrategy {

    private static final Logger log = LoggerFactory.getLogger(SlackConnectionStrategy.class);

    private static final String AUTHORIZE_URL = "https://slack.com/oauth/v2/authorize";

    // Locked, least-privilege OAuth bot-scope set for the live Slack subsystem (DM mentor + assistant + App
    // Home). Each scope maps to a Slack API method or event the SHIPPED code actually exercises; rotating apps
    // are rejected at finalize until the token-rotation refresher ships.
    //
    // NOTE — deliberately EXCLUDED: channels:history / groups:history. Those authorize delivery of public/private
    // channel `message` events, which only the channel-ingestion subsystem consumes
    // (SlackIngestService.ingestChannelMessage). That subsystem's capability flag
    // (hephaestus.integration.slack.conversation-ingest.enabled) is available by default, but these OAuth scopes are
    // a separate, still-excluded gate: without them Slack delivers no channel `message` events, so no channel
    // traffic reaches the bot regardless of the flag. Requesting them is a deliberate, consent-designed rollout
    // step (paired with the per-channel activation UX) — so they stay off until that scope request ships.
    static final Set<String> DEFAULT_SCOPES = Set.of(
        // chat.postMessage + chat.startStream/appendStream/stopStream: canned replies and the streamed mentor DM
        // reply (SlackMessageService).
        "chat:write",
        // Post to an admin-configured notification channel / leaderboard-digest channel the bot may not have
        // joined (SlackConnectionAdminController, SlackLeaderboardDigestPublisher).
        "chat:write.public",
        // assistant.threads.setStatus + setSuggestedPrompts: the assistant "Thinking…" status and the
        // suggested-prompt seed on assistant_thread_started (SlackMessageService / SlackStreamingMentorChannel).
        "assistant:write",
        // Delivery of `message` events with channel_type=im — the DM that drives a mentor turn
        // (SlackEventDispatcher). Without it the mentor DM entry point receives nothing.
        "im:history",
        // team/workspace metadata resolution.
        "team:read",
        // users.list + Slack user → workspace member identity resolution (SlackMentorIdentityResolver).
        "users:read",
        // email-keyed member matching when linking a Slack user to a Hephaestus member.
        "users:read.email"
    );

    private final OAuthStateService oauthStateService;
    private final SlackOAuthClient oauthClient;
    private final SlackCredentialProvider credentialProvider;
    private final String clientId;
    private final String scopes;
    private final String redirectUri;

    public SlackConnectionStrategy(
        OAuthStateService oauthStateService,
        SlackOAuthClient oauthClient,
        SlackCredentialProvider credentialProvider,
        @Value("${hephaestus.integration.slack.client-id:}") String clientId,
        @Value("${hephaestus.integration.slack.redirect-uri:}") String redirectUri
    ) {
        this.oauthStateService = oauthStateService;
        this.oauthClient = oauthClient;
        this.credentialProvider = credentialProvider;
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
        String state = oauthStateService.issue(request.workspaceId(), IntegrationKind.SLACK, request.actorRef());
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

    @Override
    public void revoke(IntegrationRef ref) {
        // Best-effort: invalidate the bot token on Slack's side so a disconnect doesn't
        // leave a dangling credential. Failure here MUST NOT block the local UNINSTALLED
        // transition — the caller has already cleared credentials_encrypted, so the
        // local state is consistent regardless of Slack's reachability. Network failures
        // (502/timeout), 401 (already-revoked), 5xx — all swallowed with a single
        // warn log so the disconnect flow stays idempotent.
        var bundle = credentialProvider.resolve(ref);
        if (bundle.isEmpty() || !(bundle.get() instanceof BearerToken bt)) {
            log.debug("Slack revoke skipped: no bearer token for workspace={}", ref.workspaceId());
            return;
        }
        try {
            boolean revoked = oauthClient.revoke(bt.token());
            log.info("Slack revoke for workspace={}: success={}", ref.workspaceId(), revoked);
        } catch (RuntimeException e) {
            log.warn(
                "Slack revoke call failed for workspace={} (local UNINSTALLED still applied): {}",
                ref.workspaceId(),
                e.toString()
            );
        }
    }

    private String redirectUri() {
        return redirectUri == null ? "" : redirectUri;
    }

    private static String enc(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
