package de.tum.cit.aet.hephaestus.core.runtime.hub;

import java.time.Instant;

/** Fired AFTER the session is removed from {@link WorkerSessionRegistry} — listeners cannot look it up. */
public record WorkerDisconnectedEvent(String workerId, String sessionId, String reason, Instant disconnectedAt) {}
