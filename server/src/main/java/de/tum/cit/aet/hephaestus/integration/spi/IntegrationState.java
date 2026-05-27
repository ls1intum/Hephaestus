package de.tum.cit.aet.hephaestus.integration.spi;

import java.util.Map;
import java.util.Set;

/**
 * Per-Connection lifecycle state.
 *
 * <p>{@code PENDING} models the OAuth round-trip — we model OUR wait, not vendor's
 * setup work. {@code UNINSTALLED} is terminal: the row stays for audit, no further
 * transitions are legal, and reconnect requires a vendor-issued fresh
 * {@code instance_key} (the same tuple would collide with the {@code uq_connection}
 * unique constraint). Reconnect-with-same-installation-id is a known product gap
 * tracked separately.
 *
 * <p>Idempotent transitions: hitting {@code SUSPENDED} on an already-{@code SUSPENDED}
 * Connection returns the current state with no audit row — protects against webhook
 * redelivery floods.
 */
public enum IntegrationState {
    /** OAuth in flight, or pending vendor-side accept. */
    PENDING,

    /** Credentials valid; the integration is usable. */
    ACTIVE,

    /** Vendor revoked / Hephaestus admin paused; credentials retained but unused. */
    SUSPENDED,

    /** Hard disconnected; credentials cleared. Terminal — no legal transition out. */
    UNINSTALLED;

    private static final Map<IntegrationState, Set<IntegrationState>> LEGAL = Map.of(
        PENDING,
        Set.of(ACTIVE, UNINSTALLED),
        ACTIVE,
        Set.of(SUSPENDED, UNINSTALLED),
        SUSPENDED,
        Set.of(ACTIVE, UNINSTALLED),
        UNINSTALLED,
        Set.of()
    );

    public boolean canTransitionTo(IntegrationState next) {
        return LEGAL.get(this).contains(next);
    }
}
