package de.tum.in.www1.hephaestus.practices.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import de.tum.in.www1.hephaestus.practices.PracticeRepository;
import de.tum.in.www1.hephaestus.practices.PracticesPullRequestQueryRepository;
import de.tum.in.www1.hephaestus.practices.detection.BadPracticeDetectorScheduler;
import de.tum.in.www1.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@DisplayName("PracticesWorkspacePurgeAdapter")
class PracticesWorkspacePurgeAdapterTest extends BaseUnitTest {

    @Mock
    private PracticesPullRequestQueryRepository pullRequestQueryRepository;

    @Mock
    private BadPracticeDetectorScheduler detectorScheduler;

    @Mock
    private PracticeFindingRepository practiceFindingRepository;

    @Mock
    private PracticeRepository practiceRepository;

    private PracticesWorkspacePurgeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PracticesWorkspacePurgeAdapter(
            pullRequestQueryRepository,
            detectorScheduler,
            practiceFindingRepository,
            practiceRepository
        );
    }

    @Test
    @DisplayName("cancels scheduled tasks when PRs exist")
    void deleteWorkspaceData_withPullRequests_cancelsScheduledTasks() {
        // Given
        Long workspaceId = 123L;
        List<Long> prIds = List.of(1L, 2L, 3L);
        when(pullRequestQueryRepository.findPullRequestIdsByWorkspaceId(workspaceId)).thenReturn(prIds);
        when(detectorScheduler.cancelScheduledTasksForPullRequests(prIds)).thenReturn(2);

        // When
        adapter.deleteWorkspaceData(workspaceId);

        // Then
        verify(pullRequestQueryRepository).findPullRequestIdsByWorkspaceId(workspaceId);
        verify(detectorScheduler).cancelScheduledTasksForPullRequests(prIds);
    }

    @Test
    @DisplayName("skips scheduler when no PRs exist")
    void deleteWorkspaceData_withNoPullRequests_skipsSchedulerCall() {
        // Given
        Long workspaceId = 456L;
        when(pullRequestQueryRepository.findPullRequestIdsByWorkspaceId(workspaceId)).thenReturn(
            Collections.emptyList()
        );

        // When
        adapter.deleteWorkspaceData(workspaceId);

        // Then
        verify(pullRequestQueryRepository).findPullRequestIdsByWorkspaceId(workspaceId);
        verifyNoInteractions(detectorScheduler);
    }

    @Test
    @DisplayName("deletes both findings and practices for workspace")
    void deleteWorkspaceData_deletesFindingsAndPractices() {
        // Given
        Long workspaceId = 789L;
        when(pullRequestQueryRepository.findPullRequestIdsByWorkspaceId(workspaceId)).thenReturn(
            Collections.emptyList()
        );

        // When
        adapter.deleteWorkspaceData(workspaceId);

        // Then — both deletes called (explicit finding deletion is defense-in-depth; CASCADE also handles it)
        verify(practiceFindingRepository).deleteAllByPracticeWorkspaceId(workspaceId);
        verify(practiceRepository).deleteAllByWorkspaceId(workspaceId);
    }

    @Test
    @DisplayName("runs before default-order contributors")
    void getOrder_returnsNegativeValue() {
        // The adapter should run early, before other contributors
        assertThat(adapter.getOrder()).isLessThan(0);
    }
}
