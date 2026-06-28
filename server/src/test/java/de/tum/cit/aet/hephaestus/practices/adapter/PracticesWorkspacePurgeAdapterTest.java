package de.tum.cit.aet.hephaestus.practices.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import de.tum.cit.aet.hephaestus.practices.PracticeAreaRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class PracticesWorkspacePurgeAdapterTest extends BaseUnitTest {

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private ObservationRepository observationRepository;

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private PracticeAreaRepository practiceAreaRepository;

    private PracticesWorkspacePurgeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PracticesWorkspacePurgeAdapter(
            feedbackRepository,
            observationRepository,
            practiceRepository,
            practiceAreaRepository
        );
    }

    @Test
    void deleteWorkspaceData_deletesAllPracticesData() {
        Long workspaceId = 789L;

        adapter.deleteWorkspaceData(workspaceId);

        verify(feedbackRepository).deleteAllByWorkspaceId(workspaceId);
        verify(observationRepository).deleteAllByPracticeWorkspaceId(workspaceId);
        verify(practiceRepository).deleteAllByWorkspaceId(workspaceId);
        verify(practiceAreaRepository).deleteAllByWorkspaceId(workspaceId);
    }

    @Test
    @DisplayName("runs before default-order purge contributors")
    void getOrder_returnsNegativeValue() {
        assertThat(adapter.getOrder()).isLessThan(0);
    }
}
