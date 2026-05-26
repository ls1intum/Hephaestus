package de.tum.cit.aet.hephaestus.integration.outline.credentials;

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
 * {@link ApiCredentialProvider} for Outline.
 *
 * <p>Same shape as the Slack provider — looks up the active Connection and decrypts
 * the credential blob via {@link CredentialBundleConverter}. Outline credentials are
 * stored as whichever variant the initiation flow produced: {@link OAuthSession}
 * (Outline OAuth — access + refresh tokens) or {@link BearerToken} (API token pasted
 * directly). The converter round-trips the {@code @JsonTypeInfo} discriminator so
 * either shape comes back faithfully.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = true)
public class OutlineCredentialProvider implements ApiCredentialProvider {

    private static final Logger log = LoggerFactory.getLogger(OutlineCredentialProvider.class);

    private final ConnectionService connectionService;
    private final CredentialBundleConverter credentialConverter;

    public OutlineCredentialProvider(ConnectionService connectionService,
                                     CredentialBundleConverter credentialConverter) {
        this.connectionService = connectionService;
        this.credentialConverter = credentialConverter;
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
        Connection conn = connection.get();
        if (conn.getState() != IntegrationState.ACTIVE) {
            return Optional.empty();
        }
        if (conn.getCredentialsEncrypted() == null) {
            log.warn("Outline Connection {} has no credentials blob — cannot resolve token", conn.getId());
            return Optional.empty();
        }
        return conn.credentials(credentialConverter);
    }
}
