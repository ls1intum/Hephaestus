package de.tum.in.www1.hephaestus.practices.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import de.tum.in.www1.hephaestus.practices.PracticesPullRequestQueryRepository;
import de.tum.in.www1.hephaestus.practices.detection.BadPracticeDetectorScheduler;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PracticesWorkspacePurgeAdapterTest {

    @Mock
    private PracticesPullRequestQueryRepository pullRequestQueryRepository;

    @Mock
    private BadPracticeDetectorScheduler detectorScheduler;

    private PracticesWorkspacePurgeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PracticesWorkspacePurgeAdapter(pullRequestQueryRepository, detectorScheduler);
    }

    @Test
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
    void deleteWorkspaceData_withNoPullRequests_skipsSchedulerCall() {
        // Given
        Long workspaceId = 456L;
        when(pullRequestQueryRepository.findPullRequestIdsByWorkspaceId(workspaceId)).thenReturn(Collections.emptyList());

        // When
        adapter.deleteWorkspaceData(workspaceId);

        // Then
        verify(pullRequestQueryRepository).findPullRequestIdsByWorkspaceId(workspaceId);
        verifyNoInteractions(detectorScheduler);
    }

    @Test
    void getOrder_returnsNegativeValue() {
        // The adapter should run early, before other contributors
        assertThat(adapter.getOrder()).isLessThan(0);
    }
}
