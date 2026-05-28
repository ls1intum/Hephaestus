package de.tum.cit.aet.hephaestus.integration.scm.gitlab.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties;
import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties.Http;
import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties.Publish;
import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties.Shutdown;
import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties.Stream;
import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties.TokenRotation;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabTokenRotationClient;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabTokenRotationClient.RotatedToken;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabTokenRotationClient.TokenInfo;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabTokenService;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabWebhookClient;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabWebhookClient.GroupInfo;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabWebhookClient.WebhookConfig;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabWebhookClient.WebhookInfo;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Unit tests for {@link GitLabWebhookService}.
 *
 * <p>legacy {@code Workspace.gitlab*} setters are gone; the
 * service now reads/writes the {@code GitLabConfig} on the active GitLab Connection
 * via {@link ConnectionService}. Tests mirror the new flow via a small in-memory map
 * stand-in for the registry so we don't need an integration test container here.
 */
@Tag("unit")
class GitLabWebhookServiceTest extends BaseUnitTest {

    @Mock
    private ObjectProvider<GitLabWebhookClient> webhookClientProvider;

    @Mock
    private ObjectProvider<GitLabTokenRotationClient> rotationClientProvider;

    @Mock
    private ObjectProvider<GitLabTokenService> tokenServiceProvider;

    @Mock
    private GitLabWebhookClient webhookClient;

    @Mock
    private GitLabTokenRotationClient rotationClient;

    @Mock
    private GitLabTokenService tokenService;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private ConnectionService connectionService;

    private GitLabWebhookService webhookService;
    private Workspace workspace;
    private Map<Long, ConnectionConfig.GitLabConfig> gitLabConfigs;
    private Map<Long, BearerToken> gitLabBearerTokens;

    private static final String EXTERNAL_URL = "https://app.example.com";
    private static final String SECRET = "a]RkF9P2s#Lm7$xQ4wN!vB3yJ6tH0dCe";

    @BeforeEach
    void setUp() {
        WebhookProperties properties = new WebhookProperties(
            EXTERNAL_URL,
            SECRET,
            new TokenRotation(7, 90),
            new Publish(java.time.Duration.ofSeconds(9), 5, java.time.Duration.ofMillis(200)),
            new Stream(java.time.Duration.ofMinutes(2), java.time.Duration.ofDays(180), 2_000_000L),
            new Shutdown(java.time.Duration.ofSeconds(15)),
            new Http(26_214_400L)
        );

        webhookService = new GitLabWebhookService(
            webhookClientProvider,
            rotationClientProvider,
            tokenServiceProvider,
            properties,
            workspaceRepository,
            connectionService
        );

        workspace = new Workspace();
        workspace.setAccountLogin("my-org");
        ReflectionTestUtils.setField(workspace, "id", 1L);

        gitLabConfigs = new HashMap<>();
        gitLabBearerTokens = new HashMap<>();

        // Default: workspace is a GitLab workspace with a token. Tests that need
        // to flip this out (non-GitLab, missing token) override per-test.
        bindGitLabConfig(
            1L,
            new ConnectionConfig.GitLabConfig(
                "https://gitlab.com",
                null,
                null,
                ConnectionConfig.GitLabConfig.SigningMode.PLAINTEXT,
                Set.of()
            )
        );
        gitLabBearerTokens.put(1L, new BearerToken("glpat-test-token", null));

        // lenient() — each Nested test exercises a different code path, so a given
        // stub may go unused per test. Mockito's strict-stub mode would otherwise reject
        // the shared setUp.
        Mockito.lenient()
            .when(connectionService.findActiveProviderKind(anyLong()))
            .thenAnswer(inv -> {
                long id = inv.getArgument(0);
                return gitLabConfigs.containsKey(id) ? Optional.of(IntegrationKind.GITLAB) : Optional.empty();
            });
        Mockito.lenient()
            .when(connectionService.findActiveGitLabConfig(anyLong()))
            .thenAnswer(inv -> {
                long id = inv.getArgument(0);
                return Optional.ofNullable(gitLabConfigs.get(id));
            });
        Mockito.lenient()
            .when(connectionService.findActiveBearerToken(anyLong(), eq(IntegrationKind.GITLAB)))
            .thenAnswer(inv -> {
                long id = inv.getArgument(0);
                return Optional.ofNullable(gitLabBearerTokens.get(id));
            });
        Mockito.lenient()
            .when(connectionService.updateConfig(anyLong(), eq(IntegrationKind.GITLAB), any()))
            .thenAnswer(this::applyUpdateConfig);
        Mockito.lenient()
            .when(connectionService.rotateBearerToken(anyLong(), eq(IntegrationKind.GITLAB), any(BearerToken.class)))
            .thenAnswer(inv -> {
                long id = inv.getArgument(0);
                BearerToken token = inv.getArgument(2);
                gitLabBearerTokens.put(id, token);
                return Optional.empty();
            });
    }

    private void bindGitLabConfig(long workspaceId, ConnectionConfig.GitLabConfig cfg) {
        gitLabConfigs.put(workspaceId, cfg);
    }

    @SuppressWarnings("unchecked")
    private Optional<Object> applyUpdateConfig(InvocationOnMock inv) {
        long id = inv.getArgument(0);
        UnaryOperator<ConnectionConfig> mutator = inv.getArgument(2);
        ConnectionConfig.GitLabConfig current = gitLabConfigs.get(id);
        if (current == null) return Optional.empty();
        ConnectionConfig.GitLabConfig next = (ConnectionConfig.GitLabConfig) mutator.apply(current);
        gitLabConfigs.put(id, next);
        return Optional.empty();
    }

    private ConnectionConfig.GitLabConfig currentConfig(long workspaceId) {
        return gitLabConfigs.get(workspaceId);
    }

    @Nested
    class RegisterWebhook {

        @Test
        void shouldSkipForNonGitLab() {
            gitLabConfigs.remove(1L);

            WebhookSetupResult result = webhookService.registerWebhook(workspace);

            assertThat(result.registered()).isFalse();
            assertThat(result.failureReason()).contains("Not a GitLab");
        }

        @Test
        void shouldSkipWhenClientUnavailable() {
            when(webhookClientProvider.getIfAvailable()).thenReturn(null);

            WebhookSetupResult result = webhookService.registerWebhook(workspace);

            assertThat(result.registered()).isFalse();
            assertThat(result.failureReason()).contains("unavailable");
        }

        @Test
        void shouldRegisterNewWebhook() {
            when(webhookClientProvider.getIfAvailable()).thenReturn(webhookClient);
            when(webhookClient.lookupGroup(1L, "my-org")).thenReturn(new GroupInfo(42L, "My Org", "my-org"));
            when(webhookClient.listGroupWebhooks(1L, 42L)).thenReturn(List.of());
            when(webhookClient.registerGroupWebhook(eq(1L), eq(42L), any(WebhookConfig.class))).thenReturn(
                new WebhookInfo(99L, EXTERNAL_URL + "/webhooks/gitlab")
            );

            WebhookSetupResult result = webhookService.registerWebhook(workspace);

            assertThat(result.registered()).isTrue();
            assertThat(result.webhookId()).isEqualTo(99L);
            assertThat(result.groupId()).isEqualTo(42L);
            assertThat(currentConfig(1L).gitlabGroupId()).isEqualTo(42L);
            assertThat(currentConfig(1L).gitlabWebhookId()).isEqualTo(99L);
        }

        @Test
        void shouldAdoptExistingWebhook() {
            when(webhookClientProvider.getIfAvailable()).thenReturn(webhookClient);
            when(webhookClient.lookupGroup(1L, "my-org")).thenReturn(new GroupInfo(42L, "My Org", "my-org"));
            when(webhookClient.listGroupWebhooks(1L, 42L)).thenReturn(
                List.of(
                    new WebhookInfo(77L, EXTERNAL_URL + "/webhooks/gitlab"),
                    new WebhookInfo(78L, "https://other.com/hooks")
                )
            );

            WebhookSetupResult result = webhookService.registerWebhook(workspace);

            assertThat(result.registered()).isTrue();
            assertThat(result.webhookId()).isEqualTo(77L);
            verify(webhookClient, never()).registerGroupWebhook(anyLong(), anyLong(), any());
        }

        @Test
        void shouldReturnSuccessForExistingWebhook() {
            bindGitLabConfig(
                1L,
                new ConnectionConfig.GitLabConfig(
                    "https://gitlab.com",
                    42L,
                    99L,
                    ConnectionConfig.GitLabConfig.SigningMode.PLAINTEXT,
                    Set.of()
                )
            );

            when(webhookClientProvider.getIfAvailable()).thenReturn(webhookClient);
            when(webhookClient.getGroupWebhook(1L, 42L, 99L)).thenReturn(
                Optional.of(new WebhookInfo(99L, EXTERNAL_URL + "/webhooks/gitlab"))
            );

            WebhookSetupResult result = webhookService.registerWebhook(workspace);

            assertThat(result.registered()).isTrue();
            assertThat(result.webhookId()).isEqualTo(99L);
            verify(webhookClient, never()).registerGroupWebhook(anyLong(), anyLong(), any());
        }

        @Test
        void shouldReRegisterDeletedWebhook() {
            bindGitLabConfig(
                1L,
                new ConnectionConfig.GitLabConfig(
                    "https://gitlab.com",
                    42L,
                    99L,
                    ConnectionConfig.GitLabConfig.SigningMode.PLAINTEXT,
                    Set.of()
                )
            );

            when(webhookClientProvider.getIfAvailable()).thenReturn(webhookClient);
            when(webhookClient.getGroupWebhook(1L, 42L, 99L)).thenReturn(Optional.empty()); // Deleted externally
            when(webhookClient.listGroupWebhooks(1L, 42L)).thenReturn(List.of());
            when(webhookClient.registerGroupWebhook(eq(1L), eq(42L), any(WebhookConfig.class))).thenReturn(
                new WebhookInfo(100L, EXTERNAL_URL + "/webhooks/gitlab")
            );

            WebhookSetupResult result = webhookService.registerWebhook(workspace);

            assertThat(result.registered()).isTrue();
            assertThat(result.webhookId()).isEqualTo(100L);
        }

        @Test
        void shouldReturnFailedOn403() {
            when(webhookClientProvider.getIfAvailable()).thenReturn(webhookClient);
            when(webhookClient.lookupGroup(1L, "my-org")).thenReturn(new GroupInfo(42L, "My Org", "my-org"));
            when(webhookClient.listGroupWebhooks(1L, 42L)).thenReturn(List.of());
            when(webhookClient.registerGroupWebhook(eq(1L), eq(42L), any(WebhookConfig.class))).thenThrow(
                WebClientResponseException.create(403, "Forbidden", null, null, null)
            );

            WebhookSetupResult result = webhookService.registerWebhook(workspace);

            assertThat(result.registered()).isFalse();
            assertThat(result.failureReason()).contains("Insufficient permissions");
        }

        @Test
        void shouldSkipWhenNotConfigured() {
            WebhookProperties unconfigured = new WebhookProperties(
                "",
                "",
                new TokenRotation(7, 90),
                new Publish(java.time.Duration.ofSeconds(9), 5, java.time.Duration.ofMillis(200)),
                new Stream(java.time.Duration.ofMinutes(2), java.time.Duration.ofDays(180), 2_000_000L),
                new Shutdown(java.time.Duration.ofSeconds(15)),
                new Http(26_214_400L)
            );
            var service = new GitLabWebhookService(
                webhookClientProvider,
                rotationClientProvider,
                tokenServiceProvider,
                unconfigured,
                workspaceRepository,
                connectionService
            );

            WebhookSetupResult result = service.registerWebhook(workspace);

            assertThat(result.registered()).isFalse();
            assertThat(result.failureReason()).contains("not configured");
        }
    }

    @Nested
    class RotateTokenIfNeeded {

        @Test
        void shouldSkipForNonGitLab() {
            gitLabConfigs.remove(1L);

            webhookService.rotateTokenIfNeeded(workspace);

            verify(rotationClientProvider, never()).getIfAvailable();
        }

        @Test
        void shouldSkipWhenUnavailable() {
            when(rotationClientProvider.getIfAvailable()).thenReturn(null);

            webhookService.rotateTokenIfNeeded(workspace);
            // No exception, no rotation
        }

        @Test
        void shouldSkipNoExpiry() {
            when(rotationClientProvider.getIfAvailable()).thenReturn(rotationClient);
            when(rotationClient.getTokenInfo(1L)).thenReturn(new TokenInfo(1L, "test", null));

            webhookService.rotateTokenIfNeeded(workspace);

            verify(rotationClient, never()).rotateToken(anyLong(), any());
        }

        @Test
        void shouldSkipNotExpiringSoon() {
            when(rotationClientProvider.getIfAvailable()).thenReturn(rotationClient);
            when(rotationClient.getTokenInfo(1L)).thenReturn(new TokenInfo(1L, "test", LocalDate.now().plusDays(30)));

            webhookService.rotateTokenIfNeeded(workspace);

            verify(rotationClient, never()).rotateToken(anyLong(), any());
        }

        @Test
        void shouldRotateWhenExpiringSoon() {
            when(rotationClientProvider.getIfAvailable()).thenReturn(rotationClient);
            when(tokenServiceProvider.getIfAvailable()).thenReturn(tokenService);
            when(rotationClient.getTokenInfo(1L)).thenReturn(new TokenInfo(1L, "test", LocalDate.now().plusDays(3)));
            when(rotationClient.rotateToken(eq(1L), any(LocalDate.class))).thenReturn(
                new RotatedToken("glpat-new-token", LocalDate.now().plusDays(90))
            );

            webhookService.rotateTokenIfNeeded(workspace);

            assertThat(gitLabBearerTokens.get(1L).token()).isEqualTo("glpat-new-token");
            verify(connectionService).rotateBearerToken(eq(1L), eq(IntegrationKind.GITLAB), any(BearerToken.class));
            verify(tokenService).invalidateCache(1L);
        }

        @Test
        void shouldContinueOnError() {
            when(rotationClientProvider.getIfAvailable()).thenReturn(rotationClient);
            when(rotationClient.getTokenInfo(1L)).thenThrow(new IllegalStateException("Connection refused"));

            // Should not throw
            webhookService.rotateTokenIfNeeded(workspace);
        }
    }

    @Nested
    class DeregisterWebhook {

        @Test
        void shouldSkipWhenNoWebhook() {
            webhookService.deregisterWebhook(workspace);

            verify(webhookClientProvider, never()).getIfAvailable();
        }

        @Test
        void shouldDeregisterAndClearFields() {
            bindGitLabConfig(
                1L,
                new ConnectionConfig.GitLabConfig(
                    "https://gitlab.com",
                    42L,
                    99L,
                    ConnectionConfig.GitLabConfig.SigningMode.PLAINTEXT,
                    Set.of()
                )
            );

            when(webhookClientProvider.getIfAvailable()).thenReturn(webhookClient);

            webhookService.deregisterWebhook(workspace);

            verify(webhookClient).deregisterGroupWebhook(1L, 42L, 99L);
            assertThat(currentConfig(1L).gitlabWebhookId()).isNull();
            assertThat(currentConfig(1L).gitlabGroupId()).isNull();
        }

        @Test
        void shouldClearFieldsOnError() {
            bindGitLabConfig(
                1L,
                new ConnectionConfig.GitLabConfig(
                    "https://gitlab.com",
                    42L,
                    99L,
                    ConnectionConfig.GitLabConfig.SigningMode.PLAINTEXT,
                    Set.of()
                )
            );

            when(webhookClientProvider.getIfAvailable()).thenReturn(webhookClient);

            // Simulate API error
            org.mockito.Mockito.doThrow(new RuntimeException("API error"))
                .when(webhookClient)
                .deregisterGroupWebhook(1L, 42L, 99L);

            webhookService.deregisterWebhook(workspace);

            // Fields should still be cleared (best-effort)
            assertThat(currentConfig(1L).gitlabWebhookId()).isNull();
            assertThat(currentConfig(1L).gitlabGroupId()).isNull();
        }

        @Test
        void shouldClearFieldsWhenClientUnavailable() {
            bindGitLabConfig(
                1L,
                new ConnectionConfig.GitLabConfig(
                    "https://gitlab.com",
                    42L,
                    99L,
                    ConnectionConfig.GitLabConfig.SigningMode.PLAINTEXT,
                    Set.of()
                )
            );

            when(webhookClientProvider.getIfAvailable()).thenReturn(null);

            webhookService.deregisterWebhook(workspace);

            assertThat(currentConfig(1L).gitlabWebhookId()).isNull();
            assertThat(currentConfig(1L).gitlabGroupId()).isNull();
        }
    }

    @Nested
    class DeregisterWebhookByWorkspaceId {

        @Test
        void shouldDeregisterForExistingWorkspace() {
            bindGitLabConfig(
                1L,
                new ConnectionConfig.GitLabConfig(
                    "https://gitlab.com",
                    42L,
                    99L,
                    ConnectionConfig.GitLabConfig.SigningMode.PLAINTEXT,
                    Set.of()
                )
            );

            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(webhookClientProvider.getIfAvailable()).thenReturn(webhookClient);

            webhookService.deregisterWebhookByWorkspaceId(1L);

            verify(webhookClient).deregisterGroupWebhook(1L, 42L, 99L);
            assertThat(currentConfig(1L).gitlabWebhookId()).isNull();
            assertThat(currentConfig(1L).gitlabGroupId()).isNull();
        }

        @Test
        void shouldDoNothingWhenWorkspaceNotFound() {
            when(workspaceRepository.findById(999L)).thenReturn(Optional.empty());

            webhookService.deregisterWebhookByWorkspaceId(999L);

            verify(webhookClientProvider, never()).getIfAvailable();
        }
    }
}
