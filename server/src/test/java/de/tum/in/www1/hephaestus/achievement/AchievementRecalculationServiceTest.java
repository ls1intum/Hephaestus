package de.tum.in.www1.hephaestus.achievement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import de.tum.in.www1.hephaestus.activity.ActivityEvent;
import de.tum.in.www1.hephaestus.activity.ActivityEventRepository;
import de.tum.in.www1.hephaestus.activity.ActivityEventType;
import de.tum.in.www1.hephaestus.activity.ActivitySavedEvent;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Unit tests for {@link AchievementRecalculationService}.
 */
class AchievementRecalculationServiceTest extends BaseUnitTest {

    @Mock
    private UserAchievementRepository userAchievementRepository;

    @Mock
    private ActivityEventRepository activityEventRepository;

    @Mock
    private AchievementService achievementService;

    @Mock
    private TransactionTemplate transactionTemplate;

    private AchievementRecalculationService service;
    private User testUser;

    @BeforeEach
    void setUp() {
        service = new AchievementRecalculationService(
            userAchievementRepository,
            activityEventRepository,
            achievementService,
            transactionTemplate
        );

        testUser = new User();
        testUser.setId(1L);
        testUser.setLogin("testuser");
    }

    private ActivityEvent createActivityEvent(ActivityEventType eventType) {
        Workspace workspace = new Workspace();
        workspace.setId(1L);

        return ActivityEvent.builder()
            .actor(testUser)
            .eventType(eventType)
            .occurredAt(Instant.now())
            .workspace(workspace)
            .targetType("pull_request")
            .targetId(100L)
            .eventKey(eventType.name() + ":100:" + System.nanoTime())
            .ingestedAt(Instant.now())
            .build();
    }

    @Nested
    @DisplayName("recalculateUserInternal")
    class RecalculateTests {

        @Test
        @DisplayName("wipes progress and replays events")
        void wipesAndReplays() {
            ActivityEvent event1 = createActivityEvent(ActivityEventType.PULL_REQUEST_MERGED);
            ActivityEvent event2 = createActivityEvent(ActivityEventType.COMMIT_CREATED);

            // TransactionTemplate.executeWithoutResult -> just run the consumer
            doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                var consumer = invocation.getArgument(0, java.util.function.Consumer.class);
                consumer.accept(null);
                return null;
            })
                .when(transactionTemplate)
                .executeWithoutResult(any());

            // TransactionTemplate.execute for the slice query
            when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
                var callback = invocation.getArgument(0);
                @SuppressWarnings("unchecked")
                var typedCallback = (org.springframework.transaction.support.TransactionCallback<Object>) callback;
                return typedCallback.doInTransaction(null);
            });

            // Return a single slice with 2 events (no next page)
            when(
                activityEventRepository.findSliceByActorIdOrderByOccurredAtAsc(eq(1L), any(Pageable.class))
            ).thenReturn(new SliceImpl<>(List.of(event1, event2), PageRequest.of(0, 500), false));

            service.recalculateUserInternal(testUser);

            // Verify delete was called
            verify(userAchievementRepository).deleteByUserId(1L);

            // Verify each event was replayed
            verify(achievementService, times(2)).checkAndUnlock(any(ActivitySavedEvent.class));
        }

        @Test
        @DisplayName("handles empty activity history")
        void handlesEmptyHistory() {
            doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                var consumer = invocation.getArgument(0, java.util.function.Consumer.class);
                consumer.accept(null);
                return null;
            })
                .when(transactionTemplate)
                .executeWithoutResult(any());

            when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
                var callback = invocation.getArgument(0);
                @SuppressWarnings("unchecked")
                var typedCallback = (org.springframework.transaction.support.TransactionCallback<Object>) callback;
                return typedCallback.doInTransaction(null);
            });

            when(
                activityEventRepository.findSliceByActorIdOrderByOccurredAtAsc(eq(1L), any(Pageable.class))
            ).thenReturn(new SliceImpl<>(List.of()));

            service.recalculateUserInternal(testUser);

            verify(userAchievementRepository).deleteByUserId(1L);
            verify(achievementService, never()).checkAndUnlock(any());
        }
    }
}
