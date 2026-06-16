package de.tum.cit.aet.hephaestus.practices.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import de.tum.cit.aet.hephaestus.practices.PracticeAreaRepository;
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
    private PracticeAreaRepository practiceGoalRepository;

    private PracticesWorkspacePurgeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PracticesWorkspacePurgeAdapter(
            practiceFindingRepository,
            practiceRepository,
            practiceGoalRepository
        );
    }

    @Test
    void deleteWorkspaceData_deletesAllPracticesData() {
        Long workspaceId = 789L;

        adapter.deleteWorkspaceData(workspaceId);

        // Then — findings, practices, and goals are all removed.
        verify(practiceFindingRepository).deleteAllByPracticeWorkspaceId(workspaceId);
        verify(practiceRepository).deleteAllByWorkspaceId(workspaceId);
        verify(practiceGoalRepository).deleteAllByWorkspaceId(workspaceId);
    }

    @Test
    @DisplayName("runs before default-order developers")
    void getOrder_returnsNegativeValue() {
        // The adapter should run early, before other developers
        assertThat(adapter.getOrder()).isLessThan(0);
    }
}
