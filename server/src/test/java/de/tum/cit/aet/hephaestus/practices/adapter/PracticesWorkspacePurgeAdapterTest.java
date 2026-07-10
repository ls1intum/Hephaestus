package de.tum.cit.aet.hephaestus.practices.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import de.tum.cit.aet.hephaestus.core.audit.DataAccessAuditWriter;
import de.tum.cit.aet.hephaestus.practices.PracticeAreaRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
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

    @Mock
    private DataAccessAuditWriter dataAccessAuditWriter;

    private PracticesWorkspacePurgeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PracticesWorkspacePurgeAdapter(
            feedbackRepository,
            observationRepository,
            practiceRepository,
            practiceAreaRepository,
            dataAccessAuditWriter
        );
    }

    @Test
    void deleteWorkspaceData_deletesAllPracticesData() {
        Long workspaceId = 789L;

        adapter.deleteWorkspaceData(workspaceId);

        // The FK-driven dependency order is load-bearing (feedback has a RESTRICT FK; practices clear the
        // practice -> practice_area references before areas are removed). Assert the ORDER, not just the
        // calls, so a reordering refactor fails the unit test instead of only failing on a real DB.
        InOrder inOrder = inOrder(
            feedbackRepository,
            observationRepository,
            practiceRepository,
            practiceAreaRepository
        );
        inOrder.verify(feedbackRepository).deleteAllByWorkspaceId(workspaceId);
        inOrder.verify(observationRepository).deleteAllByPracticeWorkspaceId(workspaceId);
        inOrder.verify(practiceRepository).deleteAllByWorkspaceId(workspaceId);
        inOrder.verify(practiceAreaRepository).deleteAllByWorkspaceId(workspaceId);
        verify(dataAccessAuditWriter).purgeWorkspace(workspaceId);
    }

    @Test
    @DisplayName("runs before default-order purge contributors")
    void getOrder_returnsNegativeValue() {
        assertThat(adapter.getOrder()).isLessThan(0);
    }
}
