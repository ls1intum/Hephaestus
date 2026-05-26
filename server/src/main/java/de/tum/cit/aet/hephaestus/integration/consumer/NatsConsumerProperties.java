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
 * NATS consumer tuning bound to {@code hephaestus.integration.consumer}. Read by every
 * consumer-side collaborator under {@code integration.consumer}.
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.integration.consumer")
public record NatsConsumerProperties(
    @DurationUnit(ChronoUnit.MINUTES)
    @DefaultValue("5m")
    @NotNull(message = "ack-wait must not be null")
    Duration ackWait,
    @DefaultValue("500")
    @Min(value = 1, message = "max-ack-pending must be at least 1")
    @Max(value = 10_000, message = "max-ack-pending must not exceed 10,000")
    int maxAckPending,
    @DurationUnit(ChronoUnit.SECONDS)
    @DefaultValue("2s")
    @NotNull(message = "reconnect-delay must not be null")
    Duration reconnectDelay,
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
