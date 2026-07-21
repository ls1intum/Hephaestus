package de.tum.cit.aet.hephaestus.agent.job;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the PostgreSQL-backed agent job queue.
 *
 * <p>The queue IS the {@code agent_job} table — {@link AgentJobService#submit} inserting a
 * {@code QUEUED} row is the enqueue; there is no separate transport to publish onto. Delivery is
 * poll-based: {@link AgentJobExecutor} periodically claims a batch of {@code QUEUED} rows with
 * {@code FOR UPDATE SKIP LOCKED}. Liveness/orphan-detection continues to run through
 * {@code worker_registry} heartbeats (see {@link WorkerLivenessReporter} and
 * {@link AgentJobZombieSweeper}), unrelated to how jobs are delivered.
 *
 * <p>Example configuration in {@code application.yml}:
 * <pre>{@code
 * hephaestus:
 *   agent:
 *     enabled: true
 *     poll-interval: 1s
 *     claim-batch-size: 5
 *     max-retries: 5
 *     heartbeat-interval: 25s
 * }</pre>
 *
 * @param enabled           whether the poll-based agent job executor is active
 * @param pollInterval      how long the poll loop sleeps between claim attempts when there is
 *                          nothing to do (empty poll, or every candidate was skipped)
 * @param claimBatchSize    max candidate QUEUED jobs considered per poll iteration; bounded by
 *                          this worker's own free local capacity so one poll never over-claims
 * @param maxRetries        max orphan-requeue attempts before a RUNNING job whose owning worker
 *                          was lost is failed instead of requeued again — the authoritative retry
 *                          budget lives on {@code agent_job.retry_count}
 * @param heartbeatInterval interval between {@code worker_registry} liveness heartbeats
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.agent")
public record AgentProperties(
    @DefaultValue("false") boolean enabled,
    @DurationUnit(ChronoUnit.SECONDS) @DefaultValue("1s") @NotNull Duration pollInterval,
    @DefaultValue("5") @Min(1) int claimBatchSize,
    @DefaultValue("5") @Positive int maxRetries,
    @DurationUnit(ChronoUnit.SECONDS) @DefaultValue("25s") @NotNull Duration heartbeatInterval
) {}
