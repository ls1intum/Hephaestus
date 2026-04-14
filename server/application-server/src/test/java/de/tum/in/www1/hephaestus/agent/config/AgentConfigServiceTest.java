package de.tum.in.www1.hephaestus.agent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.agent.AgentType;
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
            Set.of()
        );
    }

    @Test
    @DisplayName("should create Claude config with model version")
    void shouldCreateClaudeConfigWithModelVersion() {
        when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "claude-reviewer")).thenReturn(false);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new CreateAgentConfigRequestDTO(
            "claude-reviewer",
            true,
            AgentType.CLAUDE_CODE,
            "claude-sonnet-4-20250514",
            "2026-03-17",
            null,
            LlmProvider.ANTHROPIC,
            300,
            2,
            false,
            CredentialMode.PROXY
        );

        AgentConfig result = agentConfigService.createConfig(workspaceContext, request);

        assertThat(result.getName()).isEqualTo("claude-reviewer");
        assertThat(result.getAgentType()).isEqualTo(AgentType.CLAUDE_CODE);
        assertThat(result.getModelVersion()).isEqualTo("2026-03-17");
        assertThat(result.getCredentialMode()).isEqualTo(CredentialMode.PROXY);
    }

    @Test
    @DisplayName("should accept Pi with any provider")
    void shouldAcceptPiWithAnyProvider() {
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        for (LlmProvider provider : LlmProvider.values()) {
            String name = "pi-" + provider.name().toLowerCase();
            when(agentConfigRepository.existsByWorkspaceIdAndName(1L, name)).thenReturn(false);

            var request = new CreateAgentConfigRequestDTO(
                name,
                true,
                AgentType.PI,
                "gpt-5.4-mini",
                null,
                "sk-test",
                provider,
                300,
                2,
                true,
                CredentialMode.API_KEY
            );

            AgentConfig result = agentConfigService.createConfig(workspaceContext, request);
            assertThat(result.getLlmProvider()).isEqualTo(provider);
            assertThat(result.getAgentType()).isEqualTo(AgentType.PI);
        }
    }

    @Test
    @DisplayName("should reject Claude config with non-Anthropic provider")
    void shouldRejectClaudeConfigWithNonAnthropicProvider() {
        var request = new CreateAgentConfigRequestDTO(
            "bad-claude",
            true,
            AgentType.CLAUDE_CODE,
            null,
            null,
            null,
            LlmProvider.OPENAI,
            null,
            null,
            null,
            null
        );

        assertThatThrownBy(() -> agentConfigService.createConfig(workspaceContext, request))
            .isInstanceOf(AgentConfigProviderMismatchException.class)
            .hasMessageContaining("CLAUDE_CODE")
            .hasMessageContaining("ANTHROPIC");
    }

    @Test
    @DisplayName("should reject direct auth without internet access")
    void shouldRejectDirectAuthWithoutInternetAccess() {
        when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "claude-direct")).thenReturn(false);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

        var request = new CreateAgentConfigRequestDTO(
            "claude-direct",
            true,
            AgentType.CLAUDE_CODE,
            null,
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
            .hasMessageContaining("internet");
    }

    @Test
    @DisplayName("should reject direct auth without credential")
    void shouldRejectDirectAuthWithoutCredential() {
        when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "pi-direct")).thenReturn(false);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

        var request = new CreateAgentConfigRequestDTO(
            "pi-direct",
            true,
            AgentType.PI,
            "gpt-5.4-mini",
            null,
            null,
            LlmProvider.OPENAI,
            null,
            null,
            true,
            CredentialMode.API_KEY
        );

        assertThatThrownBy(() -> agentConfigService.createConfig(workspaceContext, request))
            .isInstanceOf(AgentConfigMissingCredentialException.class)
            .hasMessageContaining("credential");
    }

    @Test
    @DisplayName("should reject Pi OAuth credential mode")
    void shouldRejectPiOAuthCredentialMode() {
        when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "pi-oauth")).thenReturn(false);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

        var request = new CreateAgentConfigRequestDTO(
            "pi-oauth",
            true,
            AgentType.PI,
            "gpt-5.4-mini",
            null,
            "oauth-token",
            LlmProvider.OPENAI,
            300,
            2,
            true,
            CredentialMode.OAUTH
        );

        assertThatThrownBy(() -> agentConfigService.createConfig(workspaceContext, request))
            .isInstanceOf(AgentConfigCredentialModeException.class)
            .hasMessageContaining("Pi does not support OAuth");
    }

    @Test
    @DisplayName("should rename config and keep secret when update omits api key")
    void shouldRenameConfigAndKeepSecretWhenUpdateOmitsApiKey() {
        AgentConfig existing = new AgentConfig();
        existing.setId(10L);
        existing.setWorkspace(workspace);
        existing.setName("old-name");
        existing.setAgentType(AgentType.CLAUDE_CODE);
        existing.setLlmProvider(LlmProvider.ANTHROPIC);
        existing.setLlmApiKey("sk-existing-secret");

        when(agentConfigRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(existing));
        when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "new-name")).thenReturn(false);
        when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new UpdateAgentConfigRequestDTO(
            "new-name",
            null,
            AgentType.PI,
            "gpt-4o",
            null,
            "2026-03-17",
            null,
            null,
            null,
            LlmProvider.OPENAI,
            120,
            null,
            true,
            null
        );

        AgentConfig result = agentConfigService.updateConfig(workspaceContext, 10L, request);

        assertThat(result.getName()).isEqualTo("new-name");
        assertThat(result.getAgentType()).isEqualTo(AgentType.PI);
        assertThat(result.getModelVersion()).isEqualTo("2026-03-17");
        assertThat(result.getLlmApiKey()).isEqualTo("sk-existing-secret");
        assertThat(result.getTimeoutSeconds()).isEqualTo(120);
    }

    @Test
    @DisplayName("should clear api key when requested")
    void shouldClearApiKeyWhenRequested() {
        AgentConfig existing = new AgentConfig();
        existing.setId(10L);
        existing.setWorkspace(workspace);
        existing.setName("claude-reviewer");
        existing.setAgentType(AgentType.CLAUDE_CODE);
        existing.setLlmProvider(LlmProvider.ANTHROPIC);
        existing.setCredentialMode(CredentialMode.PROXY);
        existing.setLlmApiKey("sk-existing-secret");

        when(agentConfigRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(existing));
        when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new UpdateAgentConfigRequestDTO(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            true,
            null,
            null,
            null,
            null,
            null
        );

        AgentConfig result = agentConfigService.updateConfig(workspaceContext, 10L, request);

        assertThat(result.getLlmApiKey()).isNull();
    }

    @Test
    @DisplayName("should clear model fields when requested")
    void shouldClearModelFieldsWhenRequested() {
        AgentConfig existing = new AgentConfig();
        existing.setId(10L);
        existing.setWorkspace(workspace);
        existing.setName("pi-reviewer");
        existing.setAgentType(AgentType.PI);
        existing.setLlmProvider(LlmProvider.OPENAI);
        existing.setModelName("gpt-5.4-mini");
        existing.setModelVersion("2026-03-17");

        when(agentConfigRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(existing));
        when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new UpdateAgentConfigRequestDTO(
            null,
            null,
            null,
            null,
            true,
            null,
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        AgentConfig result = agentConfigService.updateConfig(workspaceContext, 10L, request);

        assertThat(result.getModelName()).isNull();
        assertThat(result.getModelVersion()).isNull();
    }

    @Test
    @DisplayName("should reject duplicate name during rename")
    void shouldRejectDuplicateNameDuringRename() {
        AgentConfig existing = new AgentConfig();
        existing.setId(10L);
        existing.setWorkspace(workspace);
        existing.setName("old-name");
        existing.setAgentType(AgentType.CLAUDE_CODE);
        existing.setLlmProvider(LlmProvider.ANTHROPIC);

        when(agentConfigRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(existing));
        when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "new-name")).thenReturn(true);

        var request = new UpdateAgentConfigRequestDTO(
            "new-name",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        assertThatThrownBy(() -> agentConfigService.updateConfig(workspaceContext, 10L, request))
            .isInstanceOf(AgentConfigNameConflictException.class)
            .hasMessageContaining("new-name");
    }

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
    @DisplayName("should throw not found when config is missing")
    void shouldThrowNotFoundWhenConfigIsMissing() {
        when(agentConfigRepository.findByIdAndWorkspaceId(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentConfigService.getConfig(workspaceContext, 999L)).isInstanceOf(
            EntityNotFoundException.class
        );
    }

    @Test
    @DisplayName("should delete config when no active jobs exist")
    void shouldDeleteConfigWhenNoActiveJobsExist() {
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
}
