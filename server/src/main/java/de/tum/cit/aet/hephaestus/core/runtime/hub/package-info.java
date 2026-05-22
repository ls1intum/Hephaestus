/**
 * Server-side worker hub: WSS endpoint at {@code /api/workers/connect}, per-worker session
 * registry with atomic duplicate replacement, and JWT-based authentication for incoming worker
 * connections. Gated by
 * {@link de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole#SERVER_PROPERTY} via
 * {@link de.tum.cit.aet.hephaestus.core.runtime.hub.HubConfiguration}.
 *
 * <p>Protocol records ({@code core.runtime.worker.protocol}) are shared with the worker side;
 * the hub only owns transport (Spring {@code WebSocketHandler}), session lifecycle, and auth.
 * See ADR 0009.
 */
@org.springframework.modulith.NamedInterface("worker-hub")
package de.tum.cit.aet.hephaestus.core.runtime.hub;
