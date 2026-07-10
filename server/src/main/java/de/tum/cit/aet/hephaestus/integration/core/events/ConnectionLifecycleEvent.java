package de.tum.cit.aet.hephaestus.integration.core.events;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;

/**
 * In-process signal that a Connection crossed the ACTIVE boundary, published from the single
 * transition funnel ({@code ConnectionService#transition}) inside the transition's transaction.
 * Vendor adapters react with {@code @TransactionalEventListener(AFTER_COMMIT)} to run
 * connect-time work (webhook registration, an initial sync) the moment an admin connects,
 * instead of waiting for the next scheduler tick. Published only on a genuine state change —
 * same-state no-ops and idempotent audit replays stay silent.
 */
public sealed interface ConnectionLifecycleEvent {
    long connectionId();
    long workspaceId();
    IntegrationKind kind();

    /** The Connection just became ACTIVE (fresh connect or reactivation). */
    record Activated(long connectionId, long workspaceId, IntegrationKind kind) implements ConnectionLifecycleEvent {}

    /** The Connection just left ACTIVE (suspended or uninstalled). */
    record Deactivated(long connectionId, long workspaceId, IntegrationKind kind) implements ConnectionLifecycleEvent {}
}
