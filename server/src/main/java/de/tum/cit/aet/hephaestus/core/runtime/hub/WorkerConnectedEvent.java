package de.tum.cit.aet.hephaestus.core.runtime.hub;

import java.time.Instant;

/** Fired after a successful handshake. Consumers fetch full session state from {@link WorkerSessionRegistry}. */
public record WorkerConnectedEvent(String workerId, String sessionId, Instant connectedAt) {}
