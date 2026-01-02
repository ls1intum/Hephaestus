package de.tum.in.www1.hephaestus.activity;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.annotation.Observed;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job for automated integrity verification of activity events.
 *
 * <p>Periodically samples random events and verifies their content hash
 * to detect potential tampering or data corruption.
 *
 * <h3>Alerting</h3>
 * <p>Corrupted events are logged at ERROR level and counted via metrics.
 * Configure alerts on the {@code activity.integrity.corrupted} metric.
 *
 * @see ActivityEvent#verifyIntegrity()
 */
@Component
public class ActivityIntegrityScheduler implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(ActivityIntegrityScheduler.class);
    private static final long MAX_ALLOWED_SILENCE_MILLIS = 2 * 60 * 60 * 1000; // 2 hours

    private final ActivityEventRepository eventRepository;
    private final Counter corruptedEventsCounter;
    private final Counter verifiedEventsCounter;
    private final Timer verificationTimer;
    private final int sampleSize;
    private final boolean enabled;

    // Track last run for health check
    private final AtomicReference<Instant> lastRunTimestamp = new AtomicReference<>();
    private final AtomicLong lastCorruptedCount = new AtomicLong(0);
    private final AtomicLong lastVerifiedCount = new AtomicLong(0);

    public ActivityIntegrityScheduler(
        ActivityEventRepository eventRepository,
        MeterRegistry meterRegistry,
        @Value("${hephaestus.activity.integrity.sample-size:100}") int sampleSize,
        @Value("${hephaestus.activity.integrity.enabled:true}") boolean enabled
    ) {
        this.eventRepository = eventRepository;
        this.sampleSize = sampleSize;
        this.enabled = enabled;
        this.corruptedEventsCounter = Counter.builder("activity.integrity.corrupted")
            .description("Number of events with failed integrity checks")
            .register(meterRegistry);
        this.verifiedEventsCounter = Counter.builder("activity.integrity.verified")
            .description("Number of events successfully verified")
            .register(meterRegistry);
        this.verificationTimer = Timer.builder("activity.integrity.duration")
            .description("Duration of integrity verification job")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);

        // Gauges for current state visibility
        Gauge.builder("activity.integrity.last_run_epoch_seconds", lastRunTimestamp, ref ->
            ref.get() != null ? ref.get().getEpochSecond() : 0
        )
            .description("Epoch seconds of last integrity check run")
            .register(meterRegistry);
        Gauge.builder("activity.integrity.last_corrupted_count", lastCorruptedCount, AtomicLong::get)
            .description("Number of corrupted events in last run")
            .register(meterRegistry);
        Gauge.builder("activity.integrity.last_verified_count", lastVerifiedCount, AtomicLong::get)
            .description("Number of verified events in last run")
            .register(meterRegistry);
    }

    /**
     * Verify integrity of a random sample of activity events.
     *
     * <p>Runs every hour by default. Configure via:
     * {@code hephaestus.activity.integrity.cron}
     */
    @Observed(name = "activity.integrity.verify", contextualName = "verify-event-integrity")
    @Scheduled(cron = "${hephaestus.activity.integrity.cron:0 0 * * * *}")
    public void verifyEventIntegrity() {
        if (!enabled) {
            logger.debug("Integrity verification disabled");
            return;
        }

        logger.info("activity.integrity.started sampleSize={}", sampleSize);
        Instant startTime = Instant.now();

        Timer.Sample timerSample = Timer.start();
        try {
            List<ActivityEvent> sample = eventRepository.findRandomSample(PageRequest.of(0, sampleSize));

            if (sample.isEmpty()) {
                logger.debug("No events to verify");
                lastRunTimestamp.set(Instant.now());
                lastVerifiedCount.set(0);
                lastCorruptedCount.set(0);
                return;
            }

            List<ActivityEvent> corrupted = sample
                .stream()
                .filter(event -> !event.verifyIntegrity())
                .toList();

            int verifiedCount = sample.size() - corrupted.size();
            verifiedEventsCounter.increment(verifiedCount);
            lastVerifiedCount.set(verifiedCount);
            lastCorruptedCount.set(corrupted.size());
            lastRunTimestamp.set(Instant.now());

            if (!corrupted.isEmpty()) {
                corruptedEventsCounter.increment(corrupted.size());
                for (ActivityEvent event : corrupted) {
                    logger.error(
                        "INTEGRITY_VIOLATION eventId={} eventKey={} eventType={} targetId={}",
                        event.getId(),
                        event.getEventKey(),
                        event.getEventType(),
                        event.getTargetId()
                    );
                }
                logger.error(
                    "activity.integrity.failed corruptedCount={} sampledCount={} corruptionRate={}",
                    corrupted.size(),
                    sample.size(),
                    String.format("%.2f%%", (corrupted.size() * 100.0) / sample.size())
                );
            } else {
                logger.info(
                    "activity.integrity.passed verifiedCount={} durationMs={}",
                    verifiedCount,
                    java.time.Duration.between(startTime, Instant.now()).toMillis()
                );
            }
        } finally {
            timerSample.stop(verificationTimer);
        }
    }

    @Override
    public Health health() {
        if (!enabled) {
            return Health.up().withDetail("status", "disabled").build();
        }

        Instant lastRun = lastRunTimestamp.get();
        if (lastRun == null) {
            return Health.unknown()
                .withDetail("status", "never_run")
                .withDetail("message", "Integrity scheduler has not run yet")
                .build();
        }

        long millisSinceLastRun = Instant.now().toEpochMilli() - lastRun.toEpochMilli();
        long corruptedCount = lastCorruptedCount.get();

        Health.Builder builder = corruptedCount > 0 ? Health.down() : Health.up();

        builder
            .withDetail("lastRun", lastRun.toString())
            .withDetail("millisSinceLastRun", millisSinceLastRun)
            .withDetail("lastCorruptedCount", corruptedCount)
            .withDetail("lastVerifiedCount", lastVerifiedCount.get());

        if (millisSinceLastRun > MAX_ALLOWED_SILENCE_MILLIS) {
            return Health.down()
                .withDetail("status", "stale")
                .withDetail("message", "Integrity check has not run in over 2 hours")
                .withDetail("lastRun", lastRun.toString())
                .build();
        }

        return builder.build();
    }
}
