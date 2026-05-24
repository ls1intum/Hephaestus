package de.tum.cit.aet.hephaestus.integration.gitlab.credentials;

import de.tum.cit.aet.hephaestus.core.security.EncryptionException;
import de.tum.cit.aet.hephaestus.integration.registry.Connection;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.registry.CredentialBundleConverter;
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
 * for the workspace, (b) the Connection state is not {@link IntegrationState#ACTIVE},
 * or (c) no credential blob is stored on the row yet. All three signal "no auth
 * available" to callers — the API client wrapper turns this into a 4xx-style surfaced
 * error rather than crashing the worker.
 *
 * <p>Decryption errors ({@link EncryptionException} — typically key rotation without
 * re-encrypt, or row tampering) are NOT swallowed. We let them propagate, because
 * silently mapping "I cannot decrypt this blob" to "no credential present" would hide
 * a real security/operational failure.
 */
@Component
public class GitlabCredentialProvider implements ApiCredentialProvider {

    private static final Logger log = LoggerFactory.getLogger(GitlabCredentialProvider.class);

    private final ConnectionService connectionService;
    private final CredentialBundleConverter credentialConverter;

    public GitlabCredentialProvider(ConnectionService connectionService,
                                    CredentialBundleConverter credentialConverter) {
        this.connectionService = connectionService;
        this.credentialConverter = credentialConverter;
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
        return conn.credentials(credentialConverter);
    }
}
