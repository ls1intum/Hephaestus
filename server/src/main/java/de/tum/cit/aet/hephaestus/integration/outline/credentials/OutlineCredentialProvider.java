package de.tum.cit.aet.hephaestus.integration.outline.credentials;

import de.tum.cit.aet.hephaestus.integration.registry.Connection;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationState;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stub {@link ApiCredentialProvider} for Outline.
 *
 * <p>Same pattern as the Slack stub — looks up the active Connection but returns
 * {@link Optional#empty()} with a TODO log line until the credential converter
 * lands. Outline credentials will be an {@link OAuthSession} (Outline returns
 * access + refresh tokens), unlike Slack's plain bot token.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = true)
public class OutlineCredentialProvider implements ApiCredentialProvider {

    private static final Logger log = LoggerFactory.getLogger(OutlineCredentialProvider.class);

    private final ConnectionService connectionService;

    public OutlineCredentialProvider(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.OUTLINE;
    }

    @Override
    public Optional<CredentialBundle> resolve(IntegrationRef ref) {
        Optional<Connection> connection = connectionService.findActive(ref.workspaceId(), IntegrationKind.OUTLINE);
        if (connection.isEmpty()) {
            log.debug("Outline credential resolve: no ACTIVE Connection for workspace={}", ref.workspaceId());
            return Optional.empty();
        }
        if (connection.get().getState() != IntegrationState.ACTIVE) {
            return Optional.empty();
        }
        // TODO(#1203): hand back OAuthSession once the credential converter decrypts
        // Connection.credentialsEncrypted into access + refresh tokens.
        log.debug("Outline credential resolve: Connection {} present but credential converter not yet wired",
            connection.get().getId());
        return Optional.empty();
    }
}
