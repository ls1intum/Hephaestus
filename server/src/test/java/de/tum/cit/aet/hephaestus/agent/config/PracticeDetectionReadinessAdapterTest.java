package de.tum.cit.aet.hephaestus.agent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.catalog.ResolvedLlmModel;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class PracticeDetectionReadinessAdapterTest extends BaseUnitTest {

    @Mock
    private WorkspaceAgentBindingRepository bindingRepository;

    @Mock
    private LlmModelResolver resolver;

    private PracticeDetectionReadinessAdapter checker;

    @BeforeEach
    void setUp() {
        checker = new PracticeDetectionReadinessAdapter(bindingRepository, resolver);
    }

    private WorkspaceAgentBinding binding(boolean enabled) {
        WorkspaceAgentBinding b = new WorkspaceAgentBinding();
        b.setPurpose(AgentPurpose.PRACTICE_DETECTION);
        b.setEnabled(enabled);
        return b;
    }

    @Test
    void unboundPracticeIsNotRunnable() {
        when(bindingRepository.findByWorkspaceIdAndPurposeWithModels(1L, AgentPurpose.PRACTICE_DETECTION)).thenReturn(
            Optional.empty()
        );
        assertThat(checker.hasRunnableAgent(1L)).isFalse();
    }

    @Test
    void disabledBindingIsNotRunnable() {
        when(bindingRepository.findByWorkspaceIdAndPurposeWithModels(1L, AgentPurpose.PRACTICE_DETECTION)).thenReturn(
            Optional.of(binding(false))
        );
        assertThat(checker.hasRunnableAgent(1L)).isFalse();
    }

    @Test
    void enabledBindingWithRevokedModelIsNotRunnable() {
        WorkspaceAgentBinding b = binding(true);
        when(bindingRepository.findByWorkspaceIdAndPurposeWithModels(1L, AgentPurpose.PRACTICE_DETECTION)).thenReturn(
            Optional.of(b)
        );
        when(resolver.resolve(b)).thenThrow(new IllegalStateException("unavailable"));
        assertThat(checker.hasRunnableAgent(1L)).isFalse();
    }

    @Test
    void enabledBindingWithAvailableModelIsRunnable() {
        WorkspaceAgentBinding b = binding(true);
        when(bindingRepository.findByWorkspaceIdAndPurposeWithModels(1L, AgentPurpose.PRACTICE_DETECTION)).thenReturn(
            Optional.of(b)
        );
        when(resolver.resolve(b)).thenReturn(org.mockito.Mockito.mock(ResolvedLlmModel.class));
        assertThat(checker.hasRunnableAgent(1L)).isTrue();
    }
}
