package de.tum.cit.aet.hephaestus.agent.job;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the agent job NATS stream and consumer.
 *
 * <p>Example configuration in {@code application.yml}:
 * <pre>{@code
 * hephaestus:
 *   agent:
 *     nats:
 *       enabled: true
 *       server: nats://localhost:4222
 *       stream-name: AGENT
 *       consumer-name: hephaestus-agent-executor
 *       ack-wait: 70m
 *       max-deliver: 5
 *       max-ack-pending: 16   # cluster-wide; ≈ replicas × per-worker concurrency
 *       fetch-batch-size: 5   # per-replica pull; keep ≤ a single worker's concurrency
 *       heartbeat-interval: 25s
 * }</pre>
 *
 * @param enabled           whether the agent NATS consumer is active
 * @param server            NATS server URL
 * @param streamName        JetStream stream name
 * @param consumerName      durable pull consumer name
 * @param ackWait           max time before NATS redelivers an unacknowledged message
 *                          (must exceed max container timeout + buffer)
 * @param maxDeliver        max delivery attempts before dead-lettering
 * @param maxAckPending     max outstanding unacknowledged messages on the (shared, durable) consumer.
 *                          This is a CLUSTER-WIDE bound across all worker replicas — set it to roughly
 *                          {@code replicas × per-worker concurrency} so N replicas aren't throttled to
 *                          a single worker's worth of work (#1138).
 * @param fetchBatchSize    max messages a single replica pulls per fetch. Keep this near a single
 *                          worker's local capacity so one replica doesn't grab the whole cluster's
 *                          unacked budget and starve siblings; excess is left for other replicas.
 * @param heartbeatInterval interval between NATS InProgress heartbeats
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.agent.nats")
public record AgentNatsProperties(
    @DefaultValue("false") boolean enabled,
    @NotBlank(message = "NATS server URL must not be blank") String server,
    @DefaultValue("AGENT") @NotBlank String streamName,
    @DefaultValue("hephaestus-agent-executor") @NotBlank String consumerName,
    @DurationUnit(ChronoUnit.MINUTES) @DefaultValue("70m") @NotNull Duration ackWait,
    @DefaultValue("5") @Positive int maxDeliver,
    @DefaultValue("16") @Min(1) int maxAckPending,
    @DefaultValue("5") @Min(1) int fetchBatchSize,
    @DurationUnit(ChronoUnit.SECONDS) @DefaultValue("25s") @NotNull Duration heartbeatInterval
) {
    /** Subject prefix for agent job messages. Used by both publisher and consumer. */
    public static final String SUBJECT_PREFIX = "agent.jobs.";

    /** Wildcard subject for the stream and consumer filter. */
    public static final String SUBJECT_WILDCARD = SUBJECT_PREFIX + ">";
}
