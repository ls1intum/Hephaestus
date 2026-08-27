package de.tum.cit.aet.hephaestus.integration.scm.gitlab.connect;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.CredentialBundle;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionStrategy;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.workspace.GitLabWebhookService;
import de.tum.cit.aet.hephaestus.workspace.ScmWorkspaceContentEraser;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * GitLab connection lifecycle strategy.
 *
 * <p>GitLab uses a Personal Access Token paste flow (no OAuth round-trip). The user enters:
 * <ul>
 *   <li>{@code pat} — their GitLab PAT scoped to {@code api}, {@code read_repository}, {@code write_repository}
 *   <li>{@code group_id} — the GitLab group id the PAT has access to; becomes the
 *       Connection's {@code instanceKey}
 * </ul>
 *
 * <p>{@link #initiate} returns {@link ConnectInitiation.AcceptInline} immediately —
 * there is no vendor redirect. {@link #finalizeConnect} is therefore a no-op (the
 * UI never invokes it for GitLab).
 *
 * <p>{@link #revoke} cannot revoke the PAT itself (GitLab PATs are revocable only from the
 * user's profile page — there is no third-party revoke API), but it DOES tear down the group
 * webhook we registered on connect. It runs from the disconnect flow while the Connection is
 * still ACTIVE and the PAT still live — the only window in which GitLab will hand out a token to
 * delete the hook — mirroring {@code OutlineConnectionStrategy.revoke}. It then erases this
 * workspace's mirrored SCM data through {@link ScmWorkspaceContentEraser}. Local state transitions
 * are handled by the caller via {@code ConnectionService.disconnect()}.
 *
 * <p>Disconnect is GitLab's <b>only</b> erase trigger: unlike a GitHub App there is no vendor-side
 * uninstall signal for a PAT.
 */
@ConditionalOnServerRole
@Component
public class GitlabConnectionStrategy implements ConnectionStrategy {

    private static final Logger log = LoggerFactory.getLogger(GitlabConnectionStrategy.class);

    static final String INPUT_PAT = "pat";
    static final String INPUT_GROUP_ID = "group_id";

    private final GitLabWebhookService webhookService;
    private final ScmWorkspaceContentEraser contentEraser;

    public GitlabConnectionStrategy(GitLabWebhookService webhookService, ScmWorkspaceContentEraser contentEraser) {
        this.webhookService = webhookService;
        this.contentEraser = contentEraser;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITLAB;
    }

    @Override
    public ConnectInitiation initiate(InitiateRequest request) {
        Map<String, String> userInput = request.userInput();
        if (userInput == null) {
            throw new IllegalArgumentException("GitLab initiate requires userInput with 'pat' and 'group_id'");
        }
        String pat = userInput.get(INPUT_PAT);
        if (pat == null || pat.isBlank()) {
            throw new IllegalArgumentException("Missing required field: '" + INPUT_PAT + "'");
        }
        String groupId = userInput.get(INPUT_GROUP_ID);
        if (groupId == null || groupId.isBlank()) {
            throw new IllegalArgumentException("Missing required field: '" + INPUT_GROUP_ID + "'");
        }
        // The PAT is held in memory only for the duration of this call; the caller
        // (ConnectionService) is responsible for encrypting and persisting via the
        // credential converter.
        CredentialBundle bundle = new BearerToken(pat, null);
        return new ConnectInitiation.AcceptInline(bundle, groupId);
    }

    @Override
    public ConnectFinalization finalizeConnect(IntegrationRef ref, Map<String, String> callbackParams) {
        // PAT-paste flow has no callback. Returning Failed surfaces a clear error if
        // the orchestrator mistakenly invokes this for GitLab.
        return new ConnectFinalization.Failed(
            "GitLab uses PAT-paste — finalizeConnect is not applicable; use initiate() output directly"
        );
    }

    @Override
    public void revoke(@Nullable IntegrationRef ref) {
        if (ref == null) {
            return;
        }
        log.info(
            "GitLab revoke called for workspace={} instanceKey={} (deregistering group webhook; PAT revoke is user-side)",
            ref.workspaceId(),
            ref.instanceKey()
        );
        if (ref.connectionId() == null) {
            webhookService.deregisterActiveWebhook(ref.workspaceId());
        } else {
            webhookService.deregisterWebhookForConnection(ref.workspaceId(), ref.connectionId());
        }
        contentEraser.eraseWorkspaceScmMirror(ref.workspaceId());
    }

    @Override
    public void revokeProvider(IntegrationRef ref) {
        if (ref == null || ref.connectionId() == null) {
            throw new IllegalArgumentException("GitLab provider teardown requires a connection id");
        }
        webhookService.deregisterWebhookForConnectionStrict(ref.workspaceId(), ref.connectionId());
    }
}
