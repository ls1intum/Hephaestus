package de.tum.cit.aet.hephaestus.agent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnection;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelWorkspaceGrantRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.ModelVisibility;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmConnection;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.dao.DataIntegrityViolationException;

class AgentConfigServiceTest extends BaseUnitTest {

    @Mock
    private AgentConfigRepository agentConfigRepository;

    @Mock
    private AgentJobRepository agentJobRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private ConfigAuditPort configAudit;

    @Mock
    private LlmModelRepository llmModelRepository;

    @Mock
    private WorkspaceLlmModelRepository workspaceLlmModelRepository;

    @Mock
    private LlmModelWorkspaceGrantRepository llmModelWorkspaceGrantRepository;

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
    class Read {

        @Test
        void shouldReturnConfigsForWorkspace() {
            var configs = List.of(new AgentConfig(), new AgentConfig());
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
                .enabled(false)
                .timeoutSeconds(300)
                .maxConcurrentJobs(2)
                .allowInternet(true)
                .build();

            AgentConfig result = agentConfigService.createConfig(workspaceContext, request);

            assertThat(result.getWorkspace()).isEqualTo(workspace);
            assertThat(result.getName()).isEqualTo("my-agent");
            assertThat(result.isEnabled()).isFalse();
            assertThat(result.getModelName()).isNull();
            assertThat(result.getLlmApiKey()).isNull();
            assertThat(result.getLlmProvider()).isNull();
            assertThat(result.getTimeoutSeconds()).isEqualTo(300);
            assertThat(result.getMaxConcurrentJobs()).isEqualTo(2);
            assertThat(result.isAllowInternet()).isTrue();
        }

        @Test
        void shouldRejectDuplicateName() {
            when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "my-agent")).thenReturn(true);

            var request = CreateAgentConfigRequestDTO.builder().name("my-agent").enabled(false).build();

            assertThatThrownBy(() -> agentConfigService.createConfig(workspaceContext, request))
                .isInstanceOf(AgentConfigNameConflictException.class)
                .hasMessageContaining("my-agent");
        }

        @Test
        void shouldTranslateUniqueConstraintRaceIntoConflict() {
            // The exists-check passed (the concurrent winner committed after it ran), so save() trips the
            // DB unique constraint. The loser must still surface the 409 conflict, not a raw 500.
            when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "my-agent")).thenReturn(false);
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
            when(agentConfigRepository.save(any())).thenThrow(
                new DataIntegrityViolationException("uk_agent_config_workspace_name")
            );

            var request = CreateAgentConfigRequestDTO.builder().name("my-agent").enabled(false).build();

            assertThatThrownBy(() -> agentConfigService.createConfig(workspaceContext, request))
                .isInstanceOf(AgentConfigNameConflictException.class)
                .hasMessageContaining("my-agent");
        }

        @Test
        void shouldRejectEnabledConfigWithoutCatalogBinding() {
            when(agentConfigRepository.existsByWorkspaceIdAndName(1L, "my-agent")).thenReturn(false);
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

            var request = CreateAgentConfigRequestDTO.builder().name("my-agent").enabled(true).build();

            assertThatThrownBy(() -> agentConfigService.createConfig(workspaceContext, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bind exactly one");
            verify(agentConfigRepository, never()).save(any());
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

            when(agentConfigRepository.findByIdAndWorkspaceIdForUpdate(10L, 1L)).thenReturn(Optional.of(existing));
            when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = UpdateAgentConfigRequestDTO.builder().timeoutSeconds(120).build();

            AgentConfig result = agentConfigService.updateConfig(workspaceContext, 10L, request);

            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getLlmApiKey()).isEqualTo("sk-existing-secret");
            assertThat(result.getTimeoutSeconds()).isEqualTo(120);
        }

        @Test
        void shouldThrowNotFoundWhenUpdatingNonExistentConfig() {
            when(agentConfigRepository.findByIdAndWorkspaceIdForUpdate(999L, 1L)).thenReturn(Optional.empty());

            var request = UpdateAgentConfigRequestDTO.builder().build();

            assertThatThrownBy(() -> agentConfigService.updateConfig(workspaceContext, 999L, request)).isInstanceOf(
                EntityNotFoundException.class
            );
        }

        @Test
        void shouldRejectEnablingUnboundConfig() {
            AgentConfig existing = new AgentConfig();
            existing.setId(10L);
            existing.setWorkspace(workspace);
            when(agentConfigRepository.findByIdAndWorkspaceIdForUpdate(10L, 1L)).thenReturn(Optional.of(existing));

            var request = UpdateAgentConfigRequestDTO.builder().enabled(true).build();

            assertThatThrownBy(() -> agentConfigService.updateConfig(workspaceContext, 10L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bind exactly one");
            verify(agentConfigRepository, never()).save(any());
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
        void shouldRejectDeleteWhenBoundToMentor() {
            // Sibling of shouldRejectDeleteWhenConfigBound for the mentorConfigId branch (practiceConfigId
            // left null) — guards against dropping the mentor clause from the bound-check.
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
            workspace.setMentorConfigId(10L);
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

    @Nested
    class ConfigAuditWrites {

        @Test
        void updateRecordsThePreMutationStateAsBefore() {
            // Pins snapshot ORDERING, not just presence: move `before` below the setters and every
            // agent-config update becomes an empty diff, which the recorder suppresses as a no-op —
            // total, silent audit loss for updates, with a green build.
            AgentConfig config = new AgentConfig();
            config.setId(7L);
            config.setWorkspace(workspace);
            config.setName("primary");
            config.setLlmProvider(LlmProvider.OPENAI);
            config.setTimeoutSeconds(600);
            when(agentConfigRepository.findByIdAndWorkspaceIdForUpdate(7L, 1L)).thenReturn(Optional.of(config));
            when(agentConfigRepository.save(any(AgentConfig.class))).thenAnswer(i -> i.getArgument(0));

            agentConfigService.updateConfig(
                workspaceContext,
                7L,
                UpdateAgentConfigRequestDTO.builder().timeoutSeconds(900).build()
            );

            ArgumentCaptor<ConfigAuditEntry> captor = ArgumentCaptor.forClass(ConfigAuditEntry.class);
            verify(configAudit).record(captor.capture());
            ConfigAuditEntry entry = captor.getValue();
            assertThat(entry.entityType()).isEqualTo(ConfigAuditEntityType.AGENT_CONFIG);
            assertThat(entry.entityId()).isEqualTo("7");
            assertThat(String.valueOf(entry.before())).contains("timeoutSeconds=600");
            assertThat(String.valueOf(entry.after())).contains("timeoutSeconds=900");
        }
    }

    @Nested
    class ModelBinding {

        private AgentConfig existingConfig() {
            AgentConfig config = new AgentConfig();
            config.setId(10L);
            config.setWorkspace(workspace);
            config.setLlmProvider(LlmProvider.ANTHROPIC);
            return config;
        }

        private LlmModel instanceModel(
            Long id,
            ModelVisibility visibility,
            boolean modelEnabled,
            boolean connectionEnabled
        ) {
            LlmConnection connection = new LlmConnection();
            connection.setId(100L);
            connection.setEnabled(connectionEnabled);
            LlmModel model = new LlmModel();
            model.setId(id);
            model.setConnection(connection);
            model.setVisibility(visibility);
            model.setEnabled(modelEnabled);
            return model;
        }

        private WorkspaceLlmModel workspaceModel(Long id, boolean modelEnabled, boolean connectionEnabled) {
            WorkspaceLlmConnection connection = new WorkspaceLlmConnection();
            connection.setId(200L);
            connection.setEnabled(connectionEnabled);
            WorkspaceLlmModel model = new WorkspaceLlmModel();
            model.setId(id);
            model.setConnection(connection);
            model.setEnabled(modelEnabled);
            return model;
        }

        @Test
        void settingBothInstanceAndWorkspaceModelIsRejected() {
            when(agentConfigRepository.findByIdAndWorkspaceIdForUpdate(10L, 1L)).thenReturn(
                Optional.of(existingConfig())
            );

            var request = UpdateAgentConfigRequestDTO.builder().instanceModelId(5L).workspaceModelId(6L).build();

            assertThatThrownBy(() -> agentConfigService.updateConfig(workspaceContext, 10L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only one model");
            verify(agentConfigRepository, never()).save(any());
        }

        @Test
        void bindingAPublicEnabledInstanceModelSucceedsAndClearsAnyWorkspaceModel() {
            AgentConfig existing = existingConfig();
            existing.setWorkspaceModel(workspaceModel(6L, true, true));
            when(agentConfigRepository.findByIdAndWorkspaceIdForUpdate(10L, 1L)).thenReturn(Optional.of(existing));
            when(llmModelRepository.findById(5L)).thenReturn(
                Optional.of(instanceModel(5L, ModelVisibility.PUBLIC, true, true))
            );
            when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = UpdateAgentConfigRequestDTO.builder().instanceModelId(5L).build();
            AgentConfig result = agentConfigService.updateConfig(workspaceContext, 10L, request);

            assertThat(result.getInstanceModel()).isNotNull();
            assertThat(result.getInstanceModel().getId()).isEqualTo(5L);
            assertThat(result.getWorkspaceModel()).isNull();
        }

        @Test
        void bindingAGrantedInstanceModelWithoutAGrantIsRejected() {
            when(agentConfigRepository.findByIdAndWorkspaceIdForUpdate(10L, 1L)).thenReturn(
                Optional.of(existingConfig())
            );
            when(llmModelRepository.findById(5L)).thenReturn(
                Optional.of(instanceModel(5L, ModelVisibility.GRANTED, true, true))
            );
            when(llmModelWorkspaceGrantRepository.existsByIdModelIdAndIdWorkspaceId(5L, 1L)).thenReturn(false);

            var request = UpdateAgentConfigRequestDTO.builder().instanceModelId(5L).build();

            assertThatThrownBy(() -> agentConfigService.updateConfig(workspaceContext, 10L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("isn't available");
            verify(agentConfigRepository, never()).save(any());
        }

        @Test
        void bindingAGrantedInstanceModelWithAGrantSucceeds() {
            AgentConfig existing = existingConfig();
            when(agentConfigRepository.findByIdAndWorkspaceIdForUpdate(10L, 1L)).thenReturn(Optional.of(existing));
            when(llmModelRepository.findById(5L)).thenReturn(
                Optional.of(instanceModel(5L, ModelVisibility.GRANTED, true, true))
            );
            when(llmModelWorkspaceGrantRepository.existsByIdModelIdAndIdWorkspaceId(5L, 1L)).thenReturn(true);
            when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = UpdateAgentConfigRequestDTO.builder().instanceModelId(5L).build();
            AgentConfig result = agentConfigService.updateConfig(workspaceContext, 10L, request);

            assertThat(result.getInstanceModel().getId()).isEqualTo(5L);
        }

        @Test
        void bindingADisabledInstanceModelIsRejected() {
            when(agentConfigRepository.findByIdAndWorkspaceIdForUpdate(10L, 1L)).thenReturn(
                Optional.of(existingConfig())
            );
            when(llmModelRepository.findById(5L)).thenReturn(
                Optional.of(instanceModel(5L, ModelVisibility.PUBLIC, false, true))
            );

            var request = UpdateAgentConfigRequestDTO.builder().instanceModelId(5L).build();

            assertThatThrownBy(() -> agentConfigService.updateConfig(workspaceContext, 10L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("isn't available");
        }

        @Test
        void bindingAnInstanceModelWhoseConnectionIsDisabledIsRejected() {
            when(agentConfigRepository.findByIdAndWorkspaceIdForUpdate(10L, 1L)).thenReturn(
                Optional.of(existingConfig())
            );
            when(llmModelRepository.findById(5L)).thenReturn(
                Optional.of(instanceModel(5L, ModelVisibility.PUBLIC, true, false))
            );

            var request = UpdateAgentConfigRequestDTO.builder().instanceModelId(5L).build();

            assertThatThrownBy(() -> agentConfigService.updateConfig(workspaceContext, 10L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("isn't available");
        }

        @Test
        void bindingAnEnabledWorkspaceModelSucceedsAndClearsAnyInstanceModel() {
            AgentConfig existing = existingConfig();
            existing.setInstanceModel(instanceModel(5L, ModelVisibility.PUBLIC, true, true));
            when(agentConfigRepository.findByIdAndWorkspaceIdForUpdate(10L, 1L)).thenReturn(Optional.of(existing));
            when(workspaceLlmModelRepository.findByIdAndWorkspaceId(6L, 1L)).thenReturn(
                Optional.of(workspaceModel(6L, true, true))
            );
            when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = UpdateAgentConfigRequestDTO.builder().workspaceModelId(6L).build();
            AgentConfig result = agentConfigService.updateConfig(workspaceContext, 10L, request);

            assertThat(result.getWorkspaceModel().getId()).isEqualTo(6L);
            assertThat(result.getInstanceModel()).isNull();
        }

        @Test
        void bindingAWorkspaceModelFromAnotherWorkspaceIsRejected() {
            // The tenancy-scoped lookup itself enforces this (composite FK also guards it server-side);
            // this proves the service surfaces a clean 404, not a 500, for a foreign/nonexistent id.
            when(agentConfigRepository.findByIdAndWorkspaceIdForUpdate(10L, 1L)).thenReturn(
                Optional.of(existingConfig())
            );
            when(workspaceLlmModelRepository.findByIdAndWorkspaceId(6L, 1L)).thenReturn(Optional.empty());

            var request = UpdateAgentConfigRequestDTO.builder().workspaceModelId(6L).build();

            assertThatThrownBy(() -> agentConfigService.updateConfig(workspaceContext, 10L, request)).isInstanceOf(
                EntityNotFoundException.class
            );
        }

        @Test
        void bindingADisabledWorkspaceModelIsRejected() {
            when(agentConfigRepository.findByIdAndWorkspaceIdForUpdate(10L, 1L)).thenReturn(
                Optional.of(existingConfig())
            );
            when(workspaceLlmModelRepository.findByIdAndWorkspaceId(6L, 1L)).thenReturn(
                Optional.of(workspaceModel(6L, false, true))
            );

            var request = UpdateAgentConfigRequestDTO.builder().workspaceModelId(6L).build();

            assertThatThrownBy(() -> agentConfigService.updateConfig(workspaceContext, 10L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("isn't available");
        }

        @Test
        void leavingBothIdsAbsentPreservesTheExistingBinding() {
            AgentConfig existing = existingConfig();
            existing.setInstanceModel(instanceModel(5L, ModelVisibility.PUBLIC, true, true));
            when(agentConfigRepository.findByIdAndWorkspaceIdForUpdate(10L, 1L)).thenReturn(Optional.of(existing));
            when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = UpdateAgentConfigRequestDTO.builder().timeoutSeconds(900).build();
            AgentConfig result = agentConfigService.updateConfig(workspaceContext, 10L, request);

            assertThat(result.getInstanceModel()).isNotNull();
            assertThat(result.getInstanceModel().getId()).isEqualTo(5L);
            verify(llmModelRepository, never()).findById(any());
        }

        @Test
        void enabledConfigCannotKeepARevokedBindingDuringAnUnrelatedUpdate() {
            AgentConfig existing = existingConfig();
            existing.setEnabled(true);
            existing.setInstanceModel(instanceModel(5L, ModelVisibility.GRANTED, true, true));
            when(agentConfigRepository.findByIdAndWorkspaceIdForUpdate(10L, 1L)).thenReturn(Optional.of(existing));
            when(llmModelWorkspaceGrantRepository.existsByIdModelIdAndIdWorkspaceId(5L, 1L)).thenReturn(false);

            var request = UpdateAgentConfigRequestDTO.builder().timeoutSeconds(900).build();

            assertThatThrownBy(() -> agentConfigService.updateConfig(workspaceContext, 10L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("isn't available");
            verify(agentConfigRepository, never()).save(any());
        }

        @Test
        void revokedBindingCanBeSavedWhenTheSameUpdateDisablesTheConfig() {
            AgentConfig existing = existingConfig();
            existing.setEnabled(true);
            existing.setInstanceModel(instanceModel(5L, ModelVisibility.GRANTED, true, true));
            when(agentConfigRepository.findByIdAndWorkspaceIdForUpdate(10L, 1L)).thenReturn(Optional.of(existing));
            when(agentConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = UpdateAgentConfigRequestDTO.builder().enabled(false).build();
            AgentConfig result = agentConfigService.updateConfig(workspaceContext, 10L, request);

            assertThat(result.isEnabled()).isFalse();
            verify(agentConfigRepository).save(existing);
        }
    }
}
