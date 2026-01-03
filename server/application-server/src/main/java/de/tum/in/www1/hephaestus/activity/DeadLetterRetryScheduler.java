package de.tum.in.www1.hephaestus.activity;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.annotation.Observed;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Automated retry scheduler for dead letter events.
 *
 * <p>Periodically attempts to retry pending dead letters to recover from
 * transient failures. This reduces manual intervention for temporary issues.
 *
 * <h3>Retry Strategy</h3>
 * <ul>
 *   <li>Processes oldest dead letters first (FIFO)</li>
 *   <li>Limits batch size to prevent overwhelming the system</li>
 *   <li>Skips events that have exceeded max retry attempts</li>
 *   <li>Respects backoff by checking event age</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * <pre>
 * hephaestus:
 *   activity:
 *     dead-letter:
 *       auto-retry:
 *         enabled: true
 *         batch-size: 10
 *         min-age-minutes: 5
 *         max-retry-attempts: 5
 *         cron: "0 *&#47;5 * * * *"  # Every 5 minutes
 * </pre>
 *
 * @see DeadLetterEventService
 * @see DeadLetterEvent
 */
@Component
public class DeadLetterRetryScheduler implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(DeadLetterRetryScheduler.class);
    private static final long MAX_ALLOWED_SILENCE_MILLIS = 15 * 60 * 1000; // 15 minutes

    private final DeadLetterEventService deadLetterService;
    private final DeadLetterEventRepository deadLetterRepository;
    private final Counter retriedCounter;
    private final Counter successCounter;
    private final Counter failedCounter;
    private final Counter skippedCounter;
    private final int batchSize;
    private final int minAgeMinutes;
    private final int maxRetryAttempts;
    private final boolean enabled;

    // Track state for health check
    private final AtomicReference<Instant> lastRunTimestamp = new AtomicReference<>();
    private final AtomicLong lastSuccessCount = new AtomicLong(0);
    private final AtomicLong lastFailedCount = new AtomicLong(0);

    public DeadLetterRetryScheduler(
        DeadLetterEventService deadLetterService,
        DeadLetterEventRepository deadLetterRepository,
        MeterRegistry meterRegistry,
        @Value("${hephaestus.activity.dead-letter.auto-retry.enabled:true}") boolean enabled,
        @Value("${hephaestus.activity.dead-letter.auto-retry.batch-size:10}") int batchSize,
        @Value("${hephaestus.activity.dead-letter.auto-retry.min-age-minutes:5}") int minAgeMinutes,
        @Value("${hephaestus.activity.dead-letter.auto-retry.max-retry-attempts:5}") int maxRetryAttempts
    ) {
        this.deadLetterService = deadLetterService;
        this.deadLetterRepository = deadLetterRepository;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.minAgeMinutes = minAgeMinutes;
        this.maxRetryAttempts = maxRetryAttempts;

        this.retriedCounter = Counter.builder("activity.dead_letter.auto_retry.attempted")
            .description("Number of dead letters attempted to auto-retry")
            .register(meterRegistry);
        this.successCounter = Counter.builder("activity.dead_letter.auto_retry.success")
            .description("Number of dead letters successfully retried")
            .register(meterRegistry);
        this.failedCounter = Counter.builder("activity.dead_letter.auto_retry.failed")
            .description("Number of dead letters that failed auto-retry")
            .register(meterRegistry);
        this.skippedCounter = Counter.builder("activity.dead_letter.auto_retry.skipped")
            .description("Number of dead letters skipped (max attempts exceeded)")
            .register(meterRegistry);

        // Gauges for observability
        Gauge.builder("activity.dead_letter.auto_retry.last_run_epoch_seconds", lastRunTimestamp, ref ->
            ref.get() != null ? ref.get().getEpochSecond() : 0
        )
            .description("Epoch seconds of last auto-retry run")
            .register(meterRegistry);
        Gauge.builder("activity.dead_letter.auto_retry.last_success_count", lastSuccessCount, AtomicLong::get)
            .description("Number of successful retries in last run")
            .register(meterRegistry);
        Gauge.builder("activity.dead_letter.auto_retry.last_failed_count", lastFailedCount, AtomicLong::get)
            .description("Number of failed retries in last run")
            .register(meterRegistry);
    }

    /**
     * Automatically retry pending dead letters.
     *
     * <p>Runs every 5 minutes by default. Configure via:
     * {@code hephaestus.activity.dead-letter.auto-retry.cron}
     */
    @Observed(name = "activity.dead_letter.auto_retry", contextualName = "auto-retry-dead-letters")
    @Scheduled(cron = "${hephaestus.activity.dead-letter.auto-retry.cron:0 */5 * * * *}")
    public void retryPendingDeadLetters() {
        if (!enabled) {
            logger.debug("Dead letter auto-retry disabled");
            return;
        }

        List<DeadLetterEvent> pending = deadLetterService.findPendingForRetry(batchSize);
        if (pending.isEmpty()) {
            lastRunTimestamp.set(Instant.now());
            lastSuccessCount.set(0);
            lastFailedCount.set(0);
            logger.debug("No pending dead letters to retry");
            return;
        }

        logger.info("activity.dead_letter.auto_retry.started batchSize={}", pending.size());

        Instant minCreatedAt = Instant.now().minus(Duration.ofMinutes(minAgeMinutes));
        long successCount = 0;
        long failedCount = 0;
        long skippedCount = 0;

        for (DeadLetterEvent event : pending) {
            // Skip events that are too recent (respect backoff)
            if (event.getCreatedAt().isAfter(minCreatedAt)) {
                logger.debug("Skipping recent dead letter {}: created {}", event.getId(), event.getCreatedAt());
                continue;
            }

            // Skip events that have exceeded max retry attempts
            if (event.getRetryCount() >= maxRetryAttempts) {
                skippedCount++;
                skippedCounter.increment();
                logger.warn(
                    "activity.dead_letter.max_retries_exceeded eventId={} retryCount={} eventType={}",
                    event.getId(),
                    event.getRetryCount(),
                    event.getEventType()
                );
                // Auto-discard after max attempts
                deadLetterService.discard(
                    event.getId(),
                    "Auto-discarded after " + maxRetryAttempts + " retry attempts"
                );
                continue;
            }

            retriedCounter.increment();
            DeadLetterEventService.RetryResult result = deadLetterService.retry(event.getId());

            if (result.success()) {
                successCount++;
                successCounter.increment();
                logger.info("activity.dead_letter.auto_retry.success eventId={}", event.getId());
            } else {
                failedCount++;
                failedCounter.increment();
                logger.warn(
                    "activity.dead_letter.auto_retry.failed eventId={} reason={}",
                    event.getId(),
                    result.message()
                );
            }
        }

        lastRunTimestamp.set(Instant.now());
        lastSuccessCount.set(successCount);
        lastFailedCount.set(failedCount);

        logger.info(
            "activity.dead_letter.auto_retry.completed success={} failed={} skipped={}",
            successCount,
            failedCount,
            skippedCount
        );
    }

    @Override
    public Health health() {
        Instant lastRun = lastRunTimestamp.get();
        long pending = deadLetterRepository.countByStatus(DeadLetterEvent.Status.PENDING);

        if (!enabled) {
            return Health.up().withDetail("status", "disabled").withDetail("pending", pending).build();
        }

        if (lastRun == null) {
            return Health.up().withDetail("status", "not_run_yet").withDetail("pending", pending).build();
        }

        long silenceMillis = System.currentTimeMillis() - lastRun.toEpochMilli();
        boolean isHealthy = silenceMillis < MAX_ALLOWED_SILENCE_MILLIS;

        Health.Builder builder = isHealthy ? Health.up() : Health.down();
        return builder
            .withDetail("status", isHealthy ? "running" : "stale")
            .withDetail("lastRun", lastRun.toString())
            .withDetail("silenceMinutes", silenceMillis / 60000)
            .withDetail("pending", pending)
            .withDetail("lastSuccess", lastSuccessCount.get())
            .withDetail("lastFailed", lastFailedCount.get())
            .build();
    }
}
