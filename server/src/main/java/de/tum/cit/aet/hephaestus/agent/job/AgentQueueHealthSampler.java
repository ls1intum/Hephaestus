package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Queue-health gauges for the {@code agent_job} queue (#1368 hardening — pressure-test verdict Tier 1
 * #2). Sampled on a timer, never on a request path — each metric is one cheap, index-backed query.
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
 * <p>AtomicLong-backed (Micrometer's recommended pattern for a gauge whose value is computed
 * out-of-band rather than read live from a data structure): the gauge reads the atomic on scrape, the
 * scheduled {@link #sample()} writes it every 15s.
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.agent", name = "enabled", havingValue = "true")
@WorkspaceAgnostic("Fleet-wide queue health; not workspace-scoped")
public class AgentQueueHealthSampler {

    private final AgentJobRepository jobRepository;
    private final AtomicLong depth = new AtomicLong();
    private final AtomicLong oldestAgeSeconds = new AtomicLong();
    private final AtomicLong running = new AtomicLong();

    public AgentQueueHealthSampler(AgentJobRepository jobRepository, MeterRegistry meterRegistry) {
        this.jobRepository = jobRepository;
        Gauge.builder("agent.queue.depth", depth, AtomicLong::get)
            .description("QUEUED jobs currently eligible to run (available_at <= now)")
            .register(meterRegistry);
        Gauge.builder("agent.queue.oldest_age_seconds", oldestAgeSeconds, AtomicLong::get)
            .description("Age in seconds of the oldest eligible QUEUED job; 0 when the queue is empty")
            .register(meterRegistry);
        Gauge.builder("agent.queue.running", running, AtomicLong::get)
            .description("Jobs currently RUNNING fleet-wide")
            .register(meterRegistry);
    }

    @Scheduled(fixedDelay = 15, initialDelay = 10, timeUnit = TimeUnit.SECONDS)
    public void sample() {
        Instant now = Instant.now();
        depth.set(jobRepository.countEligibleQueued(now));
        running.set(jobRepository.countRunning());
        oldestAgeSeconds.set(
            jobRepository
                .findOldestEligibleQueuedAt(now)
                .map(oldest -> Math.max(0, Duration.between(oldest, now).getSeconds()))
                .orElse(0L)
        );
    }
}
