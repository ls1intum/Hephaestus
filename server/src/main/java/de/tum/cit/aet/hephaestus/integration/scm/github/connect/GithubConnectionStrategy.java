package de.tum.cit.aet.hephaestus.integration.scm.github.connect;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.oauth.state.OAuthStateService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.InstallationCredential;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionStrategy;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.workspace.ScmWorkspaceContentEraser;
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
 * {@link InstallationCredential} for orchestrator persistence.
 *
 * <p>{@link #revoke} makes no vendor-side call — GitHub App uninstall happens on GitHub's side and
 * we observe {@code installation.deleted} via the lifecycle webhook — but it DOES erase this
 * workspace's mirrored SCM data through {@link ScmWorkspaceContentEraser}. That heals a real
 * asymmetry: a vendor-side uninstall already erased everything (via
 * {@code GitHubWorkspaceProvisioningAdapter#onInstallationDeleted}), while an admin disconnect
 * erased nothing, so mirrored issues/PRs/reviews/comments outlived the connection that justified
 * holding them. Slack and Outline erase on disconnect too; this brings GitHub in line.
 */
@ConditionalOnServerRole
@Component
public class GithubConnectionStrategy implements ConnectionStrategy {

    private static final Logger log = LoggerFactory.getLogger(GithubConnectionStrategy.class);

    private static final String CALLBACK_PARAM_INSTALLATION_ID = "installation_id";
    private static final String CALLBACK_PARAM_STATE = "state";

    private final String installUrl;
    private final String appId;
    private final OAuthStateService oauthStateService;
    private final ScmWorkspaceContentEraser contentEraser;

    public GithubConnectionStrategy(
        @Value(
            "${hephaestus.integration.github.app.installation-url:${hephaestus.integration.github.app.install-url:}}"
        ) String installUrl,
        @Value("${hephaestus.integration.github.app.id:}") String appId,
        OAuthStateService oauthStateService,
        ScmWorkspaceContentEraser contentEraser
    ) {
        this.installUrl = installUrl == null ? "" : installUrl.trim();
        this.appId = appId == null ? "" : appId.trim();
        this.oauthStateService = oauthStateService;
        this.contentEraser = contentEraser;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    @Override
    public ConnectInitiation initiate(InitiateRequest request) {
        if (installUrl.isEmpty()) {
            throw new IllegalStateException(
                "hephaestus.integration.github.app.installation-url is not configured — cannot initiate GitHub App install"
            );
        }
        String state = oauthStateService.issue(request.workspaceId(), IntegrationKind.GITHUB, request.actorRef());
        String separator = installUrl.contains("?") ? "&" : "?";
        URI vendorUrl = URI.create(
            installUrl + separator + CALLBACK_PARAM_STATE + "=" + URLEncoder.encode(state, StandardCharsets.UTF_8)
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
        // wired hephaestus.integration.github.app.id yet. The credential record carries it so
        // downstream callers (token refresh) can reconstruct the JWT without hitting
        // the DB for an unrelated property.
        InstallationCredential credentials = new InstallationCredential(installationId, appId);
        return new ConnectFinalization.Completed(Long.toString(installationId), credentials, null);
    }

    @Override
    public void revoke(IntegrationRef ref) {
        if (ref == null) {
            return;
        }
        // No vendor-side call: GitHub App uninstall is initiated on github.com (App settings UI);
        // we observe installation.deleted via the lifecycle webhook and transition the Connection
        // there. The caller drives the local state change via ConnectionService.disconnect().
        //
        // The LOCAL erase, however, belongs here. This runs inside the fenced disconnect
        // transaction (sync jobs already cancelled/refused), before the UNINSTALLED transition
        // clears credentials. Hard-delete, orphan-guarded: repositories shared with another
        // workspace survive — see ScmWorkspaceContentEraser.
        log.info("GitHub revoke: erasing local SCM mirror (uninstall itself is webhook-driven), ref={}", ref);
        contentEraser.eraseWorkspaceScmMirror(ref.workspaceId());
    }
}
