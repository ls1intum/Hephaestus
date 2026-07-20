package de.tum.cit.aet.hephaestus.integration.core.sync.api;

/**
 * Derived connection health — never stored, computed on read by {@link SyncStatusService} from the
 * connection's {@code IntegrationState} plus its most recent job outcome and resource error count.
 */
public enum ConnectionHealth {
    /** Connection is in the OAuth/setup round-trip. */
    PENDING,
    /** ACTIVE with no known problems. */
    HEALTHY,
    /** ACTIVE but something needs attention (errored resources, a warnings-flagged job, vendor-side trouble). */
    DEGRADED,
    /** ACTIVE but the last job failed outright. */
    FAILED,
    /** Vendor revoked / admin paused / disconnected. */
    SUSPENDED,
}
