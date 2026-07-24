package de.tum.cit.aet.hephaestus.agent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModelRepository;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

class AgentBindingServiceTest extends BaseUnitTest {

    @Mock
    private AgentConfigRepository agentConfigRepository;

    @Mock
    private WorkspaceAgentBindingRepository bindingRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private LlmModelRepository llmModelRepository;

    @Mock
    private WorkspaceLlmModelRepository workspaceLlmModelRepository;

    @Mock
    private LlmModelResolver llmModelResolver;

    @Mock
    private ConfigAuditPort configAudit;

    @InjectMocks
    private AgentBindingService service;

    private WorkspaceContext context() {
        WorkspaceContext ctx = mock(WorkspaceContext.class);
        when(ctx.id()).thenReturn(1L);
        return ctx;
    }

    private Workspace workspace() {
        Workspace w = new Workspace();
        w.setId(1L);
        return w;
    }

    private AgentConfig configWithInstanceModel() {
        AgentConfig config = new AgentConfig();
        config.setId(10L);
        LlmModel model = new LlmModel();
        model.setId(99L);
        config.setInstanceModel(model);
        config.setEnabled(true);
        config.setTimeoutSeconds(300);
        config.setMaxConcurrentJobs(2);
        config.setAllowInternet(true);
        return config;
    }

    @Test
    void syncUpsertsTheBindingFromThePointedAtConfig() {
        Workspace w = workspace();
        w.setPracticeConfigId(10L);
        when(agentConfigRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(configWithInstanceModel()));
        when(bindingRepository.findByWorkspaceIdAndPurpose(1L, AgentPurpose.PRACTICE_DETECTION)).thenReturn(
            Optional.empty()
        );

        service.sync(w, AgentPurpose.PRACTICE_DETECTION);

        ArgumentCaptor<WorkspaceAgentBinding> saved = ArgumentCaptor.forClass(WorkspaceAgentBinding.class);
        verify(bindingRepository).save(saved.capture());
        assertThat(saved.getValue().getPurpose()).isEqualTo(AgentPurpose.PRACTICE_DETECTION);
        assertThat(saved.getValue().getInstanceModel().getId()).isEqualTo(99L);
        assertThat(saved.getValue().getTimeoutSeconds()).isEqualTo(300);
        assertThat(saved.getValue().getMaxConcurrentJobs()).isEqualTo(2);
        assertThat(saved.getValue().isAllowInternet()).isTrue();
        assertThat(saved.getValue().isEnabled()).isTrue();
    }

    @Test
    void syncRemovesTheBindingWhenThePointerIsCleared() {
        Workspace w = workspace(); // no practiceConfigId
        WorkspaceAgentBinding existing = new WorkspaceAgentBinding();
        when(bindingRepository.findByWorkspaceIdAndPurpose(1L, AgentPurpose.PRACTICE_DETECTION)).thenReturn(
            Optional.of(existing)
        );

        service.sync(w, AgentPurpose.PRACTICE_DETECTION);

        verify(bindingRepository).delete(existing);
        verify(bindingRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void syncRemovesTheBindingWhenTheConfigHasNoUsableModel() {
        Workspace w = workspace();
        w.setMentorConfigId(10L);
        AgentConfig unbound = new AgentConfig(); // neither model set
        unbound.setId(10L);
        when(agentConfigRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(unbound));
        WorkspaceAgentBinding existing = new WorkspaceAgentBinding();
        when(bindingRepository.findByWorkspaceIdAndPurpose(1L, AgentPurpose.MENTOR)).thenReturn(Optional.of(existing));

        service.sync(w, AgentPurpose.MENTOR);

        verify(bindingRepository).delete(existing);
    }

    @Test
    void upsertBindsAnAvailableInstanceModelAndClearsTheLegacyPointer() {
        Workspace w = workspace();
        w.setPracticeConfigId(55L); // legacy pointer to be cleared
        when(workspaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(w));
        when(bindingRepository.findByWorkspaceIdAndPurpose(1L, AgentPurpose.PRACTICE_DETECTION)).thenReturn(
            Optional.empty()
        );
        LlmModel model = new LlmModel();
        model.setId(99L);
        when(llmModelRepository.findById(99L)).thenReturn(Optional.of(model));
        when(bindingRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new UpdateAgentBindingRequestDTO(99L, null, 300, 2, true, true);
        WorkspaceAgentBinding saved = service.upsertBinding(context(), AgentPurpose.PRACTICE_DETECTION, request);

        assertThat(saved.getInstanceModel().getId()).isEqualTo(99L);
        assertThat(saved.getTimeoutSeconds()).isEqualTo(300);
        assertThat(saved.isEnabled()).isTrue();
        assertThat(w.getPracticeConfigId()).isNull(); // legacy pointer decoupled
        verify(llmModelResolver).resolve(org.mockito.ArgumentMatchers.any(WorkspaceAgentBinding.class));
    }

    @Test
    void upsertRejectsAModelThatIsNotAvailableToTheWorkspace() {
        Workspace w = workspace();
        when(workspaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(w));
        when(bindingRepository.findByWorkspaceIdAndPurpose(1L, AgentPurpose.PRACTICE_DETECTION)).thenReturn(
            Optional.empty()
        );
        LlmModel model = new LlmModel();
        model.setId(99L);
        when(llmModelRepository.findById(99L)).thenReturn(Optional.of(model));
        when(llmModelResolver.resolve(org.mockito.ArgumentMatchers.any(WorkspaceAgentBinding.class))).thenThrow(
            new IllegalStateException("unavailable")
        );

        var request = new UpdateAgentBindingRequestDTO(99L, null, null, null, null, true);
        assertThatThrownBy(() ->
            service.upsertBinding(context(), AgentPurpose.PRACTICE_DETECTION, request)
        ).isInstanceOf(IllegalArgumentException.class);
        verify(bindingRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void upsertRejectsWhenNotExactlyOneModelIsProvided() {
        Workspace w = workspace();
        when(workspaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(w));
        when(bindingRepository.findByWorkspaceIdAndPurpose(1L, AgentPurpose.MENTOR)).thenReturn(Optional.empty());

        var bothNull = new UpdateAgentBindingRequestDTO(null, null, null, null, null, true);
        assertThatThrownBy(() -> service.upsertBinding(context(), AgentPurpose.MENTOR, bothNull)).isInstanceOf(
            IllegalArgumentException.class
        );
    }

    @Test
    void deleteRemovesTheBindingAndClearsTheLegacyPointer() {
        Workspace w = workspace();
        w.setMentorConfigId(55L);
        when(workspaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(w));
        WorkspaceAgentBinding existing = new WorkspaceAgentBinding();
        when(bindingRepository.findByWorkspaceIdAndPurpose(1L, AgentPurpose.MENTOR)).thenReturn(Optional.of(existing));

        service.deleteBinding(context(), AgentPurpose.MENTOR);

        verify(bindingRepository).delete(existing);
        assertThat(w.getMentorConfigId()).isNull();
    }
}
