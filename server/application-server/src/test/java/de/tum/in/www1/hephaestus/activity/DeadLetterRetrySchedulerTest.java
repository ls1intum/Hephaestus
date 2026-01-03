package de.tum.in.www1.hephaestus.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.tum.in.www1.hephaestus.activity.DeadLetterEventService.RetryResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

/**
 * Unit tests for DeadLetterRetryScheduler.
 *
 * <p>Tests the automated retry logic including batch processing,
 * age filtering, max retry limits, and health indicator.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DeadLetterRetrySchedulerTest {

    @Mock
    private DeadLetterEventService deadLetterService;

    @Mock
    private DeadLetterEventRepository deadLetterRepository;

    private MeterRegistry meterRegistry;
    private DeadLetterRetryScheduler scheduler;

    private static final int BATCH_SIZE = 10;
    private static final int MIN_AGE_MINUTES = 5;
    private static final int MAX_RETRY_ATTEMPTS = 5;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        scheduler = new DeadLetterRetryScheduler(
            deadLetterService,
            deadLetterRepository,
            meterRegistry,
            true, // enabled
            BATCH_SIZE,
            MIN_AGE_MINUTES,
            MAX_RETRY_ATTEMPTS
        );
    }

    // ========================================================================
    // Disabled State
    // ========================================================================

    @Nested
    @DisplayName("when disabled")
    class DisabledTests {

        @BeforeEach
        void setUp() {
            scheduler = new DeadLetterRetryScheduler(
                deadLetterService,
                deadLetterRepository,
                meterRegistry,
                false, // disabled
                BATCH_SIZE,
                MIN_AGE_MINUTES,
                MAX_RETRY_ATTEMPTS
            );
        }

        @Test
        @DisplayName("skips processing when disabled")
        void retryPendingDeadLetters_skipsWhenDisabled() {
            // Act
            scheduler.retryPendingDeadLetters();

            // Assert
            verify(deadLetterService, never()).findPendingForRetry(anyInt());
        }

        @Test
        @DisplayName("health returns up with disabled status")
        void health_returnsDisabledStatus() {
            // Act
            Health health = scheduler.health();

            // Assert
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("status", "disabled");
        }
    }

    // ========================================================================
    // Empty Queue
    // ========================================================================

    @Nested
    @DisplayName("when queue is empty")
    class EmptyQueueTests {

        @Test
        @DisplayName("handles empty queue gracefully")
        void retryPendingDeadLetters_handlesEmptyQueue() {
            // Arrange
            when(deadLetterService.findPendingForRetry(BATCH_SIZE)).thenReturn(List.of());

            // Act
            scheduler.retryPendingDeadLetters();

            // Assert
            verify(deadLetterService, never()).retry(any());
        }
    }

    // ========================================================================
    // Retry Processing
    // ========================================================================

    @Nested
    @DisplayName("retry processing")
    class RetryProcessingTests {

        @Test
        @DisplayName("retries old pending events successfully")
        void retryPendingDeadLetters_retriesOldEvents() {
            // Arrange
            DeadLetterEvent oldEvent = createDeadLetter(
                Instant.now().minus(10, ChronoUnit.MINUTES), // Old enough
                0 // First attempt
            );
            when(deadLetterService.findPendingForRetry(BATCH_SIZE)).thenReturn(List.of(oldEvent));
            when(deadLetterService.retry(oldEvent.getId())).thenReturn(new RetryResult(true, "Success"));

            // Act
            scheduler.retryPendingDeadLetters();

            // Assert
            verify(deadLetterService).retry(oldEvent.getId());
            assertThat(meterRegistry.counter("activity.dead_letter.auto_retry.attempted").count()).isEqualTo(1);
            assertThat(meterRegistry.counter("activity.dead_letter.auto_retry.success").count()).isEqualTo(1);
        }

        @Test
        @DisplayName("skips recent events (backoff)")
        void retryPendingDeadLetters_skipsRecentEvents() {
            // Arrange
            DeadLetterEvent recentEvent = createDeadLetter(
                Instant.now().minus(1, ChronoUnit.MINUTES), // Too recent
                0
            );
            when(deadLetterService.findPendingForRetry(BATCH_SIZE)).thenReturn(List.of(recentEvent));

            // Act
            scheduler.retryPendingDeadLetters();

            // Assert
            verify(deadLetterService, never()).retry(any());
        }

        @Test
        @DisplayName("auto-discards events exceeding max retry attempts")
        void retryPendingDeadLetters_autoDiscardsMaxRetries() {
            // Arrange
            DeadLetterEvent exhaustedEvent = createDeadLetter(
                Instant.now().minus(10, ChronoUnit.MINUTES),
                MAX_RETRY_ATTEMPTS // Already at max
            );
            when(deadLetterService.findPendingForRetry(BATCH_SIZE)).thenReturn(List.of(exhaustedEvent));

            // Act
            scheduler.retryPendingDeadLetters();

            // Assert
            verify(deadLetterService, never()).retry(any());
            verify(deadLetterService).discard(eq(exhaustedEvent.getId()), contains("Auto-discarded"));
            assertThat(meterRegistry.counter("activity.dead_letter.auto_retry.skipped").count()).isEqualTo(1);
        }

        @Test
        @DisplayName("tracks failed retries")
        void retryPendingDeadLetters_tracksFailures() {
            // Arrange
            DeadLetterEvent event = createDeadLetter(Instant.now().minus(10, ChronoUnit.MINUTES), 0);
            when(deadLetterService.findPendingForRetry(BATCH_SIZE)).thenReturn(List.of(event));
            when(deadLetterService.retry(event.getId())).thenReturn(new RetryResult(false, "Database error"));

            // Act
            scheduler.retryPendingDeadLetters();

            // Assert
            verify(deadLetterService).retry(event.getId());
            assertThat(meterRegistry.counter("activity.dead_letter.auto_retry.attempted").count()).isEqualTo(1);
            assertThat(meterRegistry.counter("activity.dead_letter.auto_retry.failed").count()).isEqualTo(1);
        }

        @Test
        @DisplayName("processes mixed batch correctly")
        void retryPendingDeadLetters_processesMixedBatch() {
            // Arrange
            DeadLetterEvent oldValid = createDeadLetter(Instant.now().minus(10, ChronoUnit.MINUTES), 0);
            DeadLetterEvent tooRecent = createDeadLetter(Instant.now().minus(1, ChronoUnit.MINUTES), 0);
            DeadLetterEvent exhausted = createDeadLetter(
                Instant.now().minus(10, ChronoUnit.MINUTES),
                MAX_RETRY_ATTEMPTS
            );

            when(deadLetterService.findPendingForRetry(BATCH_SIZE)).thenReturn(List.of(oldValid, tooRecent, exhausted));
            when(deadLetterService.retry(oldValid.getId())).thenReturn(new RetryResult(true, "Success"));

            // Act
            scheduler.retryPendingDeadLetters();

            // Assert
            verify(deadLetterService, times(1)).retry(any()); // Only oldValid
            verify(deadLetterService, times(1)).discard(any(), any()); // Only exhausted
            assertThat(meterRegistry.counter("activity.dead_letter.auto_retry.success").count()).isEqualTo(1);
            assertThat(meterRegistry.counter("activity.dead_letter.auto_retry.skipped").count()).isEqualTo(1);
        }
    }

    // ========================================================================
    // Health Indicator
    // ========================================================================

    @Nested
    @DisplayName("health indicator")
    class HealthIndicatorTests {

        @Test
        @DisplayName("returns up before first run")
        void health_beforeFirstRun() {
            // Arrange
            when(deadLetterRepository.countByStatus(DeadLetterEvent.Status.PENDING)).thenReturn(0L);

            // Act
            Health health = scheduler.health();

            // Assert
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("status", "not_run_yet");
        }

        @Test
        @DisplayName("returns up after successful run")
        void health_afterSuccessfulRun() {
            // Arrange
            when(deadLetterService.findPendingForRetry(BATCH_SIZE)).thenReturn(List.of());
            when(deadLetterRepository.countByStatus(DeadLetterEvent.Status.PENDING)).thenReturn(0L);

            // Act
            scheduler.retryPendingDeadLetters();
            Health health = scheduler.health();

            // Assert
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("status", "running");
            assertThat(health.getDetails()).containsEntry("pending", 0L);
        }

        @Test
        @DisplayName("includes last run statistics")
        void health_includesStatistics() {
            // Arrange
            DeadLetterEvent event = createDeadLetter(Instant.now().minus(10, ChronoUnit.MINUTES), 0);
            when(deadLetterService.findPendingForRetry(BATCH_SIZE)).thenReturn(List.of(event));
            when(deadLetterService.retry(event.getId())).thenReturn(new RetryResult(true, "Success"));
            when(deadLetterRepository.countByStatus(DeadLetterEvent.Status.PENDING)).thenReturn(0L);

            // Act
            scheduler.retryPendingDeadLetters();
            Health health = scheduler.health();

            // Assert
            assertThat(health.getDetails()).containsEntry("lastSuccess", 1L);
            assertThat(health.getDetails()).containsEntry("lastFailed", 0L);
        }
    }

    // ========================================================================
    // Test Helpers
    // ========================================================================

    private DeadLetterEvent createDeadLetter(Instant createdAt, int retryCount) {
        return DeadLetterEvent.builder()
            .id(UUID.randomUUID())
            .workspaceId(1L)
            .eventType(ActivityEventType.PULL_REQUEST_OPENED)
            .occurredAt(Instant.now())
            .targetType("pull_request")
            .targetId(100L)
            .xp(1.0)
            .sourceSystem("github")
            .errorMessage("Test error")
            .errorType("java.lang.RuntimeException")
            .createdAt(createdAt)
            .retryCount(retryCount)
            .status(DeadLetterEvent.Status.PENDING)
            .build();
    }
}
