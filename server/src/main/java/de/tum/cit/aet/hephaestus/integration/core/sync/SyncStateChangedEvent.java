package de.tum.cit.aet.hephaestus.integration.core.sync;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;

/**
 * Published via {@code ApplicationEventPublisher} on job create/status-change/progress-flush.
 *
 * <p>Stage-1 (this PR) has no consumer — the SSE fan-out ({@code SyncEventHub}, {@code SyncPushService})
 * is a purely-additive follow-up (design doc §3.5, §5b Stage 2). Publishing now means that follow-up
 * only has to add a listener, not touch {@link SyncJobService}.
 *
 * @param scope what kind of change this is — payload is an invalidation hint, not data (the eventual
 *              SSE consumer tells clients to refetch over REST rather than carrying the DTO itself)
 */
public record SyncStateChangedEvent(long workspaceId, long connectionId, IntegrationKind kind, Scope scope) {
    public enum Scope {
        JOB,
        RESOURCES,
        CONNECTION,
        /** Webhook-liveness ("last event processed") update — see {@code ConnectionActivityRecorder}. */
        ACTIVITY,
    }
}
