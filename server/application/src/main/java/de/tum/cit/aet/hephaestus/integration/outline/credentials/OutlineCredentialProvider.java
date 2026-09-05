package de.tum.cit.aet.hephaestus.integration.outline.credentials;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.connection.CredentialReader;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
public class OutlineCredentialProvider implements ApiCredentialProvider {

    private static final Logger log = LoggerFactory.getLogger(OutlineCredentialProvider.class);

    private final ConnectionService connectionService;
    private final CredentialReader credentialReader;

    public OutlineCredentialProvider(ConnectionService connectionService, CredentialReader credentialReader) {
        this.connectionService = connectionService;
        this.credentialReader = credentialReader;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.OUTLINE;
    }

    @Override
    public Optional<CredentialBundle> resolve(IntegrationRef ref) {
        Optional<Connection> connection = connectionService.findReferenced(ref);
        if (connection.isEmpty()) {
            log.debug("Outline credential resolve: no Connection for workspace={}", ref.workspaceId());
            return Optional.empty();
        }
        Connection conn = connection.get();
        if (conn.getCredentialsEncrypted() == null) {
            log.warn("Outline Connection {} has no credentials blob — cannot resolve API token", conn.getId());
            return Optional.empty();
        }
        return credentialReader.credentialsOf(conn);
    }
}
