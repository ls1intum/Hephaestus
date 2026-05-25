package de.tum.cit.aet.hephaestus.integration.github.connect;

import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.GithubAppCredential;
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
import org.springframework.stereotype.Component;

/**
 * GitHub adapter for {@link ConnectionStrategy}: GitHub App install flow.
 * {@link #initiate} bounces to the configured install URL with a signed {@code state};
 * {@link #finalizeConnect} reads {@code installation_id} from the callback and emits a
 * {@link GithubAppCredential} for orchestrator persistence.
 *
 * <p>{@link #validate} fails closed ("probe not wired") until the GitHub installation
 * health probe lands. {@link #revoke} is a local no-op log: GitHub App uninstall happens
 * on GitHub's side; we observe {@code installation.deleted} via the lifecycle webhook
 * and transition our row to {@code UNINSTALLED} from there.
 */
@Component
public class GithubConnectionStrategy implements ConnectionStrategy {

    private static final Logger log = LoggerFactory.getLogger(GithubConnectionStrategy.class);

    private static final String CALLBACK_PARAM_INSTALLATION_ID = "installation_id";
    private static final String CALLBACK_PARAM_STATE = "state";

    private final String installUrl;
    private final String appId;
    private final OAuthStateService oauthStateService;

    public GithubConnectionStrategy(
        @Value("${hephaestus.github.app.install-url:}") String installUrl,
        @Value("${hephaestus.github.app.id:}") String appId,
        OAuthStateService oauthStateService
    ) {
        this.installUrl = installUrl == null ? "" : installUrl.trim();
        this.appId = appId == null ? "" : appId.trim();
        this.oauthStateService = oauthStateService;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    @Override
    public ConnectInitiation initiate(InitiateRequest request) {
        if (installUrl.isEmpty()) {
            throw new IllegalStateException(
                "hephaestus.github.app.install-url is not configured — cannot initiate GitHub App install"
            );
        }
        String state = oauthStateService.issue(request.workspaceId(), IntegrationKind.GITHUB);
        String separator = installUrl.contains("?") ? "&" : "?";
        URI vendorUrl = URI.create(
            installUrl + separator + CALLBACK_PARAM_STATE + "=" +
                URLEncoder.encode(state, StandardCharsets.UTF_8)
        );
        return new ConnectInitiation.RedirectToVendor(vendorUrl, state);
    }

    @Override
    public ConnectFinalization finalizeConnect(IntegrationRef ref, Map<String, String> callbackParams) {
        if (callbackParams == null) {
            return new ConnectFinalization.Failed("missing callback params");
        }
        String installationIdRaw = callbackParams.get(CALLBACK_PARAM_INSTALLATION_ID);
        if (installationIdRaw == null || installationIdRaw.isBlank()) {
            return new ConnectFinalization.Failed("missing installation_id in callback");
        }
        long installationId;
        try {
            installationId = Long.parseLong(installationIdRaw.trim());
        } catch (NumberFormatException e) {
            return new ConnectFinalization.Failed("installation_id is not a valid long: " + installationIdRaw);
        }
        // appId from the configured GitHub App; blank in dev profiles that haven't
        // wired hephaestus.github.app.id yet. The credential record carries it so
        // downstream callers (token refresh) can reconstruct the JWT without hitting
        // the DB for an unrelated property.
        GithubAppCredential credentials = new GithubAppCredential(installationId, appId);
        return new ConnectFinalization.Completed(Long.toString(installationId), credentials, null);
    }

    @Override
    public ValidationResult validate(IntegrationRef ref, de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.CredentialBundle credentials) {
        // Fail closed: returning Ok would silently transition the Connection to ACTIVE on
        // revoked installations. Follow-up wiring will call
        // GitHubAppTokenService.isInstallationSuspended against GET /app/installations/{id}.
        return new ValidationResult.Failed("probe not wired");
    }

    @Override
    public void revoke(IntegrationRef ref) {
        // Local no-op: GitHub App uninstall is initiated on github.com (App settings UI);
        // we observe installation.deleted via the lifecycle webhook and transition the
        // Connection there. The caller still drives any local state change via
        // ConnectionService.transition() — no vendor-side call belongs here.
        log.info("GitHub revoke: local no-op (uninstall is webhook-driven), ref={}", ref);
    }
}
