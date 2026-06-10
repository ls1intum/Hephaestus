package de.tum.cit.aet.hephaestus.practices.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import de.tum.cit.aet.hephaestus.practices.PracticeGoalRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class PracticesWorkspacePurgeAdapterTest extends BaseUnitTest {

    @Mock
    private PracticeFindingRepository practiceFindingRepository;

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private PracticeGoalRepository practiceGoalRepository;

    @Mock
    private de.tum.cit.aet.hephaestus.practices.feedback.delivery.FeedbackDeliveryRepository feedbackDeliveryRepository;

    @Mock
    private de.tum.cit.aet.hephaestus.practices.feedback.interaction.FeedbackInteractionRepository feedbackInteractionRepository;

    private PracticesWorkspacePurgeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PracticesWorkspacePurgeAdapter(
            practiceFindingRepository,
            practiceRepository,
            practiceGoalRepository,
            feedbackDeliveryRepository,
            feedbackInteractionRepository
        );
    }

    @Test
    void deleteWorkspaceData_deletesAllPracticesData() {
        Long workspaceId = 789L;

        adapter.deleteWorkspaceData(workspaceId);

        // Then — feedback, findings, practices, and goals are all removed.
        verify(feedbackDeliveryRepository).deleteAllByWorkspaceId(workspaceId);
        verify(feedbackInteractionRepository).deleteAllByWorkspaceId(workspaceId);
        verify(practiceFindingRepository).deleteAllByPracticeWorkspaceId(workspaceId);
        verify(practiceRepository).deleteAllByWorkspaceId(workspaceId);
        verify(practiceGoalRepository).deleteAllByWorkspaceId(workspaceId);
    }

    @Test
    @DisplayName("runs before default-order contributors")
    void getOrder_returnsNegativeValue() {
        // The adapter should run early, before other contributors
        assertThat(adapter.getOrder()).isLessThan(0);
    }
}
