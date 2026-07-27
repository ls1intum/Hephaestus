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
import de.tum.cit.aet.hephaestus.agent.catalog.ResolvedLlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditAction;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
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

        var request = new AgentBindingRequestDTO(99L, null, 300, 2, true, true);
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

        // The two model columns are interchangeable in shape but not in meaning — an entry that filed
        // the id under workspaceModelId would claim the workspace pays for a model the instance owns.
        ArgumentCaptor<ConfigAuditEntry> entry = ArgumentCaptor.forClass(ConfigAuditEntry.class);
        verify(configAudit).record(entry.capture());
        assertThat(entry.getValue().entityType()).isEqualTo(ConfigAuditEntityType.AGENT_BINDING);
        assertThat(entry.getValue().entityId()).isEqualTo(AgentPurpose.PRACTICE_DETECTION.name());
        assertThat(entry.getValue().workspaceId()).isEqualTo(1L);
        assertThat(entry.getValue().action()).isEqualTo(ConfigAuditAction.UPDATED);
        assertThat(entry.getValue().before()).hasFieldOrPropertyWithValue("instanceModelId", null);
        assertThat(entry.getValue().after())
            .hasFieldOrPropertyWithValue("instanceModelId", 99L)
            .hasFieldOrPropertyWithValue("workspaceModelId", null)
            .hasFieldOrPropertyWithValue("enabled", true);
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

        var request = new AgentBindingRequestDTO(99L, null, null, null, null, true);
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

        var bothNull = new AgentBindingRequestDTO(null, null, null, null, null, true);
        assertThatThrownBy(() -> service.upsertBinding(context(), AgentPurpose.MENTOR, bothNull)).isInstanceOf(
            IllegalArgumentException.class
        );
    }

    /**
     * The audit row is the only durable trace a delete leaves — the binding itself is gone — so the
     * assertion is on its payload, not on the fact that a call happened. Two things must hold: the
     * action reads {@code DELETED} (not {@code UPDATED} with an emptied-out "after", which would file a
     * removal alongside genuine edits), and the {@code before} snapshot carries the model the workspace
     * actually lost, captured before {@code delete()} rather than after.
     */
    @Test
    void deleteRemovesTheBindingAndFilesADeletedAuditRowNamingTheLostModel() {
        Workspace w = workspace();
        when(workspaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(w));
        WorkspaceAgentBinding existing = new WorkspaceAgentBinding();
        LlmModel bound = new LlmModel();
        bound.setId(42L);
        existing.setInstanceModel(bound);
        existing.setEnabled(true);
        when(bindingRepository.findByWorkspaceIdAndPurpose(1L, AgentPurpose.MENTOR)).thenReturn(Optional.of(existing));

        service.deleteBinding(context(), AgentPurpose.MENTOR);

        verify(bindingRepository).delete(existing);

        ArgumentCaptor<ConfigAuditEntry> entry = ArgumentCaptor.forClass(ConfigAuditEntry.class);
        verify(configAudit).record(entry.capture());
        assertThat(entry.getValue().entityType()).isEqualTo(ConfigAuditEntityType.AGENT_BINDING);
        assertThat(entry.getValue().entityId()).isEqualTo(AgentPurpose.MENTOR.name());
        assertThat(entry.getValue().workspaceId()).isEqualTo(1L);
        assertThat(entry.getValue().action())
            .as("removing a binding is a deletion, not an update to an all-null snapshot")
            .isEqualTo(ConfigAuditAction.DELETED);
        assertThat(entry.getValue().after()).isNull();
        assertThat(entry.getValue().before())
            .as("the pre-delete state must name the model the workspace lost")
            .hasFieldOrPropertyWithValue("instanceModelId", 42L)
            .hasFieldOrPropertyWithValue("workspaceModelId", null)
            .hasFieldOrPropertyWithValue("enabled", true);
    }

    /**
     * The positive leg. Without it every {@code isReady} assertion in this class survives replacing the
     * method body with {@code return false}, and the UI would report every workspace unable to run.
     */
    @Test
    void isReadyIsTrueWhenAnEnabledBindingStillResolves() {
        WorkspaceAgentBinding binding = new WorkspaceAgentBinding();
        binding.setEnabled(true);
        when(llmModelResolver.resolve(binding)).thenReturn(
            new ResolvedLlmModel("https://api.openai.com", "openai-completions", "gpt-test", null, null, false)
        );

        assertThat(service.isReady(binding)).isTrue();
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
