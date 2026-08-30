package de.tum.cit.aet.hephaestus.integration.core.consumer;

import jakarta.validation.Valid;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

/**
 * Owns the {@code hephaestus.sync.nats.*} property block: the connection knobs shared
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
        @Nullable String username,
        @Nullable String password,
        @Nullable String durableConsumerName,
        @Valid Consumer consumer) {
    public NatsConnectionProperties(
            boolean enabled,
            @Nullable String server,
            @Nullable String durableConsumerName,
            @Nullable Consumer consumer) {
        this(enabled, server, null, null, durableConsumerName, consumer);
    }

    @ConstructorBinding
    public NatsConnectionProperties(
            boolean enabled,
            @Nullable String server,
            @Nullable String username,
            @Nullable String password,
            @Nullable String durableConsumerName,
            @Nullable Consumer consumer) {
        if (enabled && (server == null || server.isBlank())) {
            throw new IllegalStateException("hephaestus.sync.nats.server must be set when enabled=true");
        }
        if ((username == null) != (password == null)) {
            throw new IllegalStateException("NATS username and password must be configured together");
        }
        this.enabled = enabled;
        this.server = server;
        this.username = username;
        this.password = password;
        this.durableConsumerName = durableConsumerName;
        this.consumer = consumer == null ? new Consumer(Duration.ofSeconds(60)) : consumer;
    }

    /** Connection-side knobs shared between the consumer fleet and the publisher. */
    public record Consumer(
            @DurationUnit(ChronoUnit.SECONDS) @DefaultValue("60s")
            Duration requestTimeout) {}
}
