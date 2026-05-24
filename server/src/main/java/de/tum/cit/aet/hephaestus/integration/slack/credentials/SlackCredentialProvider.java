package de.tum.cit.aet.hephaestus.integration.slack.credentials;

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
 * Stub {@link ApiCredentialProvider} for Slack.
 *
 * <p>Looks up the active Connection (no Connection / non-ACTIVE state → empty).
 * Returns {@link Optional#empty()} unconditionally with a TODO log line: the
 * encrypted bot-token reader lands in the credential-converter follow-up
 * (#1198 next slice). Callers MUST already treat empty as "no auth available".
 *
 * <p>Once the converter exists, this stub becomes a one-liner that hands the
 * decrypted {@code xoxb-…} string back as {@link BearerToken}.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = true)
public class SlackCredentialProvider implements ApiCredentialProvider {

    private static final Logger log = LoggerFactory.getLogger(SlackCredentialProvider.class);

    private final ConnectionService connectionService;

    public SlackCredentialProvider(ConnectionService connectionService) {
        this.connectionService = connectionService;
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
        if (connection.get().getState() != IntegrationState.ACTIVE) {
            return Optional.empty();
        }
        // TODO(#1198 next slice): hand back BearerToken once the credential-converter
        // bean decrypts Connection.credentialsEncrypted into the bot token string.
        log.debug("Slack credential resolve: Connection {} present but credential converter not yet wired",
            connection.get().getId());
        return Optional.empty();
    }
}
