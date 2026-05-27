package de.tum.cit.aet.hephaestus.integration.core.consumer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;

// idleHeartbeat / heartbeatRestartThreshold / heartbeatLogInterval / installationStaleAfter
// were dropped — declared but never read. Heartbeat-alarm restart logic from the legacy
// NatsConsumerService has not been ported.

/**
 * Connection-level configuration for the integration framework's NATS surface.
 *
 * <p>Owns the {@code hephaestus.sync.nats.*} property block that drives the JetStream
 * publisher (inbound webhook fan-out) AND the consumer fleet (per-scope + installation
 * subscriptions). The prefix is preserved verbatim from the pre-unification connection
 * properties so production YAML continues to bind without an operator-facing rename —
 * see {@code application.yml}.
 *
 * <p>The consumer-side tuning knobs (ack-wait, max-ack-pending, poison handling, …)
 * live separately on {@link NatsConsumerProperties} under
 * {@code hephaestus.integration.consumer.*}. This bean covers ONLY the
 * connection/replay knobs that are shared with the publisher half of the pipeline:
 *
 * <ul>
 *   <li>{@code enabled} — master switch (mirrors the publisher gate)</li>
 *   <li>{@code server} — NATS URL; required when enabled</li>
 *   <li>{@code durable-consumer-name} — base name for JetStream durables (one per scope
 *       plus one installation)</li>
 *   <li>{@code replay-timeframe-days} — how far back to replay on first consumer create</li>
 * </ul>
 *
 * <p><b>Note.</b> The {@code hephaestus.agent.nats.*} prefix used by the agent runtime is
 * a deliberately separate connection (different cluster in some deployments — see the
 * project memory note on "Separate NATS"). This bean does NOT bind that prefix.
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

    /**
     * Connection-side knobs shared between the consumer fleet and the publisher.
     * Ack-wait and poison handling live on {@link NatsConsumerProperties}.
     */
    public record Consumer(@DurationUnit(ChronoUnit.SECONDS) @DefaultValue("60s") Duration requestTimeout) {}
}
