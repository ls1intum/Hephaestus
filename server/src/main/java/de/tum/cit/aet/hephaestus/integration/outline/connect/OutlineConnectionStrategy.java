package de.tum.cit.aet.hephaestus.integration.outline.connect;

import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.CredentialBundle;
import de.tum.cit.aet.hephaestus.integration.spi.ConnectionStrategy;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.spi.OAuthStateService;
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
 * Outline OAuth connection strategy.
 *
 * <p>Server URL is configurable to support self-hosted Outline installs alongside
 * the SaaS {@code app.getoutline.com}. The actual token exchange is TODO — same
 * pattern as Slack: stub now, real HTTP client lands with #1203.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = true)
public class OutlineConnectionStrategy implements ConnectionStrategy {

    private static final Logger log = LoggerFactory.getLogger(OutlineConnectionStrategy.class);

    /** Conservative default: read documents + manage webhook subscriptions. */
    private static final String DEFAULT_SCOPES = "read write";

    private final OAuthStateService oauthStateService;
    private final String serverUrl;
    private final String clientId;
    private final String scopes;
    private final String redirectUri;

    public OutlineConnectionStrategy(
        OAuthStateService oauthStateService,
        @Value("${hephaestus.outline.server-url:https://app.getoutline.com}") String serverUrl,
        @Value("${hephaestus.outline.client-id:}") String clientId,
        @Value("${hephaestus.outline.scopes:" + DEFAULT_SCOPES + "}") String scopes,
        @Value("${hephaestus.outline.redirect-uri:}") String redirectUri
    ) {
        this.oauthStateService = oauthStateService;
        this.serverUrl = stripTrailingSlash(serverUrl);
        this.clientId = clientId;
        this.scopes = scopes;
        this.redirectUri = redirectUri;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.OUTLINE;
    }

    @Override
    public ConnectInitiation initiate(InitiateRequest request) {
        String state = oauthStateService.issue(request.workspaceId(), IntegrationKind.OUTLINE);
        StringBuilder url = new StringBuilder(serverUrl).append("/oauth/authorize?")
            .append("client_id=").append(enc(clientId))
            .append("&response_type=code")
            .append("&scope=").append(enc(scopes))
            .append("&state=").append(enc(state));
        if (redirectUri != null && !redirectUri.isBlank()) {
            url.append("&redirect_uri=").append(enc(redirectUri));
        }
        return new ConnectInitiation.RedirectToVendor(URI.create(url.toString()), state);
    }

    @Override
    public ConnectFinalization finalizeConnect(IntegrationRef ref, Map<String, String> callbackParams) {
        // TODO(#1203): POST to <serverUrl>/oauth.access with grant_type=authorization_code,
        // exchange code for { access_token, refresh_token, expires_in, team.id, team.name },
        // return ConnectFinalization.Completed(team.id, OAuthSession(access, refresh, expires), team.name).
        log.warn("Outline finalizeConnect called but OAuth code exchange not yet wired (workspace={}, params keys={})",
            ref.workspaceId(), callbackParams == null ? null : callbackParams.keySet());
        return new ConnectFinalization.Failed("OAuth code exchange not yet wired");
    }

    @Override
    public ValidationResult validate(IntegrationRef ref, CredentialBundle credentials) {
        // Honest: auth.info probe ships with the Outline OAuth client (#1203).
        return new ValidationResult.NotImplemented("Outline auth.info probe not wired");
    }

    @Override
    public void revoke(IntegrationRef ref) {
        // TODO(#1203): no Outline-side revoke endpoint exists today; best-effort delete of
        // our integration_webhook_subscription rows lives in the lifecycle path.
        log.debug("Outline revoke stub for workspace={}", ref.workspaceId());
    }

    private static String enc(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
