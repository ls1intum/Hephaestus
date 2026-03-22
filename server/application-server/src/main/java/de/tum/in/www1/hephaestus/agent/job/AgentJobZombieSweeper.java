package de.tum.in.www1.hephaestus.agent.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.config.ConfigSnapshot;
import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodic recovery for orphaned agent jobs.
 *
 * <p>Two sweeps:
 * <ol>
 *   <li><b>Zombie QUEUED</b> (every 5 min): re-publishes QUEUED jobs older than 10 minutes
 *       that were never picked up — typically due to NATS publish failure after DB commit.</li>
 *   <li><b>Stale RUNNING</b> (every 2 min): marks RUNNING jobs as FAILED if they've exceeded
 *       their timeout + 5-minute buffer — handles executor crashes and OOM kills.</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.agent.nats", name = "enabled", havingValue = "true")
@WorkspaceAgnostic("Zombie sweeper operates across all workspaces")
public class AgentJobZombieSweeper {

    private static final Logger log = LoggerFactory.getLogger(AgentJobZombieSweeper.class);

    private static final Duration QUEUED_STALE_THRESHOLD = Duration.ofMinutes(10);
    private static final Duration RUNNING_BUFFER = Duration.ofMinutes(5);

    private final AgentJobRepository jobRepository;
    private final AgentJobSubmitter submitter;
    private final ObjectMapper objectMapper;
    private final Counter zombieRepublished;
    private final Counter zombieReaped;

    public AgentJobZombieSweeper(
        AgentJobRepository jobRepository,
        AgentJobSubmitter submitter,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry
    ) {
        this.jobRepository = jobRepository;
        this.submitter = submitter;
        this.objectMapper = objectMapper;
        this.zombieRepublished = Counter.builder("agent.job.zombie.republished")
            .description("QUEUED jobs re-published to NATS")
            .register(meterRegistry);
        this.zombieReaped = Counter.builder("agent.job.zombie.reaped")
            .description("Stale RUNNING jobs marked as TIMED_OUT")
            .register(meterRegistry);
    }

    /**
     * Re-publish QUEUED jobs that were never picked up by the NATS consumer.
     */
    @Transactional(readOnly = true)
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES, initialDelay = 2)
    public void republishStaleQueuedJobs() {
        Instant cutoff = Instant.now().minus(QUEUED_STALE_THRESHOLD);
        List<AgentJob> staleJobs = jobRepository.findStaleQueuedJobs(cutoff);

        if (staleJobs.isEmpty()) {
            return;
        }

        log.info("Found {} stale QUEUED jobs, re-publishing to NATS", staleJobs.size());

        for (AgentJob job : staleJobs) {
            try {
                submitter.publish(job.getId(), job.getWorkspace().getId());
                zombieRepublished.increment();
                log.info("Re-published stale job: jobId={}, createdAt={}", job.getId(), job.getCreatedAt());
            } catch (Exception e) {
                log.warn("Failed to re-publish stale job: jobId={}, error={}", job.getId(), e.getMessage());
            }
        }
    }

    /**
     * Mark stale RUNNING jobs as FAILED (executor crashed or OOM-killed).
     */
    @Transactional
    @Scheduled(fixedDelay = 2, timeUnit = TimeUnit.MINUTES, initialDelay = 1)
    public void reapStaleRunningJobs() {
        // Use the buffer as cutoff: any job running longer than 5 minutes is worth checking.
        // The per-job timeout from configSnapshot determines the actual staleness.
        Instant conservativeCutoff = Instant.now().minus(RUNNING_BUFFER);
        List<AgentJob> staleJobs = jobRepository.findStaleRunningJobs(conservativeCutoff);

        if (staleJobs.isEmpty()) {
            return;
        }

        for (AgentJob job : staleJobs) {
            try {
                // Check per-job timeout from config snapshot
                int timeoutSeconds = getTimeoutFromSnapshot(job);
                Duration maxLifetime = Duration.ofSeconds(timeoutSeconds).plus(RUNNING_BUFFER);
                if (job.getStartedAt() != null && job.getStartedAt().plus(maxLifetime).isAfter(Instant.now())) {
                    continue; // Not stale yet for this specific job's timeout
                }

                int updated = jobRepository.transitionStatus(
                    job.getId(),
                    AgentJobStatus.TIMED_OUT,
                    Instant.now(),
                    "Reaped: exceeded timeout (executor may have crashed)",
                    java.util.Set.of(AgentJobStatus.RUNNING)
                );

                if (updated > 0) {
                    zombieReaped.increment();
                    log.warn("Reaped stale RUNNING job: jobId={}, startedAt={}", job.getId(), job.getStartedAt());
                }
            } catch (Exception e) {
                log.warn("Failed to reap stale job: jobId={}, error={}", job.getId(), e.getMessage());
            }
        }
    }

    private int getTimeoutFromSnapshot(AgentJob job) {
        try {
            if (job.getConfigSnapshot() != null) {
                ConfigSnapshot snapshot = ConfigSnapshot.fromJson(job.getConfigSnapshot(), objectMapper);
                return snapshot.timeoutSeconds();
            }
        } catch (Exception e) {
            log.debug("Could not parse config snapshot for job {}: {}", job.getId(), e.getMessage());
        }
        return 600; // Default 10 minutes
    }
}
