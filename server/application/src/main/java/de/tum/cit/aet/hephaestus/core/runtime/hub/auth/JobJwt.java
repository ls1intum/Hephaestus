package de.tum.cit.aet.hephaestus.core.runtime.hub.auth;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record JobJwt(
        UUID jobId, Long workspaceId, int attempt, Set<String> scopes, String jti, Instant issuedAt, Instant expiresAt)
        implements WorkerJwt {
    public JobJwt {
        scopes = Set.copyOf(scopes);
        if (attempt < 0) throw new IllegalArgumentException("attempt must not be negative");
    }
}
