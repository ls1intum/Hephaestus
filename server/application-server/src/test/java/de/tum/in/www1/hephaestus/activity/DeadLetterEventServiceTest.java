package de.tum.in.www1.hephaestus.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.tum.in.www1.hephaestus.activity.DeadLetterEventService.DeadLetterStats;
import de.tum.in.www1.hephaestus.activity.DeadLetterEventService.RetryResult;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for DeadLetterEventService.
 *
 * <p>Tests the recovery workflow: finding pending dead letters,
 * retrying them, tracking retry counts, and discarding unrecoverable events.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DeadLetterEventServiceTest {

    @Mock
    private DeadLetterEventRepository deadLetterRepository;

    @Mock
    private ActivityEventService activityEventService;

    private DeadLetterEventService service;

    @BeforeEach
    void setUp() {
        service = new DeadLetterEventService(deadLetterRepository, activityEventService);
    }

    // ========================================================================
    // Find Operations
    // ========================================================================

    @Nested
    @DisplayName("findPendingForRetry")
    class FindPendingForRetryTests {

        @Test
        @DisplayName("delegates to repository with limit")
        void findPendingForRetry_delegatesToRepository() {
            // Arrange
            DeadLetterEvent event = createDeadLetter();
            when(deadLetterRepository.findPendingForRetry(10)).thenReturn(List.of(event));

            // Act
            List<DeadLetterEvent> result = service.findPendingForRetry(10);

            // Assert
            assertThat(result).hasSize(1);
            verify(deadLetterRepository).findPendingForRetry(10);
        }

        @Test
        @DisplayName("returns empty list when no pending events")
        void findPendingForRetry_emptyWhenNoPending() {
            // Arrange
            when(deadLetterRepository.findPendingForRetry(10)).thenReturn(List.of());

            // Act
            List<DeadLetterEvent> result = service.findPendingForRetry(10);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findById")
    class FindByIdTests {

        @Test
        @DisplayName("returns event when found")
        void findById_returnsEvent() {
            // Arrange
            UUID id = UUID.randomUUID();
            DeadLetterEvent event = createDeadLetter();
            when(deadLetterRepository.findById(id)).thenReturn(Optional.of(event));

            // Act
            DeadLetterEvent result = service.findById(id);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getEventType()).isEqualTo(ActivityEventType.PULL_REQUEST_OPENED);
        }

        @Test
        @DisplayName("throws EntityNotFoundException when not found")
        void findById_throwsWhenNotFound() {
            // Arrange
            UUID id = UUID.randomUUID();
            when(deadLetterRepository.findById(id)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("DeadLetterEvent");
        }
    }

    // ========================================================================
    // Retry Operations
    // ========================================================================

    @Nested
    @DisplayName("retry")
    class RetryTests {

        @Test
        @DisplayName("marks event as resolved on successful retry")
        void retry_success_marksResolved() {
            // Arrange
            UUID id = UUID.randomUUID();
            DeadLetterEvent event = createDeadLetter();
            when(deadLetterRepository.findById(id)).thenReturn(Optional.of(event));
            when(
                activityEventService.record(any(), any(), any(), any(), any(), any(), any(), anyDouble(), any(), any())
            ).thenReturn(true);

            // Act
            RetryResult result = service.retry(id);

            // Assert
            assertThat(result.success()).isTrue();
            assertThat(result.message()).contains("successfully recorded");
            assertThat(event.getStatus()).isEqualTo(DeadLetterEvent.Status.RESOLVED);
            verify(deadLetterRepository).save(event);
        }

        @Test
        @DisplayName("treats duplicate as success and marks resolved")
        void retry_duplicate_treatedAsSuccess() {
            // Arrange
            UUID id = UUID.randomUUID();
            DeadLetterEvent event = createDeadLetter();
            when(deadLetterRepository.findById(id)).thenReturn(Optional.of(event));
            when(
                activityEventService.record(any(), any(), any(), any(), any(), any(), any(), anyDouble(), any(), any())
            ).thenReturn(false); // Duplicate returns false

            // Act
            RetryResult result = service.retry(id);

            // Assert
            assertThat(result.success()).isTrue();
            assertThat(result.message()).contains("already recorded");
            assertThat(event.getStatus()).isEqualTo(DeadLetterEvent.Status.RESOLVED);
            verify(deadLetterRepository).save(event);
        }

        @Test
        @DisplayName("increments retry count on failure")
        void retry_failure_incrementsRetryCount() {
            // Arrange
            UUID id = UUID.randomUUID();
            DeadLetterEvent event = createDeadLetter();
            assertThat(event.getRetryCount()).isZero();
            when(deadLetterRepository.findById(id)).thenReturn(Optional.of(event));
            when(
                activityEventService.record(any(), any(), any(), any(), any(), any(), any(), anyDouble(), any(), any())
            ).thenThrow(new RuntimeException("Database error"));

            // Act
            RetryResult result = service.retry(id);

            // Assert
            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("Retry failed");
            assertThat(event.getRetryCount()).isEqualTo(1);
            assertThat(event.getStatus()).isEqualTo(DeadLetterEvent.Status.PENDING);
            verify(deadLetterRepository).save(event);
        }

        @Test
        @DisplayName("returns failure for non-pending status")
        void retry_nonPendingStatus_returnsFalse() {
            // Arrange
            UUID id = UUID.randomUUID();
            DeadLetterEvent event = createDeadLetter();
            event.markResolved("Previously resolved");
            when(deadLetterRepository.findById(id)).thenReturn(Optional.of(event));

            // Act
            RetryResult result = service.retry(id);

            // Assert
            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("not in PENDING status");
            verify(activityEventService, never()).record(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyDouble(),
                any(),
                any()
            );
        }
    }

    // ========================================================================
    // Discard Operations
    // ========================================================================

    @Nested
    @DisplayName("discard")
    class DiscardTests {

        @Test
        @DisplayName("marks event as discarded with reason")
        void discard_marksDiscarded() {
            // Arrange
            UUID id = UUID.randomUUID();
            DeadLetterEvent event = createDeadLetter();
            when(deadLetterRepository.findById(id)).thenReturn(Optional.of(event));

            // Act
            service.discard(id, "Unrecoverable data corruption");

            // Assert
            assertThat(event.getStatus()).isEqualTo(DeadLetterEvent.Status.DISCARDED);
            assertThat(event.getResolutionNotes()).isEqualTo("Unrecoverable data corruption");
            assertThat(event.getResolvedAt()).isNotNull();
            verify(deadLetterRepository).save(event);
        }

        @Test
        @DisplayName("throws when event not found")
        void discard_throwsWhenNotFound() {
            // Arrange
            UUID id = UUID.randomUUID();
            when(deadLetterRepository.findById(id)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.discard(id, "reason")).isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ========================================================================
    // Statistics
    // ========================================================================

    @Nested
    @DisplayName("getStats")
    class GetStatsTests {

        @Test
        @DisplayName("aggregates counts by status and event type")
        void getStats_aggregatesCounts() {
            // Arrange
            when(deadLetterRepository.countByStatus(DeadLetterEvent.Status.PENDING)).thenReturn(5L);
            when(deadLetterRepository.countByStatus(DeadLetterEvent.Status.RESOLVED)).thenReturn(10L);
            when(deadLetterRepository.countByStatus(DeadLetterEvent.Status.DISCARDED)).thenReturn(2L);
            when(deadLetterRepository.countPendingByEventType()).thenReturn(
                List.of(
                    new Object[] { ActivityEventType.PULL_REQUEST_OPENED, 3L },
                    new Object[] { ActivityEventType.REVIEW_APPROVED, 2L }
                )
            );

            // Act
            DeadLetterStats stats = service.getStats();

            // Assert
            assertThat(stats.pending()).isEqualTo(5L);
            assertThat(stats.resolved()).isEqualTo(10L);
            assertThat(stats.discarded()).isEqualTo(2L);
            assertThat(stats.byEventType()).containsEntry("PULL_REQUEST_OPENED", 3L);
            assertThat(stats.byEventType()).containsEntry("REVIEW_APPROVED", 2L);
        }

        @Test
        @DisplayName("returns zero counts when empty")
        void getStats_zeroWhenEmpty() {
            // Arrange
            when(deadLetterRepository.countByStatus(any())).thenReturn(0L);
            when(deadLetterRepository.countPendingByEventType()).thenReturn(List.of());

            // Act
            DeadLetterStats stats = service.getStats();

            // Assert
            assertThat(stats.pending()).isZero();
            assertThat(stats.resolved()).isZero();
            assertThat(stats.discarded()).isZero();
            assertThat(stats.byEventType()).isEmpty();
        }
    }

    // ========================================================================
    // Test Helpers
    // ========================================================================

    private DeadLetterEvent createDeadLetter() {
        return DeadLetterEvent.builder()
            .workspaceId(1L)
            .eventType(ActivityEventType.PULL_REQUEST_OPENED)
            .occurredAt(Instant.now())
            .targetType("pull_request")
            .targetId(100L)
            .xp(1.0)
            .sourceSystem("github")
            .errorMessage("Test error")
            .errorType("java.lang.RuntimeException")
            .createdAt(Instant.now())
            .status(DeadLetterEvent.Status.PENDING)
            .build();
    }
}
