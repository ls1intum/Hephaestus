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
 * Configuration for the PostgreSQL-backed agent job queue: the {@code agent_job} table itself, claimed
 * by {@link AgentJobExecutor}'s poll loop with {@code FOR UPDATE SKIP LOCKED}.
 *
 * @param enabled           whether the agent job executor is active
 * @param pollInterval      how long the poll loop sleeps when a poll claimed nothing
 * @param claimBatchSize    max candidate QUEUED jobs considered per poll iteration
 * @param maxRetries        orphan-requeue attempts before a job whose owning worker was lost is failed
 *                          instead; the authoritative counter is {@code agent_job.retry_count}
 * @param heartbeatInterval interval between {@code worker_registry} liveness heartbeats
 * @param payloadRetention  age from {@code completed_at} at which a TERMINAL job's {@code container_logs}
 *                          and {@code output} are stripped to NULL
 * @param rowRetention      age from {@code completed_at} at which an unreferenced TERMINAL job row is
 *                          deleted; must be {@code >= payloadRetention}
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.agent")
public record AgentProperties(
    @DefaultValue("false") boolean enabled,
    @DurationUnit(ChronoUnit.SECONDS) @DefaultValue("1s") @NotNull Duration pollInterval,
    @DefaultValue("5") @Min(1) int claimBatchSize,
    @DefaultValue("5") @PositiveOrZero int maxRetries,
    @DurationUnit(ChronoUnit.SECONDS) @DefaultValue("25s") @NotNull Duration heartbeatInterval,
    @DefaultValue("P14D") @NotNull Duration payloadRetention,
    @DefaultValue("P90D") @NotNull Duration rowRetention
) {
    /** Floor for {@link #pollInterval}: below this the poll loop busy-spins against the DB. */
    public static final Duration MIN_POLL_INTERVAL = Duration.ofMillis(100);

    /** Floor for {@link #heartbeatInterval}: below this the liveness signal floods {@code worker_registry}. */
    public static final Duration MIN_HEARTBEAT_INTERVAL = Duration.ofSeconds(1);

    /**
     * A worker whose last {@code worker_registry} heartbeat is older than this is judged dead and its
     * RUNNING jobs are requeued to a sibling.
     */
    public static final Duration WORKER_LEASE_TTL = Duration.ofSeconds(60);

    /**
     * Ceiling for {@link #heartbeatInterval}. Half the lease, so a worker survives losing one beat; a
     * heartbeat slower than the lease would have every worker orphan its own running jobs.
     */
    public static final Duration MAX_HEARTBEAT_INTERVAL = WORKER_LEASE_TTL.dividedBy(2);

    /** Bean Validation has no duration-comparison constraint, so the {@link Duration} bounds are checked here. */
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
        if (heartbeatInterval.compareTo(MAX_HEARTBEAT_INTERVAL) > 0) {
            throw new IllegalArgumentException(
                "hephaestus.agent.heartbeat-interval must be <= " +
                    MAX_HEARTBEAT_INTERVAL +
                    " (half the " +
                    WORKER_LEASE_TTL +
                    " worker lease), or every worker is orphaned while its jobs are still running; got: " +
                    heartbeatInterval
            );
        }
        if (payloadRetention == null || payloadRetention.isNegative() || payloadRetention.isZero()) {
            throw new IllegalArgumentException(
                "hephaestus.agent.payload-retention must be positive, got: " + payloadRetention
            );
        }
        if (rowRetention == null || rowRetention.compareTo(payloadRetention) < 0) {
            throw new IllegalArgumentException(
                "hephaestus.agent.row-retention (" +
                    rowRetention +
                    ") must be >= payload-retention (" +
                    payloadRetention +
                    ") — a row cannot be deleted before its payload would already have been stripped"
            );
        }
    }
}
