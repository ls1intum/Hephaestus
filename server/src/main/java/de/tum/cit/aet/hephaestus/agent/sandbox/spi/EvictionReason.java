package de.tum.cit.aet.hephaestus.agent.sandbox.spi;

/**
 * Reason a session terminated. Used as the {@code reason} label on
 * {@code mentor_session_eviction_total}; do not rename without dashboard coordination.
 */
public enum EvictionReason {
    IDLE("idle"),
    /** Reserved for a future absolute-lifetime cap. */
    MAX_LIFETIME("max_lifetime"),
    MANUAL("manual"),
    ERROR("error"),
    NATURAL_EXIT("natural_exit"),
    DAEMON_UNHEALTHY("daemon_unhealthy");

    private final String tag;

    EvictionReason(String tag) {
        this.tag = tag;
    }

    /** Stable snake_case label for Micrometer. */
    public String tag() {
        return tag;
    }
}
