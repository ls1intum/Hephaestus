package de.tum.cit.aet.hephaestus.core.runtime.hub.auth;

import java.time.Instant;

public record WorkerSessionJwt(String workerId, String jti, Instant issuedAt, Instant expiresAt) implements WorkerJwt {}
