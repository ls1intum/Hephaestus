package de.tum.in.www1.hephaestus.agent.sandbox.spi;

/**
 * Reason an {@link AttachedSandbox} was evicted from the {@code InteractiveSandboxRegistry}.
 *
 * <p>Used as the {@code reason} label on the {@code mentor_session_eviction_total} Micrometer counter.
 * Values are stable identifiers (snake_case) intended to be persisted as metric tag values — do not
 * rename without a coordinated dashboard update.
 */
public enum EvictionReason {
    /** Idle reaper closed the session because {@code idleFor() > idleTtl}. */
    IDLE("idle"),
    /** Reserved for a future absolute-lifetime cap. Not wired in #1069. */
    MAX_LIFETIME("max_lifetime"),
    /** Explicit {@code close()} from a caller (e.g. user logged out). */
    MANUAL("manual"),
    /** Pump or writer detected a transport failure (non-zero exit, broken pipe, etc.). */
    ERROR("error"),
    /** Runner exited cleanly (exit code 0); not really an eviction, but distinguished for metrics. */
    NATURAL_EXIT("natural_exit"),
    /** Docker daemon became unreachable mid-session. */
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
