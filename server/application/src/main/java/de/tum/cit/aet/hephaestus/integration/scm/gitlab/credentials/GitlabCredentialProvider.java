package de.tum.cit.aet.hephaestus.integration.scm.gitlab.credentials;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.connection.CredentialBundleConverter;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GitlabCredentialProvider implements ApiCredentialProvider {

    private static final Logger log = LoggerFactory.getLogger(GitlabCredentialProvider.class);

    private final ConnectionService connectionService;
    private final CredentialBundleConverter credentialConverter;

    public GitlabCredentialProvider(
            ConnectionService connectionService, CredentialBundleConverter credentialConverter) {
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
        Optional<Connection> connection = connectionService.findReferenced(ref);
        if (connection.isEmpty()) {
            log.debug("No GitLab Connection for workspace={}", ref.workspaceId());
            return Optional.empty();
        }
        Connection conn = connection.get();
        if (conn.getCredentialsEncrypted() == null) {
            log.warn("GitLab Connection {} has no credentials_encrypted blob; cannot resolve PAT", ref.instanceKey());
            return Optional.empty();
        }
        return conn.credentials(credentialConverter);
    }
}
