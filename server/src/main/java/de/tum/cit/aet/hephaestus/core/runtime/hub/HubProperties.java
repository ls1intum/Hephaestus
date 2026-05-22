package de.tum.cit.aet.hephaestus.core.runtime.hub;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The handful of hub knobs that are actually worth tuning. Protocol-internal numbers (WSS path,
 * hello timeout, per-session send-buffer size) live as constants on the handler — they have one
 * correct value each.
 */
@ConfigurationProperties(prefix = "hephaestus.worker.hub")
public record HubProperties(
    @DefaultValue("5m") Duration forceReconnectThreshold,
    @DefaultValue("262144") int maxFrameSizeBytes,
    @DefaultValue("10s") Duration sendTimeLimit
) {
    /** WSS upgrade path. Hardcoded on both ends: worker derives {@code /api/workers/exchange} as a sibling. */
    public static final String PATH = "/api/workers/connect";
    /** Max delay from WSS upgrade to first {@code WorkerHello} before the hub closes the session. */
    public static final Duration HELLO_TIMEOUT = Duration.ofSeconds(10);
    /** Per-session outbound buffer cap; slow worker beyond this gets closed with code 1011. */
    public static final int SEND_BUFFER_SIZE_BYTES = 8 * 1024 * 1024;

    public HubProperties {
        if (forceReconnectThreshold == null || forceReconnectThreshold.isNegative()) {
            throw new IllegalArgumentException("hub.forceReconnectThreshold must be >= 0");
        }
        if (maxFrameSizeBytes < 1024 || maxFrameSizeBytes > SEND_BUFFER_SIZE_BYTES) {
            throw new IllegalArgumentException(
                "hub.maxFrameSizeBytes must be in [1024, " + SEND_BUFFER_SIZE_BYTES + "], got: " + maxFrameSizeBytes
            );
        }
        if (sendTimeLimit == null || sendTimeLimit.isZero() || sendTimeLimit.isNegative()) {
            throw new IllegalArgumentException("hub.sendTimeLimit must be positive, got: " + sendTimeLimit);
        }
    }
}
