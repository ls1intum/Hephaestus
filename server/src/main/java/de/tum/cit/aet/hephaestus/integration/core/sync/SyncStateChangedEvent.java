package de.tum.cit.aet.hephaestus.integration.core.sync;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;

/** A committed sync-state invalidation consumed by the SSE push layer. */
public record SyncStateChangedEvent(long workspaceId, long connectionId, IntegrationKind kind, Scope scope) {
    public enum Scope {
        JOB,
        RESOURCES,
        CONNECTION,
        ACTIVITY,
    }
}
