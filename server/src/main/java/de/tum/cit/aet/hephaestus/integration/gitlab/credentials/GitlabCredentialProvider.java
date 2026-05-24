package de.tum.cit.aet.hephaestus.integration.gitlab.credentials;

import de.tum.cit.aet.hephaestus.integration.registry.Connection;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationState;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Resolves credentials for the GitLab Connection bound to a workspace.
 *
 * <p>Lookup contract: returns {@link Optional#empty()} when (a) no Connection exists
 * for the workspace, or (b) the Connection state is not {@link IntegrationState#ACTIVE}.
 * Both signal "no auth available" to callers — the API client wrapper turns this into
 * a 4xx-style surfaced error rather than crashing the worker.
 *
 * <p>TODO(#1198 follow-up): actual decryption of {@link Connection#getCredentialsEncrypted()}
 * via the AES-GCM credential converter. That converter ships with the credentials
 * follow-up; for now we log clearly and return empty so the validation gates aren't
 * blocked. Once the converter lands, the body of this method shrinks to
 * {@code return Optional.of(new BearerToken(decryptToken(conn), null));}.
 */
@Component
public class GitlabCredentialProvider implements ApiCredentialProvider {

    private static final Logger log = LoggerFactory.getLogger(GitlabCredentialProvider.class);

    private final ConnectionService connectionService;

    public GitlabCredentialProvider(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITLAB;
    }

    @Override
    public Optional<CredentialBundle> resolve(IntegrationRef ref) {
        if (ref == null || ref.kind() != IntegrationKind.GITLAB) {
            return Optional.empty();
        }
        Optional<Connection> connection = connectionService.findActive(ref.workspaceId(), IntegrationKind.GITLAB);
        if (connection.isEmpty()) {
            log.debug("No ACTIVE GitLab Connection for workspace={}", ref.workspaceId());
            return Optional.empty();
        }
        Connection conn = connection.get();
        if (conn.getCredentialsEncrypted() == null) {
            log.warn("GitLab Connection {} has no credentials_encrypted blob; cannot resolve PAT",
                conn.getId());
            return Optional.empty();
        }
        // TODO(#1198 follow-up): decrypt via per-Connection AES-GCM converter and
        // wrap as BearerToken(decryptedPat, null). Until that converter is wired,
        // returning empty surfaces a clear "credentials unwired" signal instead of
        // an opaque NullPointerException downstream.
        log.warn(
            "GitlabCredentialProvider: credential decryption is not yet wired (connection={}, workspace={}). "
                + "Returning empty until the per-Connection credential converter ships.",
            conn.getId(), ref.workspaceId()
        );
        return Optional.empty();
    }
}
