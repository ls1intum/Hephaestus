/**
 * Server-side worker hub: WSS endpoint, session registry, and JWT-based auth for incoming
 * worker connections. Protocol records are shared with the worker side; the hub owns transport,
 * session lifecycle, and auth only.
 */
@org.springframework.modulith.NamedInterface("worker-hub")
package de.tum.cit.aet.hephaestus.core.runtime.hub;
