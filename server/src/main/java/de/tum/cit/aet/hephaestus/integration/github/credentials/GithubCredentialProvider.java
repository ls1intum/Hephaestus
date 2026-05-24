package de.tum.cit.aet.hephaestus.integration.github.credentials;

import de.tum.cit.aet.hephaestus.integration.registry.Connection;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionConfig;
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
 * GitHub adapter for {@link ApiCredentialProvider}. Looks up the workspace's ACTIVE
 * {@code Connection} row via {@link ConnectionService#findActive} and maps the persisted
 * {@link ConnectionConfig} sub-type to a {@link ApiCredentialProvider.CredentialBundle}:
 * {@code GitHubAppConfig} → {@link GithubAppCredential} (installation identity only;
 * actual installation token is minted by {@link GithubTokenRefresher}),
 * {@code GitHubPatConfig} → {@link BearerToken} (decrypted from the per-row credential
 * blob via {@link CredentialBundleConverter}).
 *
 * <p>Returns {@link Optional#empty()} when no Connection exists, the Connection is not
 * ACTIVE, the persisted config is not a GitHub variant, or the PAT row has no credential
 * blob yet. Callers treat empty as "no auth available" and either prompt for reconnect
 * or suspend retry — they MUST NOT fall through to anonymous API access.
 */
@Component
public class GithubCredentialProvider implements ApiCredentialProvider {

    private static final Logger log = LoggerFactory.getLogger(GithubCredentialProvider.class);

    /** GitHub App ID isn't persisted on the Connection yet — we surface the runtime app id via {@link #appIdString()}. */
    private static final String UNKNOWN_APP_ID = "unknown";

    private final ConnectionService connectionService;
    private final CredentialBundleConverter credentialConverter;

    public GithubCredentialProvider(ConnectionService connectionService,
                                    CredentialBundleConverter credentialConverter) {
        this.connectionService = connectionService;
        this.credentialConverter = credentialConverter;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    @Override
    public Optional<CredentialBundle> resolve(IntegrationRef ref) {
        if (ref == null || ref.kind() != IntegrationKind.GITHUB) {
            return Optional.empty();
        }
        Optional<Connection> maybeConnection = connectionService.findActive(ref.workspaceId(), IntegrationKind.GITHUB);
        if (maybeConnection.isEmpty()) {
            return Optional.empty();
        }
        Connection connection = maybeConnection.get();
        if (connection.getState() != IntegrationState.ACTIVE) {
            return Optional.empty();
        }
        return switch (connection.getConfig()) {
            case ConnectionConfig.GitHubAppConfig appCfg -> {
                Long installationId = appCfg.installationId();
                if (installationId == null) {
                    log.warn(
                        "GitHub App Connection {} is ACTIVE but has no installationId — skipping credential resolution",
                        connection.getId()
                    );
                    yield Optional.empty();
                }
                yield Optional.of(new GithubAppCredential(installationId, appIdString()));
            }
            case ConnectionConfig.GitHubPatConfig ignored -> {
                if (connection.getCredentialsEncrypted() == null) {
                    log.warn(
                        "GitHub PAT Connection {} has no credentials blob — cannot resolve token",
                        connection.getId()
                    );
                    yield Optional.empty();
                }
                yield connection.credentials(credentialConverter);
            }
            default -> Optional.empty();
        };
    }

    /**
     * Placeholder for the configured GitHub App ID until the Connection schema carries it
     * directly. Resolving the live value would couple this adapter to
     * {@code GitHubAppTokenService.getConfiguredAppId()}; we accept the looser contract
     * for now because the downstream {@link GithubTokenRefresher} doesn't read appId from
     * the bundle — it uses {@code installationId} plus its own configured app key.
     */
    private static String appIdString() {
        return UNKNOWN_APP_ID;
    }
}
