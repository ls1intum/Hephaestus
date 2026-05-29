package de.tum.cit.aet.hephaestus.integration.identity.connect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderType;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RegistrationToGitProviderResolver}, the {@code GitProviderRegistry} SPI
 * implementation. Focus on the workspace-scoped path ({@code gh-ws-*}/{@code gl-ws-*}), which a
 * real OAuth round-trip reaches via {@code AccountProvisioningService} — previously a 500
 * ({@code IllegalStateException("...not yet wired")}).
 */
class RegistrationToGitProviderResolverTest extends BaseUnitTest {

    private GitProviderRepository gitProviderRepository;
    private ConnectionRepository connectionRepository;
    private RegistrationToGitProviderResolver resolver;

    @BeforeEach
    void setup() {
        gitProviderRepository = mock(GitProviderRepository.class);
        connectionRepository = mock(ConnectionRepository.class);
        resolver = new RegistrationToGitProviderResolver(gitProviderRepository, connectionRepository);
    }

    /** Upsert-miss path: stub save() to return the row with a stamped (DB-generated) id. */
    private void stubSaveStampsId() {
        when(gitProviderRepository.save(any(GitProvider.class))).thenAnswer(inv -> {
            GitProvider p = inv.getArgument(0);
            setId(p, 7L);
            return p;
        });
    }

    @Test
    void defaultGithubRegistrationUpsertsGithubComOrigin() {
        stubSaveStampsId();
        when(gitProviderRepository.findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")).thenReturn(
            Optional.empty()
        );

        GitProvider provider = resolver.resolve("github");

        assertThat(provider.getType()).isEqualTo(GitProviderType.GITHUB);
        assertThat(provider.getServerUrl()).isEqualTo("https://github.com");
    }

    @Test
    void workspaceScopedGitlabRegistrationResolvesFromConnectionIssuerOrigin() {
        stubSaveStampsId();
        Connection connection = oidcConnection(
            IntegrationKind.OIDC_LOGIN_GITLAB,
            "https://gitlab.example.test/sub/path"
        );
        when(connectionRepository.findById(42L)).thenReturn(Optional.of(connection));
        when(
            gitProviderRepository.findByTypeAndServerUrl(GitProviderType.GITLAB, "https://gitlab.example.test")
        ).thenReturn(Optional.empty());

        // The previously thrown path — now a clean upsert keyed on the issuer origin.
        GitProvider provider = resolver.resolve("gl-ws-42");

        assertThat(provider.getType()).isEqualTo(GitProviderType.GITLAB);
        assertThat(provider.getServerUrl()).isEqualTo("https://gitlab.example.test");
    }

    @Test
    void workspaceScopedRegistrationPreservesExplicitPortInOrigin() {
        stubSaveStampsId();
        Connection connection = oidcConnection(IntegrationKind.OIDC_LOGIN_GITHUB, "https://ghe.example.test:8443");
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(connection));
        when(
            gitProviderRepository.findByTypeAndServerUrl(GitProviderType.GITHUB, "https://ghe.example.test:8443")
        ).thenReturn(Optional.empty());

        GitProvider provider = resolver.resolve("gh-ws-9");

        assertThat(provider.getServerUrl()).isEqualTo("https://ghe.example.test:8443");
    }

    @Test
    void workspaceScopedRegistrationReusesExistingProviderRow() {
        Connection connection = oidcConnection(IntegrationKind.OIDC_LOGIN_GITLAB, "https://gitlab.lrz.de");
        GitProvider existing = new GitProvider(GitProviderType.GITLAB, "https://gitlab.lrz.de");
        setId(existing, 3L);
        when(connectionRepository.findById(5L)).thenReturn(Optional.of(connection));
        when(gitProviderRepository.findByTypeAndServerUrl(GitProviderType.GITLAB, "https://gitlab.lrz.de")).thenReturn(
            Optional.of(existing)
        );

        assertThat(resolver.resolveProviderId("gl-ws-5")).isEqualTo(3L);
    }

    @Test
    void missingConnectionForWorkspaceRegistrationFailsWithIllegalArgument() {
        when(connectionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve("gl-ws-99")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownRegistrationIdFailsWithIllegalArgument() {
        assertThatThrownBy(() -> resolver.resolve("totally-bogus")).isInstanceOf(IllegalArgumentException.class);
    }

    private static Connection oidcConnection(IntegrationKind kind, String issuerUrl) {
        Workspace workspace = new Workspace();
        setId(workspace, 1L);
        return new Connection(
            workspace,
            kind,
            "instance",
            new ConnectionConfig.OidcLoginConfig(issuerUrl, Set.of("read_user"), "Workspace IdP")
        );
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not set id via reflection", e);
        }
    }
}
