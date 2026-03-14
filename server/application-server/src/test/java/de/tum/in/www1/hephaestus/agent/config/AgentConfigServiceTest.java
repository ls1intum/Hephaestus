package de.tum.in.www1.hephaestus.agent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.agent.AgentType;
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
            Set.of()
        );
    }

    @Nested
    @DisplayName("Provider validation")
    class ProviderValidation {

        @Test
        void shouldAcceptClaudeCodeWithAnthropic() {
            when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "test-config")).thenReturn(false);
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = new CreateAgentConfigRequestDTO(
                "test-config",
                true,
                AgentType.CLAUDE_CODE,
                "claude-sonnet-4-20250514",
                "sk-test",
                LlmProvider.ANTHROPIC,
                null,
                null,
                null
            );

            AgentConfig result = agentConfigService.createConfig(workspaceContext, request);
            assertThat(result.getAgentType()).isEqualTo(AgentType.CLAUDE_CODE);
            assertThat(result.getLlmProvider()).isEqualTo(LlmProvider.ANTHROPIC);
        }

        @Test
        void shouldRejectClaudeCodeWithOpenai() {
            var request = new CreateAgentConfigRequestDTO(
                "test-config",
                true,
                AgentType.CLAUDE_CODE,
                null,
                null,
                LlmProvider.OPENAI,
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
        void shouldAcceptCodexWithOpenai() {
            when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "codex-config")).thenReturn(false);
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = new CreateAgentConfigRequestDTO(
                "codex-config",
                true,
                AgentType.CODEX,
                "o3",
                null,
                LlmProvider.OPENAI,
                null,
                null,
                null
            );

            AgentConfig result = agentConfigService.createConfig(workspaceContext, request);
            assertThat(result.getAgentType()).isEqualTo(AgentType.CODEX);
        }

        @Test
        void shouldRejectCodexWithAnthropic() {
            var request = new CreateAgentConfigRequestDTO(
                "codex-config",
                true,
                AgentType.CODEX,
                null,
                null,
                LlmProvider.ANTHROPIC,
                null,
                null,
                null
            );

            assertThatThrownBy(() -> agentConfigService.createConfig(workspaceContext, request))
                .isInstanceOf(AgentConfigProviderMismatchException.class)
                .hasMessageContaining("CODEX")
                .hasMessageContaining("OPENAI");
        }

        @Test
        void shouldAcceptOpencodeWithAnyProvider() {
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            for (LlmProvider provider : LlmProvider.values()) {
                String name = "opencode-" + provider.name().toLowerCase();
                when(agentConfigRepository.existsByWorkspaceIdAndName(1L, name)).thenReturn(false);

                var request = new CreateAgentConfigRequestDTO(
                    name,
                    true,
                    AgentType.OPENCODE,
                    null,
                    null,
                    provider,
                    null,
                    null,
                    null
                );
                AgentConfig result = agentConfigService.createConfig(workspaceContext, request);
                assertThat(result.getLlmProvider()).isEqualTo(provider);
            }
        }

        @Test
        void shouldValidateProviderOnUpdate() {
            AgentConfig existing = new AgentConfig();
            existing.setId(10L);
            existing.setWorkspace(workspace);
            existing.setAgentType(AgentType.CLAUDE_CODE);
            existing.setLlmProvider(LlmProvider.ANTHROPIC);

            when(agentConfigRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(existing));

            // Try to change only provider to OPENAI — should fail because CLAUDE_CODE requires ANTHROPIC
            var request = new UpdateAgentConfigRequestDTO(null, null, null, null, LlmProvider.OPENAI, null, null, null);

            assertThatThrownBy(() -> agentConfigService.updateConfig(workspaceContext, 10L, request))
                .isInstanceOf(AgentConfigProviderMismatchException.class)
                .hasMessageContaining("CLAUDE_CODE")
                .hasMessageContaining("ANTHROPIC");
        }
    }

    @Nested
    @DisplayName("Create")
    class Create {

        @Test
        void shouldCreateNewConfig() {
            when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "my-agent")).thenReturn(false);
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = new CreateAgentConfigRequestDTO(
                "my-agent",
                true,
                AgentType.CLAUDE_CODE,
                "claude-sonnet-4-20250514",
                "sk-abc",
                LlmProvider.ANTHROPIC,
                300,
                2,
                true
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
        void shouldRejectDuplicateName() {
            when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "my-agent")).thenReturn(true);

            var request = new CreateAgentConfigRequestDTO(
                "my-agent",
                true,
                AgentType.CLAUDE_CODE,
                null,
                null,
                LlmProvider.ANTHROPIC,
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
        void shouldUpdateExistingConfigAndPreserveApiKeyWhenNull() {
            AgentConfig existing = new AgentConfig();
            existing.setId(10L);
            existing.setWorkspace(workspace);
            existing.setAgentType(AgentType.CLAUDE_CODE);
            existing.setLlmProvider(LlmProvider.ANTHROPIC);
            existing.setLlmApiKey("sk-existing-secret");

            when(agentConfigRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(existing));
            when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = new UpdateAgentConfigRequestDTO(
                null,
                AgentType.OPENCODE,
                "gpt-4o",
                null,
                LlmProvider.OPENAI,
                120,
                null,
                null
            );

            AgentConfig result = agentConfigService.updateConfig(workspaceContext, 10L, request);

            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getAgentType()).isEqualTo(AgentType.OPENCODE);
            assertThat(result.getLlmApiKey()).isEqualTo("sk-existing-secret");
            assertThat(result.getTimeoutSeconds()).isEqualTo(120);
        }

        @Test
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
        void shouldThrowNotFoundWhenDeletingNonExistentConfig() {
            when(agentConfigRepository.findByIdAndWorkspaceId(999L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> agentConfigService.deleteConfig(workspaceContext, 999L)).isInstanceOf(
                EntityNotFoundException.class
            );
        }
    }
}
