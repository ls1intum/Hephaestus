package de.tum.cit.aet.hephaestus.core.runtime.hub;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "hephaestus.worker.hub")
public record HubProperties(
    @DefaultValue("/api/workers/connect") String path,
    @DefaultValue("5m") Duration forceReconnectThreshold,
    @DefaultValue("262144") int maxFrameSizeBytes,
    /** Per-session outbound buffer cap; a slow worker beyond this gets closed with code 1011. */
    @DefaultValue("8388608") int sendBufferSizeBytes,
    /** Max time a single send call may block before the decorator gives up and closes. */
    @DefaultValue("10s") Duration sendTimeLimit,
    /** Max delay from WSS upgrade to first {@code WorkerHello}; missing it closes the session. */
    @DefaultValue("10s") Duration helloTimeout
) {
    public HubProperties {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("hub.path must not be blank");
        }
        if (forceReconnectThreshold == null || forceReconnectThreshold.isNegative()) {
            throw new IllegalArgumentException("hub.forceReconnectThreshold must be >= 0");
        }
        if (maxFrameSizeBytes < 1024) {
            throw new IllegalArgumentException("hub.maxFrameSizeBytes must be >= 1024, got: " + maxFrameSizeBytes);
        }
        if (sendBufferSizeBytes < maxFrameSizeBytes) {
            throw new IllegalArgumentException(
                "hub.sendBufferSizeBytes must be >= maxFrameSizeBytes, got: " + sendBufferSizeBytes);
        }
        if (sendTimeLimit == null || sendTimeLimit.isZero() || sendTimeLimit.isNegative()) {
            throw new IllegalArgumentException("hub.sendTimeLimit must be positive, got: " + sendTimeLimit);
        }
        if (helloTimeout == null || helloTimeout.isZero() || helloTimeout.isNegative()) {
            throw new IllegalArgumentException("hub.helloTimeout must be positive, got: " + helloTimeout);
        }
    }
}
