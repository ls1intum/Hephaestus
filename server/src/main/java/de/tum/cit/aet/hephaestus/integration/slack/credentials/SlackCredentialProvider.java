package de.tum.cit.aet.hephaestus.integration.slack.credentials;

import de.tum.cit.aet.hephaestus.integration.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.connection.CredentialBundleConverter;
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
 * {@link ApiCredentialProvider} for Slack.
 *
 * <p>Looks up the active Connection (no Connection / non-ACTIVE state / missing blob
 * → empty) and decrypts the {@code xoxb-…} bot token via {@link CredentialBundleConverter},
 * returning a {@link BearerToken} bundle. Callers MUST already treat empty as
 * "no auth available".
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = true)
public class SlackCredentialProvider implements ApiCredentialProvider {

    private static final Logger log = LoggerFactory.getLogger(SlackCredentialProvider.class);

    private final ConnectionService connectionService;
    private final CredentialBundleConverter credentialConverter;

    public SlackCredentialProvider(ConnectionService connectionService, CredentialBundleConverter credentialConverter) {
        this.connectionService = connectionService;
        this.credentialConverter = credentialConverter;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.SLACK;
    }

    @Override
    public Optional<CredentialBundle> resolve(IntegrationRef ref) {
        Optional<Connection> connection = connectionService.findActive(ref.workspaceId(), IntegrationKind.SLACK);
        if (connection.isEmpty()) {
            log.debug("Slack credential resolve: no ACTIVE Connection for workspace={}", ref.workspaceId());
            return Optional.empty();
        }
        Connection conn = connection.get();
        if (conn.getState() != IntegrationState.ACTIVE) {
            return Optional.empty();
        }
        if (conn.getCredentialsEncrypted() == null) {
            log.warn("Slack Connection {} has no credentials blob — cannot resolve bot token", conn.getId());
            return Optional.empty();
        }
        return conn.credentials(credentialConverter);
    }
}
