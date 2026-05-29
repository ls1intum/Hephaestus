package de.tum.cit.aet.hephaestus.integration.identity.connect;

import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderType;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration-side implementation of the {@link GitProviderRegistry} auth SPI: maps a Spring
 * {@code ClientRegistration} id to the persistent {@link GitProvider} row id (creating the row
 * on first sight). Used by {@code core.auth}'s account-provisioning to key the
 * {@code IdentityLink} by {@code (git_provider_id, subject)} from the
 * {@code OAuth2AuthenticationToken} returned by {@code oauth2Login} — without {@code core.auth}
 * importing the {@link GitProvider} entity.
 *
 * <p>The two env-default registrations ({@code github}, {@code gitlab-lrz}) map to fixed origins.
 * Workspace-scoped registrations follow the {@code gh-ws-{connectionId}} / {@code gl-ws-{connectionId}}
 * form (see {@link LoginClientRegistrationRepository}); their git-provider row is derived from the
 * backing {@link Connection}'s {@code OidcLoginConfig.issuerUrl()} origin.
 */
@Component
public class RegistrationToGitProviderResolver implements GitProviderRegistry {

    private static final String GITHUB_COM = "https://github.com";
    private static final String GITLAB_LRZ = "https://gitlab.lrz.de";
    private static final String GH_WS_PREFIX = "gh-ws-";
    private static final String GL_WS_PREFIX = "gl-ws-";

    private final GitProviderRepository gitProviderRepository;
    private final ConnectionRepository connectionRepository;

    public RegistrationToGitProviderResolver(
        GitProviderRepository gitProviderRepository,
        ConnectionRepository connectionRepository
    ) {
        this.gitProviderRepository = gitProviderRepository;
        this.connectionRepository = connectionRepository;
    }

    private static final String OIDC = "OIDC";

    @Override
    @Transactional
    public long resolveProviderId(String registrationId) {
        return resolve(registrationId).getId();
    }

    @Override
    @Transactional(readOnly = true)
    public String providerTypeName(Long gitProviderId) {
        if (gitProviderId == null) {
            return OIDC;
        }
        return gitProviderRepository
            .findById(gitProviderId)
            .map(p -> p.getType().name())
            .orElse(OIDC);
    }

    /**
     * Resolve (and create on first sight) the {@link GitProvider} row for an OAuth client
     * registration id. {@code registrationId} comes from Spring's
     * {@code OAuth2AuthenticationToken.getAuthorizedClientRegistrationId()}.
     */
    @Transactional
    public GitProvider resolve(String registrationId) {
        return switch (registrationId) {
            case "github" -> upsert(GitProviderType.GITHUB, GITHUB_COM);
            case "gitlab-lrz" -> upsert(GitProviderType.GITLAB, GITLAB_LRZ);
            default -> resolveWorkspaceScoped(registrationId);
        };
    }

    /**
     * Resolve the git-provider row for a workspace-scoped login registration
     * ({@code gh-ws-{connectionId}} / {@code gl-ws-{connectionId}}). The provider type is fixed by
     * the prefix and cross-checked against the backing {@link Connection}'s
     * {@link IntegrationKind}; the server URL is the origin of the connection's
     * {@code OidcLoginConfig.issuerUrl()}. Mirrors the env-default path's upsert-on-first-sight.
     */
    private GitProvider resolveWorkspaceScoped(String registrationId) {
        GitProviderType expectedType = registrationId.startsWith(GH_WS_PREFIX)
            ? GitProviderType.GITHUB
            : registrationId.startsWith(GL_WS_PREFIX)
                ? GitProviderType.GITLAB
                : null;
        if (expectedType == null) {
            throw new IllegalArgumentException("unknown OAuth login registrationId: " + registrationId);
        }
        long connectionId = parseConnectionId(registrationId);
        Connection connection = connectionRepository
            .findById(connectionId)
            .orElseThrow(() ->
                new IllegalArgumentException("no Connection for OAuth login registrationId: " + registrationId)
            );
        if (!(connection.getConfig() instanceof ConnectionConfig.OidcLoginConfig cfg)) {
            throw new IllegalStateException(
                "Connection " + connectionId + " backing registrationId=" + registrationId + " is not OIDC-login"
            );
        }
        // The id prefix and the Connection's kind must agree — a gl-ws-* id backed by an
        // OIDC_LOGIN_GITHUB connection (or vice versa) is a data/wiring bug, not a valid login.
        IntegrationKind expectedKind =
            expectedType == GitProviderType.GITHUB
                ? IntegrationKind.OIDC_LOGIN_GITHUB
                : IntegrationKind.OIDC_LOGIN_GITLAB;
        if (connection.getKind() != expectedKind) {
            throw new IllegalStateException(
                "Connection " +
                    connectionId +
                    " kind=" +
                    connection.getKind() +
                    " does not match registrationId=" +
                    registrationId
            );
        }
        return upsert(expectedType, originOf(cfg.issuerUrl()));
    }

    private static long parseConnectionId(String registrationId) {
        String prefix = registrationId.startsWith(GH_WS_PREFIX) ? GH_WS_PREFIX : GL_WS_PREFIX;
        try {
            return Long.parseLong(registrationId.substring(prefix.length()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("malformed OAuth login registrationId: " + registrationId, e);
        }
    }

    /**
     * The scheme + host (+ explicit non-default port) of the issuer URL — the canonical server URL
     * used to key the {@code git_provider} row, matching the env-default origins
     * ({@code https://github.com}, {@code https://gitlab.lrz.de}).
     */
    private static String originOf(String issuerUrl) {
        try {
            URI uri = new URI(issuerUrl);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalStateException("OIDC issuerUrl has no scheme/host: " + issuerUrl);
            }
            String origin = uri.getScheme() + "://" + uri.getHost();
            return uri.getPort() == -1 ? origin : origin + ":" + uri.getPort();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("malformed OIDC issuerUrl: " + issuerUrl, e);
        }
    }

    private GitProvider upsert(GitProviderType type, String serverUrl) {
        return gitProviderRepository
            .findByTypeAndServerUrl(type, serverUrl)
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(type, serverUrl)));
    }
}
