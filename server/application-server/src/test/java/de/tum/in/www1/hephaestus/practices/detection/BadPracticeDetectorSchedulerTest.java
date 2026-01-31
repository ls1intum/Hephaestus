package de.tum.in.www1.hephaestus.practices.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.practices.DetectionProperties;
import de.tum.in.www1.hephaestus.practices.spi.BadPracticeNotificationSender;
import de.tum.in.www1.hephaestus.practices.spi.UserRoleChecker;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class BadPracticeDetectorSchedulerTest {

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private PullRequestBadPracticeDetector pullRequestBadPracticeDetector;

    @Mock
    private BadPracticeNotificationSender notificationSender;

    @Mock
    private UserRoleChecker userRoleChecker;

    @Mock
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private ScheduledFuture<?> scheduledFuture1;

    @Mock
    private ScheduledFuture<?> scheduledFuture2;

    @Mock
    private ScheduledFuture<?> scheduledFuture3;

    private BadPracticeDetectorScheduler scheduler;

    @BeforeEach
    void setUp() {
        var detectionProperties = new DetectionProperties(true, null);
        scheduler = new BadPracticeDetectorScheduler(
            taskScheduler,
            pullRequestBadPracticeDetector,
            notificationSender,
            userRoleChecker,
            repositoryToMonitorRepository,
            workspaceRepository,
            detectionProperties
        );
    }

    @Test
    void cancelScheduledTasksForPullRequests_withNullCollection_returnsZero() {
        int result = scheduler.cancelScheduledTasksForPullRequests(null);
        assertThat(result).isZero();
    }

    @Test
    void cancelScheduledTasksForPullRequests_withEmptyCollection_returnsZero() {
        int result = scheduler.cancelScheduledTasksForPullRequests(Collections.emptyList());
        assertThat(result).isZero();
    }

    @Test
    void cancelScheduledTasksForPullRequests_withNoMatchingTasks_returnsZero() {
        // Given - no tasks scheduled for PR IDs 100, 200
        List<Long> prIds = List.of(100L, 200L);

        // When
        int result = scheduler.cancelScheduledTasksForPullRequests(prIds);

        // Then
        assertThat(result).isZero();
    }

    @Test
    void cancelScheduledTasksForPullRequests_cancelsActiveTasksAndRemovesFromMap() throws Exception {
        // Given - inject tasks into the internal map
        Map<Long, List<ScheduledFuture<?>>> scheduledTasks = getScheduledTasksMap();

        CopyOnWriteArrayList<ScheduledFuture<?>> tasksForPr1 = new CopyOnWriteArrayList<>();
        tasksForPr1.add(scheduledFuture1);
        tasksForPr1.add(scheduledFuture2);
        scheduledTasks.put(1L, tasksForPr1);

        CopyOnWriteArrayList<ScheduledFuture<?>> tasksForPr2 = new CopyOnWriteArrayList<>();
        tasksForPr2.add(scheduledFuture3);
        scheduledTasks.put(2L, tasksForPr2);

        // scheduledFuture1 is active and can be cancelled
        when(scheduledFuture1.isDone()).thenReturn(false);
        when(scheduledFuture1.isCancelled()).thenReturn(false);
        when(scheduledFuture1.cancel(false)).thenReturn(true);

        // scheduledFuture2 is already done
        when(scheduledFuture2.isDone()).thenReturn(true);

        // scheduledFuture3 is active and can be cancelled
        when(scheduledFuture3.isDone()).thenReturn(false);
        when(scheduledFuture3.isCancelled()).thenReturn(false);
        when(scheduledFuture3.cancel(false)).thenReturn(true);

        // When
        int result = scheduler.cancelScheduledTasksForPullRequests(List.of(1L, 2L));

        // Then
        assertThat(result).isEqualTo(2); // Only 2 active tasks were cancelled
        verify(scheduledFuture1).cancel(false);
        verify(scheduledFuture2, never()).cancel(anyBoolean());
        verify(scheduledFuture3).cancel(false);

        // Verify entries were removed from the map
        assertThat(scheduledTasks).doesNotContainKey(1L);
        assertThat(scheduledTasks).doesNotContainKey(2L);
    }

    @Test
    void cancelScheduledTasksForPullRequests_leavesUnrelatedTasksIntact() throws Exception {
        // Given - inject tasks into the internal map
        Map<Long, List<ScheduledFuture<?>>> scheduledTasks = getScheduledTasksMap();

        CopyOnWriteArrayList<ScheduledFuture<?>> tasksForPr1 = new CopyOnWriteArrayList<>();
        tasksForPr1.add(scheduledFuture1);
        scheduledTasks.put(1L, tasksForPr1);

        CopyOnWriteArrayList<ScheduledFuture<?>> tasksForPr99 = new CopyOnWriteArrayList<>();
        tasksForPr99.add(scheduledFuture2);
        scheduledTasks.put(99L, tasksForPr99);

        when(scheduledFuture1.isDone()).thenReturn(false);
        when(scheduledFuture1.isCancelled()).thenReturn(false);
        when(scheduledFuture1.cancel(false)).thenReturn(true);

        // When - only cancel tasks for PR ID 1
        scheduler.cancelScheduledTasksForPullRequests(List.of(1L));

        // Then
        assertThat(scheduledTasks).doesNotContainKey(1L);
        assertThat(scheduledTasks).containsKey(99L); // Unrelated task remains
        verifyNoInteractions(scheduledFuture2);
    }

    @SuppressWarnings("unchecked")
    private Map<Long, List<ScheduledFuture<?>>> getScheduledTasksMap() throws Exception {
        Field field = BadPracticeDetectorScheduler.class.getDeclaredField("scheduledTasks");
        field.setAccessible(true);
        return (Map<Long, List<ScheduledFuture<?>>>) field.get(scheduler);
    }
}
