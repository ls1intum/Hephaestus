package de.tum.cit.aet.hephaestus.integration.consumer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
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
 * Typed configuration for the integration framework's NATS consumer surface.
 *
 * <p>This bean is the canonical home for the consumer-tuning knobs (ack-wait,
 * max-ack-pending, idle-heartbeat, poison policy) and is read by every consumer-side
 * collaborator under {@code integration.consumer}.
 *
 * <p><b>History.</b> Pre-Slice-D the consumer-tuning knobs were duplicated between an
 * inner record on the monolithic consumer service and inlined constants. Slice D dissolved
 * the monolith into {@link IntegrationNatsConsumer} + collaborators, and this record is
 * now the single source of truth for the consumer-side tuning surface.
 *
 * <h2>Property prefix</h2>
 * {@code hephaestus.integration.consumer}. Auto-bound via {@code @ConfigurationPropertiesScan}
 * on {@code Application}, so no explicit {@code @EnableConfigurationProperties} import is
 * required.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * hephaestus:
 *   integration:
 *     consumer:
 *       ack-wait: 5m
 *       max-ack-pending: 500
 *       idle-heartbeat: 30s
 *       reconnect-delay: 2s
 *       heartbeat-restart-threshold: 60
 *       poison:
 *         max-redeliver: 10
 *         base-delay: 2s
 *         max-delay: 5m
 * }</pre>
 *
 * @param ackWait                    the JetStream ack wait — server-side timeout before
 *                                   redelivery. Default 5 minutes.
 * @param maxAckPending              max outstanding unacknowledged messages per consumer.
 *                                   Default 500.
 * @param idleHeartbeat              server-side idle heartbeat interval. Default 30s.
 * @param reconnectDelay             delay before reconnect attempts. Default 2s.
 * @param heartbeatRestartThreshold  consecutive missed heartbeats before the consumer is
 *                                   restarted. Default 60.
 * @param poison                     poison-message handling (NAK backoff &amp; redeliver cap).
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.integration.consumer")
public record NatsConsumerProperties(
    @DurationUnit(ChronoUnit.MINUTES) @DefaultValue("5m") @NotNull(
        message = "ack-wait must not be null"
    ) Duration ackWait,
    @DefaultValue("500") @Min(value = 1, message = "max-ack-pending must be at least 1") @Max(
        value = 10_000,
        message = "max-ack-pending must not exceed 10,000"
    ) int maxAckPending,
    @DurationUnit(ChronoUnit.SECONDS) @DefaultValue("30s") @NotNull(
        message = "idle-heartbeat must not be null"
    ) Duration idleHeartbeat,
    @DurationUnit(ChronoUnit.SECONDS) @DefaultValue("2s") @NotNull(
        message = "reconnect-delay must not be null"
    ) Duration reconnectDelay,
    @DefaultValue("60") @Positive(
        message = "heartbeat-restart-threshold must be positive"
    ) int heartbeatRestartThreshold,
    @Valid PoisonProperties poison
) {
    public NatsConsumerProperties {
        if (poison == null) {
            poison = new PoisonProperties(10, Duration.ofSeconds(2), Duration.ofMinutes(5));
        }
    }

    /**
     * Poison-message handling for the consumer's NAK loop.
     *
     * <p>The poison handler NAKs failing messages with exponential backoff up to
     * {@link #maxDelay()}. When a message has been redelivered {@link #maxRedeliver()} times
     * without success it is ACKed and logged at WARN so it stops blocking the consumer's
     * inflight slot.
     *
     * @param maxRedeliver redelivery attempts after which the message is treated as poison
     *                     and ACKed. Default 10.
     * @param baseDelay    starting NAK delay; the actual delay grows exponentially per
     *                     attempt. Default 2s.
     * @param maxDelay     hard cap on the NAK delay. Default 5 minutes — chosen to stay
     *                     below typical ack-wait so the server doesn't redeliver while we
     *                     are still waiting on our own NAK backoff.
     */
    public record PoisonProperties(
        @DefaultValue("10") @Positive(message = "max-redeliver must be positive") int maxRedeliver,
        @DurationUnit(ChronoUnit.SECONDS) @DefaultValue("2s") @NotNull(
            message = "base-delay must not be null"
        ) Duration baseDelay,
        @DurationUnit(ChronoUnit.MINUTES) @DefaultValue("5m") @NotNull(
            message = "max-delay must not be null"
        ) Duration maxDelay
    ) {}
}
