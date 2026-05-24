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
 * GitHub adapter for {@link ConnectionStrategy}. Encodes the GitHub App install flow:
 * {@link #initiate} bounces the user to the configured App install URL with a signed
 * {@code state} parameter; {@link #finalizeConnect} reads the {@code installation_id}
 * returned by the GitHub callback and constructs a {@link GithubAppCredential} so the
 * orchestrator can persist a {@code Connection} row keyed on that installation.
 *
 * <p>This is the SKELETON wiring for #1198. {@link #validate} returns {@code Ok}
 * unconditionally — a real-world impl would call {@code GET /app/installations/{id}}
 * via the existing {@code GitHubAppTokenService.isInstallationSuspended} probe to
 * fail-fast on revoked installations. {@link #revoke} is a no-op log line because
 * GitHub App uninstall is initiated on GitHub's side (we observe via
 * {@code installation.deleted} webhook + transition our row to {@code UNINSTALLED}).
 * Both gaps are tracked TODOs for the C13 follow-up.
 */
@Component
public class GithubConnectionStrategy implements ConnectionStrategy {

    private static final Logger log = LoggerFactory.getLogger(GithubConnectionStrategy.class);

    private static final String CALLBACK_PARAM_INSTALLATION_ID = "installation_id";
    private static final String CALLBACK_PARAM_STATE = "state";

    private final String installUrl;
    private final OAuthStateService oauthStateService;

    public GithubConnectionStrategy(
        @Value("${hephaestus.github.app.install-url:}") String installUrl,
        OAuthStateService oauthStateService
    ) {
        this.installUrl = installUrl == null ? "" : installUrl.trim();
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
        // appId placeholder mirrors GithubCredentialProvider — Connection schema does not yet carry it.
        GithubAppCredential credentials = new GithubAppCredential(installationId, "unknown");
        return new ConnectFinalization.Completed(Long.toString(installationId), credentials, null);
    }

    @Override
    public ValidationResult validate(IntegrationRef ref, de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.CredentialBundle credentials) {
        // TODO(#1198 follow-up): probe GET /app/installations/{id} via GitHubAppTokenService.isInstallationSuspended
        // to fail-fast on revoked installations before transitioning the Connection to ACTIVE.
        return new ValidationResult.Ok(null, null);
    }

    @Override
    public void revoke(IntegrationRef ref) {
        log.warn(
            "GitHub revoke is a local no-op — uninstall happens via the GitHub App settings UI (ref={})",
            ref
        );
        // TODO(#1198 follow-up): wire to ConnectionService.transition(... UNINSTALLED ...) once the
        // adapter owns lifecycle transitions; the legacy installation webhook handler still drives them today.
    }
}
