package de.tum.cit.aet.hephaestus.core.runtime.hub.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.time.Instant;
import java.util.UUID;

public class WorkerJwtIssuer {

    private final WorkerKeyRing keyRing;
    private final WorkerTokenProperties properties;

    public WorkerJwtIssuer(WorkerKeyRing keyRing, WorkerTokenProperties properties) {
        this.keyRing = keyRing;
        this.properties = properties;
    }

    public IssuedWorkerJwt issue(String workerId) {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId must not be blank");
        }
        WorkerSigningKey active = keyRing.active();
        Algorithm algorithm = Algorithm.RSA256(active.publicKey(), active.privateKey());
        Instant now = Instant.now();
        Instant expires = now.plus(properties.ttl());
        String jti = UUID.randomUUID().toString();
        String token = JWT.create()
            .withKeyId(active.kid())
            .withIssuer(properties.issuer())
            .withAudience(properties.audience())
            .withSubject(workerId)
            .withJWTId(jti)
            .withIssuedAt(now)
            .withNotBefore(now)
            .withExpiresAt(expires)
            .sign(algorithm);
        return new IssuedWorkerJwt(token, jti, expires);
    }

    public record IssuedWorkerJwt(String token, String jti, Instant expiresAt) {}
}
