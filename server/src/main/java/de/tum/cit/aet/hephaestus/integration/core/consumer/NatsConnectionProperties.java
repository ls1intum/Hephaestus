package de.tum.cit.aet.hephaestus.integration.core.consumer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

/**
 * Owns the {@code hephaestus.sync.nats.*} property block: the connection/replay knobs shared
 * between the JetStream publisher (inbound webhook fan-out) and the consumer fleet. Consumer-side
 * tuning (ack-wait, max-ack-pending, poison handling, …) lives on {@link NatsConsumerProperties}
 * under {@code hephaestus.integration.consumer.*}.
 *
 * <p>This connection serves webhook/sync ingest only. The agent job queue runs on PostgreSQL
 * (ADR 0025) and has no NATS connection of its own.
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.sync.nats")
public record NatsConnectionProperties(
    @DefaultValue("false") boolean enabled,
    @Nullable String server,
    @Nullable String durableConsumerName,
    @DefaultValue("7") @Positive(message = "Replay timeframe must be positive") int replayTimeframeDays,
    @Valid Consumer consumer
) {
    public NatsConnectionProperties {
        if (enabled && (server == null || server.isBlank())) {
            throw new IllegalStateException("hephaestus.sync.nats.server must be set when enabled=true");
        }
        if (consumer == null) {
            consumer = new Consumer(Duration.ofSeconds(60));
        }
    }

    /** Connection-side knobs shared between the consumer fleet and the publisher. */
    public record Consumer(@DurationUnit(ChronoUnit.SECONDS) @DefaultValue("60s") Duration requestTimeout) {}
}
