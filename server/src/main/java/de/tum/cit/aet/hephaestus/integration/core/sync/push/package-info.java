/**
 * Server-side SSE live-push layer for sync observability. Purely additive on top
 * of {@link de.tum.cit.aet.hephaestus.integration.core.sync.SyncStateChangedEvent}: the REST surface
 * in {@code sync.api} remains the source of truth, this layer only tells connected clients "something
 * changed, go refetch" — it never carries the DTO payload itself.
 *
 * <ul>
 *   <li>{@link de.tum.cit.aet.hephaestus.integration.core.sync.push.SyncEventHub} — per-workspace
 *       {@code SseEmitter} registry: subscribe/fan-out/heartbeat/trailing-edge coalescing.</li>
 *   <li>{@link de.tum.cit.aet.hephaestus.integration.core.sync.push.SyncPushService} — bridges
 *       {@code SyncStateChangedEvent} to the hub, optionally fanning out across replicas over a
 *       plain (non-JetStream) NATS subject when the shared connection is available.</li>
 *   <li>{@link de.tum.cit.aet.hephaestus.integration.core.sync.push.SyncEventsController} — the
 *       {@code GET /sync/events} SSE endpoint, hidden from the OpenAPI spec.</li>
 * </ul>
 *
 * <p>Gated end-to-end on {@link de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole} — the
 * worker and webhook pods never load this package.
 */
package de.tum.cit.aet.hephaestus.integration.core.sync.push;
