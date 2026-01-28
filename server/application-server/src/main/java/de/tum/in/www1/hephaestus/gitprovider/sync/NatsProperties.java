package de.tum.in.www1.hephaestus.gitprovider.sync;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for NATS messaging integration.
 *
 * <p>This class consolidates all NATS-related configuration into a single,
 * type-safe properties class using Spring Boot's {@code @ConfigurationProperties}.
 * The record is validated using JSR-380 (Bean Validation) annotations.
 *
 * <p>Duration-based fields use Spring Boot's {@link DurationUnit} annotation for
 * human-readable configuration values (e.g., {@code 5m}, {@code 30s}).
 *
 * <p>Example configuration in {@code application.yml}:
 * <pre>{@code
 * hephaestus:
 *   sync:
 *     nats:
 *       enabled: true
 *       server: nats://localhost:4222
 *       durable-consumer-name: hephaestus-consumer
 *       replay-timeframe-days: 7
 *       consumer:
 *         ack-wait: 5m
 *         max-ack-pending: 500
 *         idle-heartbeat: 30s
 *         heartbeat-restart-threshold: 60
 *         heartbeat-log-interval: 5m
 *         reconnect-delay: 2s
 *         request-timeout: 60s
 * }</pre>
 *
 * <p><strong>Note:</strong> The {@link NestedConfigurationProperty} annotation is imported
 * for documentation purposes. It is not strictly required for inner records since Spring Boot
 * automatically detects nested configuration classes, but it can improve IDE metadata generation.
 *
 * @param enabled whether NATS integration is enabled
 * @param server the NATS server URL (e.g., {@code nats://localhost:4222}); required when enabled
 * @param durableConsumerName the durable consumer name for JetStream subscriptions; nullable for ephemeral consumers
 * @param replayTimeframeDays the number of days to replay messages from when starting a new consumer
 * @param consumer consumer-specific configuration settings
 * @see Consumer
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.sync.nats")
public record NatsProperties(
    @DefaultValue("false") boolean enabled,
    @NotBlank(message = "NATS server URL must not be blank when NATS is enabled") String server,
    @Nullable String durableConsumerName,
    @DefaultValue("7") @Positive(message = "Replay timeframe must be positive") int replayTimeframeDays,
    @Valid Consumer consumer
) {
    /**
     * Compact constructor that applies default values for null parameters.
     *
     * <p>This ensures that configuration binding works correctly even when
     * optional properties are not specified in the configuration file.
     *
     * @param enabled whether NATS integration is enabled
     * @param server the NATS server URL
     * @param durableConsumerName the durable consumer name (nullable)
     * @param replayTimeframeDays the replay timeframe in days
     * @param consumer the consumer configuration
     */
    public NatsProperties {
        if (consumer == null) {
            consumer = new Consumer(
                Duration.ofMinutes(5),
                500,
                Duration.ofSeconds(30),
                60,
                Duration.ofMinutes(5),
                Duration.ofSeconds(2),
                Duration.ofSeconds(60)
            );
        }
    }

    /**
     * Consumer-specific configuration for NATS JetStream subscriptions.
     *
     * <p>All duration fields support Spring Boot's duration format:
     * <ul>
     *   <li>{@code 5m} - 5 minutes</li>
     *   <li>{@code 30s} - 30 seconds</li>
     *   <li>{@code PT5M} - ISO-8601 format (5 minutes)</li>
     *   <li>Plain integers are interpreted according to the {@link DurationUnit} annotation</li>
     * </ul>
     *
     * @param ackWait the maximum time the server will wait for an acknowledgment
     *                before redelivering the message (default: 5 minutes)
     * @param maxAckPending the maximum number of outstanding unacknowledged messages allowed;
     *                      must be between 1 and 10,000 (default: 500)
     * @param idleHeartbeat the interval at which the server sends heartbeat messages
     *                      when the consumer is idle (default: 30 seconds)
     * @param heartbeatRestartThreshold the number of consecutive missed heartbeats before triggering
     *                                  a consumer restart (default: 60)
     * @param heartbeatLogInterval the interval between heartbeat status log entries (default: 5 minutes)
     * @param reconnectDelay the delay before attempting to reconnect after a disconnection (default: 2 seconds)
     * @param requestTimeout the timeout for NATS request operations (default: 60 seconds)
     */
    public record Consumer(
        @DurationUnit(ChronoUnit.MINUTES) @DefaultValue("5m") @NotNull(
            message = "Ack wait duration must not be null"
        ) Duration ackWait,
        @DefaultValue("500") @Min(value = 1, message = "Max ack pending must be at least 1") @Max(
            value = 10000,
            message = "Max ack pending must not exceed 10,000"
        ) int maxAckPending,
        @DurationUnit(ChronoUnit.SECONDS) @DefaultValue("30s") @NotNull(
            message = "Idle heartbeat duration must not be null"
        ) Duration idleHeartbeat,
        @DefaultValue("60") @Positive(
            message = "Heartbeat restart threshold must be positive"
        ) int heartbeatRestartThreshold,
        @DurationUnit(ChronoUnit.MINUTES) @DefaultValue("5m") @NotNull(
            message = "Heartbeat log interval must not be null"
        ) Duration heartbeatLogInterval,
        @DurationUnit(ChronoUnit.SECONDS) @DefaultValue("2s") @NotNull(
            message = "Reconnect delay must not be null"
        ) Duration reconnectDelay,
        @DurationUnit(ChronoUnit.SECONDS) @DefaultValue("60s") @NotNull(
            message = "Request timeout must not be null"
        ) Duration requestTimeout
    ) {}
}
