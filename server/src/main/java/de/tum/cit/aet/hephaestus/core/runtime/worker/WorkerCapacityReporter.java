package de.tum.cit.aet.hephaestus.core.runtime.worker;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

import jakarta.annotation.PreDestroy;

/**
 * Periodic worker → hub capacity reporter. Cadence: {@code hephaestus.worker.heartbeat.interval}
 * (default 20s). Transient publish failures are counted and logged; the schedule survives them.
 */
public class WorkerCapacityReporter {

    private static final Logger log = LoggerFactory.getLogger(WorkerCapacityReporter.class);

    private final WorkerCapacityState state;
    private final WorkerControlPublisher publisher;
    private final Duration interval;
    private final ScheduledExecutorService scheduler;
    private final Counter sent;
    private final Counter failed;
    private volatile ScheduledFuture<?> task;

    public WorkerCapacityReporter(
        WorkerCapacityState state,
        WorkerControlPublisher publisher,
        WorkerProperties properties,
        ScheduledExecutorService workerScheduler,
        MeterRegistry meterRegistry
    ) {
        this.state = state;
        this.publisher = publisher;
        this.interval = properties.heartbeat().interval();
        this.scheduler = workerScheduler;
        this.sent = Counter.builder("worker.heartbeats.sent")
            .description("Worker → hub CapacityReport frames sent")
            .register(meterRegistry);
        this.failed = Counter.builder("worker.heartbeats.failed")
            .description("Worker → hub CapacityReport sends that threw")
            .register(meterRegistry);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    public void start() {
        log.info("Worker capacity reporter starting (interval={})", interval);
        long ms = Math.max(1, interval.toMillis());
        this.task = scheduler.scheduleWithFixedDelay(this::tick, 0L, ms, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stop() {
        ScheduledFuture<?> t = this.task;
        if (t != null) {
            t.cancel(false);
        }
    }

    void tick() {
        try {
            publisher.send(state.snapshot());
            sent.increment();
        } catch (Exception e) {
            failed.increment();
            log.warn("Failed to send CapacityReport: {}", e.getClass().getSimpleName(), e);
        }
    }
}
