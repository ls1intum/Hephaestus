package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.catalog.ResolvedLlmModel;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBinding;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBindingRepository;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class DefaultMentorReadinessQueryTest extends BaseUnitTest {

    @Mock
    private WorkspaceAgentBindingRepository agentBindingRepository;

    @Mock
    private LlmModelResolver llmModelResolver;

    private DefaultMentorReadinessQuery query;

    @BeforeEach
    void setUp() {
        query = new DefaultMentorReadinessQuery(agentBindingRepository, llmModelResolver);
    }

    /**
     * The positive leg. Without it every case in this class is a "false", and replacing the whole
     * method body with {@code return false} passes — Slack App Home would then tell every workspace
     * its mentor is unavailable while it is working.
     */
    @Test
    void shouldReportReadyWhenAnEnabledBindingResolvesToAModel() {
        WorkspaceAgentBinding binding = new WorkspaceAgentBinding();
        binding.setId(10L);
        binding.setPurpose(AgentPurpose.MENTOR);
        binding.setEnabled(true);
        when(agentBindingRepository.findByWorkspaceIdAndPurposeWithModels(1L, AgentPurpose.MENTOR)).thenReturn(
            Optional.of(binding)
        );
        when(llmModelResolver.resolve(binding)).thenReturn(
            new ResolvedLlmModel(
                "https://api.openai.com",
                "openai-completions",
                "gpt-test",
                null,
                null,
                false,
                FundingSource.INSTANCE
            )
        );

        assertThat(query.isReady(1L)).isTrue();
    }

    @Test
    void shouldNotReportReadyWhenMentorIsUnconfigured() {
        when(agentBindingRepository.findByWorkspaceIdAndPurposeWithModels(1L, AgentPurpose.MENTOR)).thenReturn(
            Optional.empty()
        );
        assertThat(query.isReady(1L)).isFalse();
    }

    @Test
    void shouldNotReportReadyWhenBoundModelIsUnavailable() {
        WorkspaceAgentBinding binding = new WorkspaceAgentBinding();
        binding.setId(10L);
        binding.setPurpose(AgentPurpose.MENTOR);
        binding.setEnabled(true);
        when(agentBindingRepository.findByWorkspaceIdAndPurposeWithModels(1L, AgentPurpose.MENTOR)).thenReturn(
            Optional.of(binding)
        );
        when(llmModelResolver.resolve(binding)).thenThrow(new IllegalStateException("unavailable"));

        assertThat(query.isReady(1L)).isFalse();
    }

    @Test
    void shouldNotReportReadyWhenBindingIsDisabled() {
        WorkspaceAgentBinding disabled = new WorkspaceAgentBinding();
        disabled.setId(10L);
        disabled.setPurpose(AgentPurpose.MENTOR);
        disabled.setEnabled(false);
        when(agentBindingRepository.findByWorkspaceIdAndPurposeWithModels(1L, AgentPurpose.MENTOR)).thenReturn(
            Optional.of(disabled)
        );

        assertThat(query.isReady(1L)).isFalse();
    }
}
