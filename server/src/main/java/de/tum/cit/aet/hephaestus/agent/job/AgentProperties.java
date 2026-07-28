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
 * @param rowRetention      age from {@code completed_at} at which a TERMINAL job row is deleted; must be
 *                          {@code >= payloadRetention}
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
