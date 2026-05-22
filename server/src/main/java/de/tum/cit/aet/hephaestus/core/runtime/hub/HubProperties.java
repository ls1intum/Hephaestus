package de.tum.cit.aet.hephaestus.core.runtime.hub;

import java.time.Duration;

/**
 * Protocol-internal hub constants. These are not operator-tunable — every value has one
 * correct setting derived from the WSS frame protocol, the JWT lifetime, or the worker's
 * silence-deadline reconnect logic. Kept in one file so any future protocol revision lands
 * here without grepping the codebase.
 */
final class HubProperties {

    private HubProperties() {}

    /** WSS upgrade path. Hardcoded on both ends: worker derives {@code /api/workers/exchange} as a sibling. */
    static final String PATH = "/api/workers/connect";

    /** Max delay from WSS upgrade to first {@code WorkerHello} before the hub closes the session. */
    static final Duration HELLO_TIMEOUT = Duration.ofSeconds(10);

    /** Per-session outbound buffer cap; slow worker beyond this gets closed with code 1011. */
    static final int SEND_BUFFER_SIZE_BYTES = 8 * 1024 * 1024;

    /** Max single-frame payload. Tied to the protocol's frame size, not deployment topology. */
    static final int MAX_FRAME_SIZE_BYTES = 256 * 1024;

    /** Per-frame send budget. Beyond this the slow-consumer path closes the session. */
    static final Duration SEND_TIME_LIMIT = Duration.ofSeconds(10);

    /**
     * Window before {@code WorkerJwt.exp} at which the hub emits {@code ForceReconnect} so the
     * worker exchanges a fresh JWT without dropping inflight frames. Tied to the 1-hour token
     * lifetime; not operator-facing.
     */
    static final Duration FORCE_RECONNECT_THRESHOLD = Duration.ofMinutes(5);
}
