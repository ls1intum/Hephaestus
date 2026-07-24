package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Queue-health gauges for the {@code agent_job} queue (#1368 hardening — pressure-test verdict Tier 1
 * #2). Sampled on a timer, never on a request path.
 *
 * <ul>
 *   <li>{@code agent.queue.depth} — COUNT of QUEUED jobs currently eligible to run
 *       ({@code available_at <= now()}). The basic backlog signal.</li>
 *   <li>{@code agent.queue.oldest_age_seconds} — age of the oldest eligible QUEUED job; 0 when the
 *       queue is empty. THE canonical queue-health signal (the judoscale approach: depth alone can't
 *       distinguish "briefly busy" from "stuck" — age can).</li>
 *   <li>{@code agent.queue.running} — COUNT of RUNNING jobs fleet-wide.</li>
 * </ul>
 *
 * <p>All three come from ONE query ({@link AgentJobRepository#queueHealthSnapshot}, #1368 fix wave,
 * finding #12) — three separate COUNT/MIN scans every 15s per replica were the most expensive exactly
 * when the backlog is worst (an incident), which is when the signal matters most. On a sampler failure
 * the gauges keep their LAST-GOOD values (a transient DB blip should not make the dashboard read "queue
 * is empty") and {@code agent.queue.health.sampler.failures} counts the miss so a sustained outage is
 * still observable. Interval widened from 15s to 30s (fix wave) — halves the steady-state query load for
 * a signal that does not need sub-30s resolution.
 *
 * <p>AtomicLong-backed (Micrometer's recommended pattern for a gauge whose value is computed
 * out-of-band rather than read live from a data structure): the gauge reads the atomic on scrape, the
 * scheduled {@link #sample()} writes it every 30s.
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.agent", name = "enabled", havingValue = "true")
@WorkspaceAgnostic("Fleet-wide queue health; not workspace-scoped")
public class AgentQueueHealthSampler {

    private static final Logger log = LoggerFactory.getLogger(AgentQueueHealthSampler.class);

    private final AgentJobRepository jobRepository;
    private final AtomicLong depth = new AtomicLong();
    private final AtomicLong oldestAgeSeconds = new AtomicLong();
    private final AtomicLong running = new AtomicLong();
    private final Counter samplerFailures;

    public AgentQueueHealthSampler(AgentJobRepository jobRepository, MeterRegistry meterRegistry) {
        this.jobRepository = jobRepository;
        Gauge.builder("agent.queue.depth", depth, AtomicLong::get)
            .description("QUEUED jobs currently eligible to run (available_at <= now); last-good on sampler failure")
            .register(meterRegistry);
        Gauge.builder("agent.queue.oldest_age_seconds", oldestAgeSeconds, AtomicLong::get)
            .description(
                "Age in seconds of the oldest eligible QUEUED job; 0 when the queue is empty; last-good on sampler failure"
            )
            .register(meterRegistry);
        Gauge.builder("agent.queue.running", running, AtomicLong::get)
            .description("Jobs currently RUNNING fleet-wide; last-good on sampler failure")
            .register(meterRegistry);
        this.samplerFailures = Counter.builder("agent.queue.health.sampler.failures")
            .description("Queue-health sample passes that failed (gauges kept their last-good value)")
            .register(meterRegistry);
    }

    @Scheduled(fixedDelay = 30, initialDelay = 10, timeUnit = TimeUnit.SECONDS)
    public void sample() {
        Instant now = Instant.now();
        try {
            AgentJobRepository.QueueHealthSnapshot snapshot = jobRepository.queueHealthSnapshot(now);
            depth.set(snapshot.getDepth());
            running.set(snapshot.getRunning());
            Instant oldest = snapshot.getOldestAvailableAt();
            oldestAgeSeconds.set(oldest != null ? Math.max(0, Duration.between(oldest, now).getSeconds()) : 0L);
        } catch (Exception e) {
            // Publish only on success: keep the last-good gauge values rather than a misleading
            // momentary "queue is empty" reading (#1368 fix wave, finding #12).
            samplerFailures.increment();
            log.warn("Queue-health sample failed, keeping last-good values: {}", e.getMessage());
        }
    }
}
