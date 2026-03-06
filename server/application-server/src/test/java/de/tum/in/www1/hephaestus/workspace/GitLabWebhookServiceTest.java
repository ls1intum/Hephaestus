package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabTokenRotationClient;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabTokenRotationClient.RotatedToken;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabTokenRotationClient.TokenInfo;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabTokenService;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabWebhookClient;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabWebhookClient.GroupInfo;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabWebhookClient.WebhookConfig;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabWebhookClient.WebhookInfo;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Unit tests for {@link GitLabWebhookService}.
 */
@Tag("unit")
@DisplayName("GitLabWebhookService")
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

    private GitLabWebhookService webhookService;
    private Workspace workspace;

    private static final String EXTERNAL_URL = "https://app.example.com/webhooks";
    private static final String SECRET = "a]RkF9P2s#Lm7$xQ4wN!vB3yJ6tH0dCe";

    @BeforeEach
    void setUp() {
        WebhookProperties properties = new WebhookProperties(EXTERNAL_URL, SECRET, 7, 90);

        webhookService = new GitLabWebhookService(
            webhookClientProvider,
            rotationClientProvider,
            tokenServiceProvider,
            properties,
            workspaceRepository
        );

        workspace = new Workspace();
        workspace.setGitProviderMode(Workspace.GitProviderMode.GITLAB_PAT);
        workspace.setAccountLogin("my-org");
        workspace.setPersonalAccessToken("glpat-test-token");

        // Use reflection to set ID since there's no setter on @GeneratedValue fields
        try {
            var idField = Workspace.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(workspace, 1L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("registerWebhook")
    class RegisterWebhook {

        @Test
        @DisplayName("should skip for non-GitLab workspace")
        void shouldSkipForNonGitLab() {
            workspace.setGitProviderMode(Workspace.GitProviderMode.PAT_ORG);

            WebhookSetupResult result = webhookService.registerWebhook(workspace);

            assertThat(result.registered()).isFalse();
            assertThat(result.failureReason()).contains("Not a GitLab");
        }

        @Test
        @DisplayName("should skip when webhook client unavailable")
        void shouldSkipWhenClientUnavailable() {
            when(webhookClientProvider.getIfAvailable()).thenReturn(null);

            WebhookSetupResult result = webhookService.registerWebhook(workspace);

            assertThat(result.registered()).isFalse();
            assertThat(result.failureReason()).contains("unavailable");
        }

        @Test
        @DisplayName("should register new webhook successfully")
        void shouldRegisterNewWebhook() {
            when(webhookClientProvider.getIfAvailable()).thenReturn(webhookClient);
            when(webhookClient.lookupGroup(1L, "my-org")).thenReturn(new GroupInfo(42L, "My Org", "my-org"));
            when(webhookClient.listGroupWebhooks(1L, 42L)).thenReturn(List.of());
            when(webhookClient.registerGroupWebhook(eq(1L), eq(42L), any(WebhookConfig.class))).thenReturn(
                new WebhookInfo(99L, EXTERNAL_URL + "/gitlab")
            );
            when(workspaceRepository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));

            WebhookSetupResult result = webhookService.registerWebhook(workspace);

            assertThat(result.registered()).isTrue();
            assertThat(result.webhookId()).isEqualTo(99L);
            assertThat(result.groupId()).isEqualTo(42L);
            assertThat(workspace.getGitlabGroupId()).isEqualTo(42L);
            assertThat(workspace.getGitlabWebhookId()).isEqualTo(99L);
        }

        @Test
        @DisplayName("should adopt existing webhook with matching URL")
        void shouldAdoptExistingWebhook() {
            when(webhookClientProvider.getIfAvailable()).thenReturn(webhookClient);
            when(webhookClient.lookupGroup(1L, "my-org")).thenReturn(new GroupInfo(42L, "My Org", "my-org"));
            when(webhookClient.listGroupWebhooks(1L, 42L)).thenReturn(
                List.of(new WebhookInfo(77L, EXTERNAL_URL + "/gitlab"), new WebhookInfo(78L, "https://other.com/hooks"))
            );
            when(workspaceRepository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));

            WebhookSetupResult result = webhookService.registerWebhook(workspace);

            assertThat(result.registered()).isTrue();
            assertThat(result.webhookId()).isEqualTo(77L);
            verify(webhookClient, never()).registerGroupWebhook(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("should return success when webhook already registered and exists")
        void shouldReturnSuccessForExistingWebhook() {
            workspace.setGitlabGroupId(42L);
            workspace.setGitlabWebhookId(99L);

            when(webhookClientProvider.getIfAvailable()).thenReturn(webhookClient);
            when(webhookClient.getGroupWebhook(1L, 42L, 99L)).thenReturn(
                Optional.of(new WebhookInfo(99L, EXTERNAL_URL + "/gitlab"))
            );

            WebhookSetupResult result = webhookService.registerWebhook(workspace);

            assertThat(result.registered()).isTrue();
            assertThat(result.webhookId()).isEqualTo(99L);
            verify(webhookClient, never()).registerGroupWebhook(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("should re-register when stored webhook was deleted externally")
        void shouldReRegisterDeletedWebhook() {
            workspace.setGitlabGroupId(42L);
            workspace.setGitlabWebhookId(99L);

            when(webhookClientProvider.getIfAvailable()).thenReturn(webhookClient);
            when(webhookClient.getGroupWebhook(1L, 42L, 99L)).thenReturn(Optional.empty()); // Deleted externally
            when(webhookClient.listGroupWebhooks(1L, 42L)).thenReturn(List.of());
            when(webhookClient.registerGroupWebhook(eq(1L), eq(42L), any(WebhookConfig.class))).thenReturn(
                new WebhookInfo(100L, EXTERNAL_URL + "/gitlab")
            );
            when(workspaceRepository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));

            WebhookSetupResult result = webhookService.registerWebhook(workspace);

            assertThat(result.registered()).isTrue();
            assertThat(result.webhookId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("should return failed on 403 (insufficient permissions)")
        void shouldReturnFailedOn403() {
            when(webhookClientProvider.getIfAvailable()).thenReturn(webhookClient);
            when(webhookClient.lookupGroup(1L, "my-org")).thenReturn(new GroupInfo(42L, "My Org", "my-org"));
            when(webhookClient.listGroupWebhooks(1L, 42L)).thenReturn(List.of());
            when(webhookClient.registerGroupWebhook(eq(1L), eq(42L), any(WebhookConfig.class))).thenThrow(
                WebClientResponseException.create(403, "Forbidden", null, null, null)
            );
            when(workspaceRepository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));

            WebhookSetupResult result = webhookService.registerWebhook(workspace);

            assertThat(result.registered()).isFalse();
            assertThat(result.failureReason()).contains("Insufficient permissions");
        }

        @Test
        @DisplayName("should skip when properties not configured")
        void shouldSkipWhenNotConfigured() {
            WebhookProperties unconfigured = new WebhookProperties("", "", 7, 90);
            var service = new GitLabWebhookService(
                webhookClientProvider,
                rotationClientProvider,
                tokenServiceProvider,
                unconfigured,
                workspaceRepository
            );

            WebhookSetupResult result = service.registerWebhook(workspace);

            assertThat(result.registered()).isFalse();
            assertThat(result.failureReason()).contains("not configured");
        }
    }

    @Nested
    @DisplayName("rotateTokenIfNeeded")
    class RotateTokenIfNeeded {

        @Test
        @DisplayName("should skip for non-GitLab workspace")
        void shouldSkipForNonGitLab() {
            workspace.setGitProviderMode(Workspace.GitProviderMode.PAT_ORG);

            webhookService.rotateTokenIfNeeded(workspace);

            verify(rotationClientProvider, never()).getIfAvailable();
        }

        @Test
        @DisplayName("should skip when rotation client unavailable")
        void shouldSkipWhenUnavailable() {
            when(rotationClientProvider.getIfAvailable()).thenReturn(null);

            webhookService.rotateTokenIfNeeded(workspace);
            // No exception, no rotation
        }

        @Test
        @DisplayName("should skip when token has no expiry")
        void shouldSkipNoExpiry() {
            when(rotationClientProvider.getIfAvailable()).thenReturn(rotationClient);
            when(rotationClient.getTokenInfo(1L)).thenReturn(new TokenInfo(1L, "test", null));

            webhookService.rotateTokenIfNeeded(workspace);

            verify(rotationClient, never()).rotateToken(anyLong(), any());
        }

        @Test
        @DisplayName("should skip when token not expiring soon")
        void shouldSkipNotExpiringSoon() {
            when(rotationClientProvider.getIfAvailable()).thenReturn(rotationClient);
            when(rotationClient.getTokenInfo(1L)).thenReturn(new TokenInfo(1L, "test", LocalDate.now().plusDays(30)));

            webhookService.rotateTokenIfNeeded(workspace);

            verify(rotationClient, never()).rotateToken(anyLong(), any());
        }

        @Test
        @DisplayName("should rotate when token expiring within threshold")
        void shouldRotateWhenExpiringSoon() {
            when(rotationClientProvider.getIfAvailable()).thenReturn(rotationClient);
            when(tokenServiceProvider.getIfAvailable()).thenReturn(tokenService);
            when(rotationClient.getTokenInfo(1L)).thenReturn(new TokenInfo(1L, "test", LocalDate.now().plusDays(3)));
            when(rotationClient.rotateToken(eq(1L), any(LocalDate.class))).thenReturn(
                new RotatedToken("glpat-new-token", LocalDate.now().plusDays(90))
            );
            when(workspaceRepository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));

            webhookService.rotateTokenIfNeeded(workspace);

            assertThat(workspace.getPersonalAccessToken()).isEqualTo("glpat-new-token");
            verify(workspaceRepository).save(workspace);
            verify(tokenService).invalidateCache(1L);
        }

        @Test
        @DisplayName("should continue on rotation error")
        void shouldContinueOnError() {
            when(rotationClientProvider.getIfAvailable()).thenReturn(rotationClient);
            when(rotationClient.getTokenInfo(1L)).thenThrow(new IllegalStateException("Connection refused"));

            // Should not throw
            webhookService.rotateTokenIfNeeded(workspace);
        }
    }

    @Nested
    @DisplayName("deregisterWebhook")
    class DeregisterWebhook {

        @Test
        @DisplayName("should skip when no webhook registered")
        void shouldSkipWhenNoWebhook() {
            webhookService.deregisterWebhook(workspace);

            verify(webhookClientProvider, never()).getIfAvailable();
        }

        @Test
        @DisplayName("should deregister and clear fields")
        void shouldDeregisterAndClearFields() {
            workspace.setGitlabGroupId(42L);
            workspace.setGitlabWebhookId(99L);

            when(webhookClientProvider.getIfAvailable()).thenReturn(webhookClient);
            when(workspaceRepository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));

            webhookService.deregisterWebhook(workspace);

            verify(webhookClient).deregisterGroupWebhook(1L, 42L, 99L);
            assertThat(workspace.getGitlabWebhookId()).isNull();
            assertThat(workspace.getGitlabGroupId()).isNull();
        }

        @Test
        @DisplayName("should clear fields even on deregistration error")
        void shouldClearFieldsOnError() {
            workspace.setGitlabGroupId(42L);
            workspace.setGitlabWebhookId(99L);

            when(webhookClientProvider.getIfAvailable()).thenReturn(webhookClient);
            when(workspaceRepository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));

            // Simulate API error
            org.mockito.Mockito.doThrow(new RuntimeException("API error"))
                .when(webhookClient)
                .deregisterGroupWebhook(1L, 42L, 99L);

            webhookService.deregisterWebhook(workspace);

            // Fields should still be cleared (best-effort)
            assertThat(workspace.getGitlabWebhookId()).isNull();
            assertThat(workspace.getGitlabGroupId()).isNull();
        }

        @Test
        @DisplayName("should clear fields when client unavailable")
        void shouldClearFieldsWhenClientUnavailable() {
            workspace.setGitlabGroupId(42L);
            workspace.setGitlabWebhookId(99L);

            when(webhookClientProvider.getIfAvailable()).thenReturn(null);
            when(workspaceRepository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));

            webhookService.deregisterWebhook(workspace);

            assertThat(workspace.getGitlabWebhookId()).isNull();
            assertThat(workspace.getGitlabGroupId()).isNull();
        }
    }

    @Nested
    @DisplayName("deregisterWebhookByWorkspaceId")
    class DeregisterWebhookByWorkspaceId {

        @Test
        @DisplayName("should deregister webhook for existing workspace")
        void shouldDeregisterForExistingWorkspace() {
            workspace.setGitlabGroupId(42L);
            workspace.setGitlabWebhookId(99L);

            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(webhookClientProvider.getIfAvailable()).thenReturn(webhookClient);
            when(workspaceRepository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));

            webhookService.deregisterWebhookByWorkspaceId(1L);

            verify(webhookClient).deregisterGroupWebhook(1L, 42L, 99L);
            assertThat(workspace.getGitlabWebhookId()).isNull();
            assertThat(workspace.getGitlabGroupId()).isNull();
        }

        @Test
        @DisplayName("should do nothing when workspace not found")
        void shouldDoNothingWhenWorkspaceNotFound() {
            when(workspaceRepository.findById(999L)).thenReturn(Optional.empty());

            webhookService.deregisterWebhookByWorkspaceId(999L);

            verify(webhookClientProvider, never()).getIfAvailable();
        }
    }
}
