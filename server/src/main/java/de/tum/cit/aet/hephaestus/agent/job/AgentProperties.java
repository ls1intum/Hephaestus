package de.tum.cit.aet.hephaestus.agent.job;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
 *                          nothing to do (empty poll, or every candidate was skipped); must be at
 *                          least {@link #MIN_POLL_INTERVAL} — a smaller value spins the poll loop
 *                          into a tight DB-hammering busy-loop, and zero/negative is nonsensical
 * @param claimBatchSize    max candidate QUEUED jobs considered per poll iteration; bounded by
 *                          this worker's own free local capacity so one poll never over-claims
 * @param maxRetries        max orphan-requeue attempts before a RUNNING job whose owning worker
 *                          was lost is failed instead of requeued again — the authoritative retry
 *                          budget lives on {@code agent_job.retry_count}. Zero is valid (no retries:
 *                          a lost worker's job fails on first detection instead of being requeued)
 * @param heartbeatInterval interval between {@code worker_registry} liveness heartbeats; must be at
 *                          least {@link #MIN_HEARTBEAT_INTERVAL} — a sub-second interval floods
 *                          {@code worker_registry} with writes and risks the interval truncating
 *                          to zero under the persisted column's resolution
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.agent")
public record AgentProperties(
    @DefaultValue("false") boolean enabled,
    @DurationUnit(ChronoUnit.SECONDS) @DefaultValue("1s") @NotNull Duration pollInterval,
    @DefaultValue("5") @Min(1) int claimBatchSize,
    @DefaultValue("5") @PositiveOrZero int maxRetries,
    @DurationUnit(ChronoUnit.SECONDS) @DefaultValue("25s") @NotNull Duration heartbeatInterval
) {
    /** Floor for {@link #pollInterval}: below this the poll loop busy-spins against the DB. */
    public static final Duration MIN_POLL_INTERVAL = Duration.ofMillis(100);

    /** Floor for {@link #heartbeatInterval}: below this the liveness signal floods {@code worker_registry}. */
    public static final Duration MIN_HEARTBEAT_INTERVAL = Duration.ofSeconds(1);

    /**
     * Fails startup with a clear message instead of booting a poller that spins the DB (a too-short
     * or non-positive {@code poll-interval}) or floods {@code worker_registry} (a sub-second {@code
     * heartbeat-interval}) — see {@code AGENT_POLL_INTERVAL} / {@code hephaestus.agent.heartbeat-interval}.
     * {@code @Min}/{@code @PositiveOrZero} above cover the plain-integer fields; {@link Duration}
     * bounds need an explicit check since Bean Validation has no built-in duration-comparison
     * constraint bundled with this project's validator set (matches the manual-check pattern already
     * used by {@code WorkerProperties.Heartbeat}/{@code Drain}/{@code Control}).
     */
    public AgentProperties {
        if (pollInterval == null || pollInterval.compareTo(MIN_POLL_INTERVAL) < 0) {
            throw new IllegalArgumentException(
                "hephaestus.agent.poll-interval (AGENT_POLL_INTERVAL) must be >= " +
                    MIN_POLL_INTERVAL +
                    ", got: " +
                    pollInterval
            );
        }
        if (heartbeatInterval == null || heartbeatInterval.compareTo(MIN_HEARTBEAT_INTERVAL) < 0) {
            throw new IllegalArgumentException(
                "hephaestus.agent.heartbeat-interval must be >= " +
                    MIN_HEARTBEAT_INTERVAL +
                    ", got: " +
                    heartbeatInterval
            );
        }
    }
}
