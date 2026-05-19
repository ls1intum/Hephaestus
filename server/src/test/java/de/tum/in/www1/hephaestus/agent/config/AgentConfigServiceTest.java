package de.tum.in.www1.hephaestus.agent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.job.AgentJobRepository;
import de.tum.in.www1.hephaestus.agent.job.AgentJobStatus;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@DisplayName("AgentConfigService")
class AgentConfigServiceTest extends BaseUnitTest {

    @Mock
    private AgentConfigRepository agentConfigRepository;

    @Mock
    private AgentJobRepository agentJobRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @InjectMocks
    private AgentConfigService agentConfigService;

    private Workspace workspace;
    private WorkspaceContext workspaceContext;

    @BeforeEach
    void setUp() {
        workspace = new Workspace();
        workspace.setId(1L);
        workspace.setWorkspaceSlug("test-workspace");
        workspaceContext = new WorkspaceContext(
            1L,
            "test-workspace",
            "Test Workspace",
            AccountType.ORG,
            null,
            false,
            false,
            Set.of()
        );
    }

    @Nested
    @DisplayName("Credential mode validation")
    class CredentialModeValidation {

        @Test
        @DisplayName("should reject API_KEY mode without internet access")
        void shouldRejectApiKeyWithoutInternet() {
            when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "test")).thenReturn(false);
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

            var request = new CreateAgentConfigRequestDTO(
                "test",
                true,
                null,
                "sk-key",
                LlmProvider.ANTHROPIC,
                null,
                null,
                false,
                CredentialMode.API_KEY
            );

            assertThatThrownBy(() -> agentConfigService.createConfig(workspaceContext, request))
                .isInstanceOf(AgentConfigCredentialModeException.class)
                .hasMessageContaining("API_KEY")
                .hasMessageContaining("internet");
        }

        @Test
        @DisplayName("should reject OAUTH mode without internet access")
        void shouldRejectOauthWithoutInternet() {
            when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "test")).thenReturn(false);
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

            var request = new CreateAgentConfigRequestDTO(
                "test",
                true,
                null,
                "oauth-token",
                LlmProvider.ANTHROPIC,
                null,
                null,
                false,
                CredentialMode.OAUTH
            );

            assertThatThrownBy(() -> agentConfigService.createConfig(workspaceContext, request))
                .isInstanceOf(AgentConfigCredentialModeException.class)
                .hasMessageContaining("OAUTH")
                .hasMessageContaining("internet");
        }

        @Test
        @DisplayName("should accept API_KEY mode with internet access")
        void shouldAcceptApiKeyWithInternet() {
            when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "test")).thenReturn(false);
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = new CreateAgentConfigRequestDTO(
                "test",
                true,
                null,
                "sk-key",
                LlmProvider.ANTHROPIC,
                null,
                null,
                true,
                CredentialMode.API_KEY
            );

            AgentConfig result = agentConfigService.createConfig(workspaceContext, request);
            assertThat(result.getCredentialMode()).isEqualTo(CredentialMode.API_KEY);
            assertThat(result.isAllowInternet()).isTrue();
        }

        @Test
        @DisplayName("should accept PROXY mode without internet access")
        void shouldAcceptProxyWithoutInternet() {
            when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "test")).thenReturn(false);
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = new CreateAgentConfigRequestDTO(
                "test",
                true,
                null,
                null,
                LlmProvider.ANTHROPIC,
                null,
                null,
                false,
                CredentialMode.PROXY
            );

            AgentConfig result = agentConfigService.createConfig(workspaceContext, request);
            assertThat(result.getCredentialMode()).isEqualTo(CredentialMode.PROXY);
            assertThat(result.isAllowInternet()).isFalse();
        }

        @Test
        @DisplayName("should default to PROXY mode when credentialMode is null")
        void shouldDefaultToProxy() {
            when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "test")).thenReturn(false);
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = new CreateAgentConfigRequestDTO(
                "test",
                true,
                null,
                null,
                LlmProvider.ANTHROPIC,
                null,
                null,
                null,
                null
            );

            AgentConfig result = agentConfigService.createConfig(workspaceContext, request);
            assertThat(result.getCredentialMode()).isEqualTo(CredentialMode.PROXY);
        }

        @Test
        @DisplayName("should reject credential mode change on update when internet is disabled")
        void shouldRejectCredentialModeChangeOnUpdate() {
            AgentConfig existing = new AgentConfig();
            existing.setId(10L);
            existing.setWorkspace(workspace);
            existing.setLlmProvider(LlmProvider.ANTHROPIC);
            existing.setAllowInternet(false);
            existing.setCredentialMode(CredentialMode.PROXY);

            when(agentConfigRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(existing));

            var request = new UpdateAgentConfigRequestDTO(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                CredentialMode.API_KEY
            );

            assertThatThrownBy(() -> agentConfigService.updateConfig(workspaceContext, 10L, request)).isInstanceOf(
                AgentConfigCredentialModeException.class
            );
        }

        @Test
        @DisplayName("should reject disabling internet on existing API_KEY config")
        void shouldRejectDisablingInternetOnApiKeyConfig() {
            AgentConfig existing = new AgentConfig();
            existing.setId(10L);
            existing.setWorkspace(workspace);
            existing.setLlmProvider(LlmProvider.ANTHROPIC);
            existing.setAllowInternet(true);
            existing.setCredentialMode(CredentialMode.API_KEY);

            when(agentConfigRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(existing));

            var request = new UpdateAgentConfigRequestDTO(null, null, null, null, null, null, false, null);

            assertThatThrownBy(() -> agentConfigService.updateConfig(workspaceContext, 10L, request)).isInstanceOf(
                AgentConfigCredentialModeException.class
            );
        }
    }

    @Nested
    @DisplayName("Read")
    class Read {

        @Test
        @DisplayName("should return configs for workspace")
        void shouldReturnConfigsForWorkspace() {
            var configs = java.util.List.of(new AgentConfig(), new AgentConfig());
            when(agentConfigRepository.findByWorkspaceId(1L)).thenReturn(configs);

            var result = agentConfigService.getConfigs(workspaceContext);

            assertThat(result).hasSize(2);
            verify(agentConfigRepository).findByWorkspaceId(1L);
        }

        @Test
        @DisplayName("should return single config by ID")
        void shouldReturnConfigById() {
            AgentConfig config = new AgentConfig();
            config.setId(10L);
            when(agentConfigRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(config));

            var result = agentConfigService.getConfig(workspaceContext, 10L);

            assertThat(result.getId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("should throw not found for non-existent config")
        void shouldThrowNotFoundForNonExistentConfig() {
            when(agentConfigRepository.findByIdAndWorkspaceId(999L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> agentConfigService.getConfig(workspaceContext, 999L)).isInstanceOf(
                EntityNotFoundException.class
            );
        }
    }

    @Nested
    @DisplayName("Create")
    class Create {

        @Test
        @DisplayName("should create new config with all fields")
        void shouldCreateNewConfig() {
            when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "my-agent")).thenReturn(false);
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = new CreateAgentConfigRequestDTO(
                "my-agent",
                true,
                "claude-sonnet-4-20250514",
                "sk-abc",
                LlmProvider.ANTHROPIC,
                300,
                2,
                true,
                null
            );

            AgentConfig result = agentConfigService.createConfig(workspaceContext, request);

            assertThat(result.getWorkspace()).isEqualTo(workspace);
            assertThat(result.getName()).isEqualTo("my-agent");
            assertThat(result.isEnabled()).isTrue();
            assertThat(result.getModelName()).isEqualTo("claude-sonnet-4-20250514");
            assertThat(result.getLlmApiKey()).isEqualTo("sk-abc");
            assertThat(result.getTimeoutSeconds()).isEqualTo(300);
            assertThat(result.getMaxConcurrentJobs()).isEqualTo(2);
            assertThat(result.isAllowInternet()).isTrue();
        }

        @Test
        @DisplayName("should reject duplicate name within workspace")
        void shouldRejectDuplicateName() {
            when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "my-agent")).thenReturn(true);

            var request = new CreateAgentConfigRequestDTO(
                "my-agent",
                true,
                null,
                null,
                LlmProvider.ANTHROPIC,
                null,
                null,
                null,
                null
            );

            assertThatThrownBy(() -> agentConfigService.createConfig(workspaceContext, request))
                .isInstanceOf(AgentConfigNameConflictException.class)
                .hasMessageContaining("my-agent");
        }
    }

    @Nested
    @DisplayName("Update")
    class Update {

        @Test
        @DisplayName("should update existing config and preserve API key when null")
        void shouldUpdateExistingConfigAndPreserveApiKeyWhenNull() {
            AgentConfig existing = new AgentConfig();
            existing.setId(10L);
            existing.setWorkspace(workspace);
            existing.setLlmProvider(LlmProvider.ANTHROPIC);
            existing.setLlmApiKey("sk-existing-secret");

            when(agentConfigRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(existing));
            when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = new UpdateAgentConfigRequestDTO(
                null,
                "gpt-4o",
                null,
                LlmProvider.OPENAI,
                120,
                null,
                null,
                null
            );

            AgentConfig result = agentConfigService.updateConfig(workspaceContext, 10L, request);

            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getLlmApiKey()).isEqualTo("sk-existing-secret");
            assertThat(result.getTimeoutSeconds()).isEqualTo(120);
        }

        @Test
        @DisplayName("should throw not found when updating non-existent config")
        void shouldThrowNotFoundWhenUpdatingNonExistentConfig() {
            when(agentConfigRepository.findByIdAndWorkspaceId(999L, 1L)).thenReturn(Optional.empty());

            var request = new UpdateAgentConfigRequestDTO(null, null, null, null, null, null, null, null);

            assertThatThrownBy(() -> agentConfigService.updateConfig(workspaceContext, 999L, request)).isInstanceOf(
                EntityNotFoundException.class
            );
        }
    }

    @Nested
    @DisplayName("Delete")
    class Delete {

        @Test
        @DisplayName("should delete config when no active jobs")
        void shouldDeleteConfigWhenNoActiveJobs() {
            AgentConfig config = new AgentConfig();
            config.setId(10L);
            config.setWorkspace(workspace);

            when(agentConfigRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(config));
            when(
                agentJobRepository.countByConfigIdAndStatusIn(
                    eq(10L),
                    eq(Set.of(AgentJobStatus.QUEUED, AgentJobStatus.RUNNING))
                )
            ).thenReturn(0L);

            agentConfigService.deleteConfig(workspaceContext, 10L);

            verify(agentConfigRepository).delete(config);
        }

        @Test
        @DisplayName("should reject delete when active jobs exist")
        void shouldRejectDeleteWhenActiveJobsExist() {
            AgentConfig config = new AgentConfig();
            config.setId(10L);
            config.setWorkspace(workspace);

            when(agentConfigRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(config));
            when(
                agentJobRepository.countByConfigIdAndStatusIn(
                    eq(10L),
                    eq(Set.of(AgentJobStatus.QUEUED, AgentJobStatus.RUNNING))
                )
            ).thenReturn(2L);

            assertThatThrownBy(() -> agentConfigService.deleteConfig(workspaceContext, 10L))
                .isInstanceOf(AgentConfigHasActiveJobsException.class)
                .hasMessageContaining("2 active job(s)");

            verify(agentConfigRepository, never()).delete(any());
        }

        @Test
        @DisplayName("should throw not found when deleting non-existent config")
        void shouldThrowNotFoundWhenDeletingNonExistentConfig() {
            when(agentConfigRepository.findByIdAndWorkspaceId(999L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> agentConfigService.deleteConfig(workspaceContext, 999L)).isInstanceOf(
                EntityNotFoundException.class
            );
        }
    }
}
