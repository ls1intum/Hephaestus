package de.tum.cit.aet.hephaestus.integration.scm.gitlab.connect;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.CredentialBundle;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionStrategy;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * GitLab connection lifecycle strategy.
 *
 * <p>GitLab uses a Personal Access Token paste flow (no OAuth round-trip in the
 * first-cut migration). The user enters:
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
 * <p>{@link #revoke} is best-effort and currently a no-op log: GitLab PATs can only
 * be revoked from the user's profile page — there is no admin-side revoke API for
 * tokens the user issued themselves. Local state transitions are handled by the
 * caller via {@code ConnectionService.transition()}.
 */
@ConditionalOnServerRole
@Component
public class GitlabConnectionStrategy implements ConnectionStrategy {

    private static final Logger log = LoggerFactory.getLogger(GitlabConnectionStrategy.class);

    static final String INPUT_PAT = "pat";
    static final String INPUT_GROUP_ID = "group_id";

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
    public void revoke(IntegrationRef ref) {
        // No-op: GitLab PATs are user-scoped and revoke endpoints are unavailable to
        // third parties. The Connection state transition to UNINSTALLED is performed
        // by the orchestrator via ConnectionService — we just log here so audit
        // trails can correlate the call.
        log.info(
            "GitLab revoke called for workspace={} instanceKey={} (no vendor-side revoke API; state change handled by caller)",
            ref == null ? null : ref.workspaceId(),
            ref == null ? null : ref.instanceKey()
        );
    }
}
