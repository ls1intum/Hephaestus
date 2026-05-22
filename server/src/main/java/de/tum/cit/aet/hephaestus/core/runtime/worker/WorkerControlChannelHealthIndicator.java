package de.tum.cit.aet.hephaestus.core.runtime.worker;

import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * {@code DOWN} on disconnect OR when inbound silence exceeds {@code 3 × heartbeat.interval}.
 * When the worker is intentionally unconfigured (no control endpoint), reports {@code UP} with
 * {@code configured=false} so the actuator probe stays green on monolith dev pods.
 */
public class WorkerControlChannelHealthIndicator implements HealthIndicator {

    private final WorkerControlPublisher publisher;
    private final WorkerProperties properties;
    private final Duration silenceThreshold;

    public WorkerControlChannelHealthIndicator(WorkerControlPublisher publisher, WorkerProperties properties) {
        this.publisher = publisher;
        this.properties = properties;
        this.silenceThreshold = properties.heartbeat().interval().multipliedBy(3);
    }

    @Override
    public Health health() {
        if (!properties.control().isConfigured()) {
            return Health.up().withDetail("configured", false).build();
        }
        boolean connected = publisher.isConnected();
        Instant lastInbound = publisher.lastInboundAt();
        long ageMs = lastInbound.equals(Instant.EPOCH) ? -1L : Duration.between(lastInbound, Instant.now()).toMillis();
        boolean inboundStale = lastInbound.equals(Instant.EPOCH) || ageMs > silenceThreshold.toMillis();
        Health.Builder builder = (connected && !inboundStale) ? Health.up() : Health.down();
        builder.withDetail("connected", connected);
        builder.withDetail("lastInboundAgeMs", ageMs);
        return builder.build();
    }
}
