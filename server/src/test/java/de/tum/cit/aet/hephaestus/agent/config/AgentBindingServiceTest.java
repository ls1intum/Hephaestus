package de.tum.cit.aet.hephaestus.agent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
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

    @InjectMocks
    private AgentBindingService service;

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
}
