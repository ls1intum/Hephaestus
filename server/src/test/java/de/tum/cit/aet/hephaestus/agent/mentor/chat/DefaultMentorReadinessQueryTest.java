package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfig;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfigRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.spi.WorkspaceSummaryQuery;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class DefaultMentorReadinessQueryTest extends BaseUnitTest {

    @Mock
    private WorkspaceSummaryQuery workspaceSummaryQuery;

    @Mock
    private AgentConfigRepository agentConfigRepository;

    @Mock
    private LlmModelResolver llmModelResolver;

    private DefaultMentorReadinessQuery query;

    @BeforeEach
    void setUp() {
        query = new DefaultMentorReadinessQuery(workspaceSummaryQuery, agentConfigRepository, llmModelResolver);
    }

    @Test
    void shouldNotFallBackToAnotherConfigWhenMentorIsUnconfigured() {
        when(workspaceSummaryQuery.findById(1L)).thenReturn(
            Optional.of(new WorkspaceSummaryQuery.WorkspaceSummary(1L, "workspace", "Workspace", null))
        );
        assertThat(query.isReady(1L)).isFalse();

        verify(agentConfigRepository, never()).findFirstByWorkspaceIdAndEnabledTrueOrderByIdAsc(1L);
    }

    @Test
    void shouldNotReportReadyWhenBoundConfigHasNoCatalogModel() {
        when(workspaceSummaryQuery.findById(1L)).thenReturn(
            Optional.of(new WorkspaceSummaryQuery.WorkspaceSummary(1L, "workspace", "Workspace", 10L))
        );
        Workspace workspace = new Workspace();
        workspace.setId(1L);
        AgentConfig config = new AgentConfig();
        config.setId(10L);
        config.setWorkspace(workspace);
        config.setEnabled(true);
        when(agentConfigRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(config));

        assertThat(query.isReady(1L)).isFalse();
    }
}
