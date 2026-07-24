package de.tum.cit.aet.hephaestus.agent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModelRepository;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

class AgentBindingServiceTest extends BaseUnitTest {

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

    @Test
    void upsertBindsAnAvailableInstanceModel() {
        Workspace w = workspace();
        when(workspaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(w));
        when(bindingRepository.findByWorkspaceIdAndPurpose(1L, AgentPurpose.PRACTICE_DETECTION)).thenReturn(
            Optional.empty()
        );
        LlmModel model = new LlmModel();
        model.setId(99L);
        when(llmModelRepository.findById(99L)).thenReturn(Optional.of(model));
        when(bindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new AgentBindingUpsertRequestDTO(99L, null, 300, 2, true, true);
        WorkspaceAgentBinding saved = service.upsertBinding(context(), AgentPurpose.PRACTICE_DETECTION, request);

        assertThat(saved.getWorkspace()).isSameAs(w);
        assertThat(saved.getPurpose()).isEqualTo(AgentPurpose.PRACTICE_DETECTION);
        assertThat(saved.getInstanceModel().getId()).isEqualTo(99L);
        assertThat(saved.getWorkspaceModel()).isNull();
        assertThat(saved.getTimeoutSeconds()).isEqualTo(300);
        assertThat(saved.getMaxConcurrentJobs()).isEqualTo(2);
        assertThat(saved.isAllowInternet()).isTrue();
        assertThat(saved.isEnabled()).isTrue();
        // Availability is checked by the resolver, not re-implemented here.
        verify(llmModelResolver).resolve(any(WorkspaceAgentBinding.class));
        verify(configAudit).record(any(ConfigAuditEntry.class));
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
        when(llmModelResolver.resolve(any(WorkspaceAgentBinding.class))).thenThrow(
            new IllegalStateException("unavailable")
        );

        var request = new AgentBindingUpsertRequestDTO(99L, null, null, null, null, true);
        assertThatThrownBy(() ->
            service.upsertBinding(context(), AgentPurpose.PRACTICE_DETECTION, request)
        ).isInstanceOf(IllegalArgumentException.class);
        verify(bindingRepository, never()).save(any());
    }

    @Test
    void upsertRejectsWhenNotExactlyOneModelIsProvided() {
        Workspace w = workspace();
        when(workspaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(w));
        when(bindingRepository.findByWorkspaceIdAndPurpose(1L, AgentPurpose.MENTOR)).thenReturn(Optional.empty());

        var bothNull = new AgentBindingUpsertRequestDTO(null, null, null, null, null, true);
        assertThatThrownBy(() -> service.upsertBinding(context(), AgentPurpose.MENTOR, bothNull)).isInstanceOf(
            IllegalArgumentException.class
        );
    }

    @Test
    void deleteRemovesTheBinding() {
        Workspace w = workspace();
        when(workspaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(w));
        WorkspaceAgentBinding existing = new WorkspaceAgentBinding();
        when(bindingRepository.findByWorkspaceIdAndPurpose(1L, AgentPurpose.MENTOR)).thenReturn(Optional.of(existing));

        service.deleteBinding(context(), AgentPurpose.MENTOR);

        verify(bindingRepository).delete(existing);
        verify(configAudit).record(any(ConfigAuditEntry.class));
    }

    @Test
    void getBindingsReturnsEveryPurposeConfiguredForTheWorkspace() {
        WorkspaceAgentBinding binding = new WorkspaceAgentBinding();
        binding.setPurpose(AgentPurpose.MENTOR);
        when(bindingRepository.findByWorkspaceId(1L)).thenReturn(List.of(binding));

        assertThat(service.getBindings(context())).containsExactly(binding);
    }

    @Test
    void isReadyIsFalseForADisabledBindingWithoutTouchingTheResolver() {
        WorkspaceAgentBinding binding = new WorkspaceAgentBinding();
        binding.setEnabled(false);

        assertThat(service.isReady(binding)).isFalse();
        verify(llmModelResolver, never()).resolve(any(WorkspaceAgentBinding.class));
    }

    @Test
    void isReadyIsFalseWhenTheBoundModelIsNoLongerAvailable() {
        WorkspaceAgentBinding binding = new WorkspaceAgentBinding();
        binding.setEnabled(true);
        when(llmModelResolver.resolve(binding)).thenThrow(new IllegalStateException("unavailable"));

        assertThat(service.isReady(binding)).isFalse();
    }
}
