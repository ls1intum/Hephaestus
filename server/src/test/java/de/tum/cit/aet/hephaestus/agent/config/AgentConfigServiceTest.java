package de.tum.cit.aet.hephaestus.agent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

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
    class CredentialModeValidation {

        @Test
        void shouldRejectApiKeyWithoutInternet() {
            when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "test")).thenReturn(false);
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

            var request = CreateAgentConfigRequestDTO.builder()
                .name("test")
                .enabled(true)
                .llmApiKey("sk-key")
                .llmProvider(LlmProvider.ANTHROPIC)
                .allowInternet(false)
                .credentialMode(CredentialMode.API_KEY)
                .build();

            assertThatThrownBy(() -> agentConfigService.createConfig(workspaceContext, request))
                .isInstanceOf(AgentConfigCredentialModeException.class)
                .hasMessageContaining("API_KEY")
                .hasMessageContaining("internet");
        }

        @Test
        void shouldRejectOauthWithoutInternet() {
            when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "test")).thenReturn(false);
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

            var request = CreateAgentConfigRequestDTO.builder()
                .name("test")
                .enabled(true)
                .llmApiKey("oauth-token")
                .llmProvider(LlmProvider.ANTHROPIC)
                .allowInternet(false)
                .credentialMode(CredentialMode.OAUTH)
                .build();

            assertThatThrownBy(() -> agentConfigService.createConfig(workspaceContext, request))
                .isInstanceOf(AgentConfigCredentialModeException.class)
                .hasMessageContaining("OAUTH")
                .hasMessageContaining("internet");
        }

        @Test
        void shouldAcceptApiKeyWithInternet() {
            when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "test")).thenReturn(false);
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = CreateAgentConfigRequestDTO.builder()
                .name("test")
                .enabled(true)
                .llmApiKey("sk-key")
                .llmProvider(LlmProvider.ANTHROPIC)
                .allowInternet(true)
                .credentialMode(CredentialMode.API_KEY)
                .build();

            AgentConfig result = agentConfigService.createConfig(workspaceContext, request);
            assertThat(result.getCredentialMode()).isEqualTo(CredentialMode.API_KEY);
            assertThat(result.isAllowInternet()).isTrue();
        }

        @Test
        void shouldRejectDirectModeWithoutCredential() {
            when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "test")).thenReturn(false);
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

            var request = CreateAgentConfigRequestDTO.builder()
                .name("test")
                .enabled(true)
                .llmProvider(LlmProvider.ANTHROPIC)
                .allowInternet(true)
                .credentialMode(CredentialMode.API_KEY)
                .build();

            assertThatThrownBy(() -> agentConfigService.createConfig(workspaceContext, request))
                .isInstanceOf(AgentConfigCredentialModeException.class)
                .hasMessageContaining("API key");
        }

        @Test
        void shouldAcceptProxyWithoutInternet() {
            when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "test")).thenReturn(false);
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = CreateAgentConfigRequestDTO.builder()
                .name("test")
                .enabled(true)
                .llmProvider(LlmProvider.ANTHROPIC)
                .allowInternet(false)
                .credentialMode(CredentialMode.PROXY)
                .build();

            AgentConfig result = agentConfigService.createConfig(workspaceContext, request);
            assertThat(result.getCredentialMode()).isEqualTo(CredentialMode.PROXY);
            assertThat(result.isAllowInternet()).isFalse();
        }

        @Test
        void shouldDefaultToProxy() {
            when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "test")).thenReturn(false);
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = CreateAgentConfigRequestDTO.builder()
                .name("test")
                .enabled(true)
                .llmProvider(LlmProvider.ANTHROPIC)
                .build();

            AgentConfig result = agentConfigService.createConfig(workspaceContext, request);
            assertThat(result.getCredentialMode()).isEqualTo(CredentialMode.PROXY);
        }

        @Test
        void shouldRejectCredentialModeChangeOnUpdate() {
            AgentConfig existing = new AgentConfig();
            existing.setId(10L);
            existing.setWorkspace(workspace);
            existing.setLlmProvider(LlmProvider.ANTHROPIC);
            existing.setAllowInternet(false);
            existing.setCredentialMode(CredentialMode.PROXY);

            when(agentConfigRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(existing));

            var request = UpdateAgentConfigRequestDTO.builder().credentialMode(CredentialMode.API_KEY).build();

            assertThatThrownBy(() -> agentConfigService.updateConfig(workspaceContext, 10L, request)).isInstanceOf(
                AgentConfigCredentialModeException.class
            );
        }

        @Test
        void shouldRejectDisablingInternetOnApiKeyConfig() {
            AgentConfig existing = new AgentConfig();
            existing.setId(10L);
            existing.setWorkspace(workspace);
            existing.setLlmProvider(LlmProvider.ANTHROPIC);
            existing.setAllowInternet(true);
            existing.setCredentialMode(CredentialMode.API_KEY);

            when(agentConfigRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(existing));

            var request = UpdateAgentConfigRequestDTO.builder().allowInternet(false).build();

            assertThatThrownBy(() -> agentConfigService.updateConfig(workspaceContext, 10L, request)).isInstanceOf(
                AgentConfigCredentialModeException.class
            );
        }
    }

    @Nested
    class Read {

        @Test
        void shouldReturnConfigsForWorkspace() {
            var configs = java.util.List.of(new AgentConfig(), new AgentConfig());
            when(agentConfigRepository.findByWorkspaceId(1L)).thenReturn(configs);

            var result = agentConfigService.getConfigs(workspaceContext);

            assertThat(result).hasSize(2);
            verify(agentConfigRepository).findByWorkspaceId(1L);
        }

        @Test
        void shouldReturnConfigById() {
            AgentConfig config = new AgentConfig();
            config.setId(10L);
            when(agentConfigRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(config));

            var result = agentConfigService.getConfig(workspaceContext, 10L);

            assertThat(result.getId()).isEqualTo(10L);
        }

        @Test
        void shouldThrowNotFoundForNonExistentConfig() {
            when(agentConfigRepository.findByIdAndWorkspaceId(999L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> agentConfigService.getConfig(workspaceContext, 999L)).isInstanceOf(
                EntityNotFoundException.class
            );
        }
    }

    @Nested
    class Create {

        @Test
        void shouldCreateNewConfig() {
            when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "my-agent")).thenReturn(false);
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = CreateAgentConfigRequestDTO.builder()
                .name("my-agent")
                .enabled(true)
                .modelName("claude-sonnet-4-20250514")
                .llmApiKey("sk-abc")
                .llmProvider(LlmProvider.ANTHROPIC)
                .timeoutSeconds(300)
                .maxConcurrentJobs(2)
                .allowInternet(true)
                .build();

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
        void shouldRejectDuplicateName() {
            when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "my-agent")).thenReturn(true);

            var request = CreateAgentConfigRequestDTO.builder()
                .name("my-agent")
                .enabled(true)
                .llmProvider(LlmProvider.ANTHROPIC)
                .build();

            assertThatThrownBy(() -> agentConfigService.createConfig(workspaceContext, request))
                .isInstanceOf(AgentConfigNameConflictException.class)
                .hasMessageContaining("my-agent");
        }
    }

    @Nested
    class Update {

        @Test
        void shouldUpdateExistingConfigAndPreserveApiKeyWhenNull() {
            AgentConfig existing = new AgentConfig();
            existing.setId(10L);
            existing.setWorkspace(workspace);
            existing.setLlmProvider(LlmProvider.ANTHROPIC);
            existing.setLlmApiKey("sk-existing-secret");

            when(agentConfigRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(existing));
            when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = UpdateAgentConfigRequestDTO.builder()
                .modelName("gpt-4o")
                .llmProvider(LlmProvider.OPENAI)
                .timeoutSeconds(120)
                .build();

            AgentConfig result = agentConfigService.updateConfig(workspaceContext, 10L, request);

            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getLlmApiKey()).isEqualTo("sk-existing-secret");
            assertThat(result.getTimeoutSeconds()).isEqualTo(120);
        }

        @Test
        void shouldClearApiKeyWhenClearFlagSet() {
            AgentConfig existing = new AgentConfig();
            existing.setId(10L);
            existing.setWorkspace(workspace);
            existing.setLlmProvider(LlmProvider.ANTHROPIC);
            existing.setLlmApiKey("sk-existing-secret");

            when(agentConfigRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(existing));
            when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // clearLlmApiKey wins over any provided key.
            var request = UpdateAgentConfigRequestDTO.builder().llmApiKey("sk-ignored").clearLlmApiKey(true).build();

            AgentConfig result = agentConfigService.updateConfig(workspaceContext, 10L, request);

            assertThat(result.getLlmApiKey()).isNull();
        }

        @Test
        void shouldThrowNotFoundWhenUpdatingNonExistentConfig() {
            when(agentConfigRepository.findByIdAndWorkspaceId(999L, 1L)).thenReturn(Optional.empty());

            var request = UpdateAgentConfigRequestDTO.builder().build();

            assertThatThrownBy(() -> agentConfigService.updateConfig(workspaceContext, 999L, request)).isInstanceOf(
                EntityNotFoundException.class
            );
        }
    }

    @Nested
    class Delete {

        @Test
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
            // Workspace has no bound pointers → delete proceeds.
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

            agentConfigService.deleteConfig(workspaceContext, 10L);

            verify(agentConfigRepository).delete(config);
        }

        @Test
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
        void shouldRejectDeleteWhenConfigBound() {
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
            workspace.setPracticeConfigId(10L);
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

            assertThatThrownBy(() -> agentConfigService.deleteConfig(workspaceContext, 10L)).isInstanceOf(
                AgentConfigBoundException.class
            );

            verify(agentConfigRepository, never()).delete(any());
        }

        @Test
        void shouldThrowNotFoundWhenDeletingNonExistentConfig() {
            when(agentConfigRepository.findByIdAndWorkspaceId(999L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> agentConfigService.deleteConfig(workspaceContext, 999L)).isInstanceOf(
                EntityNotFoundException.class
            );
        }
    }
}
