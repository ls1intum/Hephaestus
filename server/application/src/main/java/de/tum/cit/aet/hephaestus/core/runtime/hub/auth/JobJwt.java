package de.tum.cit.aet.hephaestus.core.runtime.hub.auth;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A verified job credential.
 *
 * @param workDeadline when the attempt's work ends, as its executor set it. The credential itself stays valid until
 *     {@code expiresAt}, so the attempt can still return its result after its work time.
 */
public record JobJwt(
        UUID jobId,
        Long workspaceId,
        int attempt,
        Set<String> scopes,
        String jti,
        Instant issuedAt,
        Instant expiresAt,
        Instant workDeadline)
        implements WorkerJwt {
    public JobJwt {
        scopes = Set.copyOf(scopes);
        if (attempt < 0) throw new IllegalArgumentException("attempt must not be negative");
        // Order only: a passed work deadline still authenticates, so the attempt can return its result until expiry.
        if (workDeadline.isBefore(issuedAt) || workDeadline.isAfter(expiresAt))
            throw new IllegalArgumentException("work deadline must not be before issue or after expiry");
    }
}
