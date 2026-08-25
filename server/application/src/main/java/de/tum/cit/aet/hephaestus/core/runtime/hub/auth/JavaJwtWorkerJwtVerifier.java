package de.tum.cit.aet.hephaestus.core.runtime.hub.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class JavaJwtWorkerJwtVerifier implements WorkerJwtVerifier {

    private static final String EXPECTED_ALG = "RS256";
    private static final String AUDIT_METRIC = "worker.jwt.verify";

    private final Map<String, JWTVerifier> verifiersByKid;
    private final WorkerTokenDenylistService denylist;
    private final MeterRegistry meterRegistry;

    public JavaJwtWorkerJwtVerifier(
        WorkerKeyRing keyRing,
        WorkerTokenProperties properties,
        WorkerTokenDenylistService denylist,
        MeterRegistry meterRegistry
    ) {
        Map<String, JWTVerifier> map = new HashMap<>();
        for (WorkerSigningKey key : keyRing.all()) {
            map.put(
                key.kid(),
                JWT.require(Algorithm.RSA256(key.publicKey(), null))
                    .withIssuer(properties.issuer())
                    .withAudience(properties.audience())
                    .acceptLeeway(5)
                    .build()
            );
        }
        this.verifiersByKid = Map.copyOf(map);
        this.denylist = denylist;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public WorkerJwt verify(String token) {
        try {
            WorkerJwt jwt = verifyInternal(token);
            meterRegistry.counter(AUDIT_METRIC, "outcome", "success").increment();
            return jwt;
        } catch (WorkerJwtInvalidException e) {
            meterRegistry.counter(AUDIT_METRIC, "outcome", "failed", "reason", e.getReasonTag()).increment();
            throw e;
        }
    }

    private WorkerJwt verifyInternal(String token) {
        if (token == null || token.isBlank()) {
            throw new WorkerJwtInvalidException("token missing", "missing");
        }
        DecodedJWT decoded;
        try {
            decoded = JWT.decode(token);
        } catch (JWTVerificationException e) {
            throw new WorkerJwtInvalidException("decode failed: " + e.getClass().getSimpleName(), "decode", e);
        }
        // Allowlist alg before key dispatch — guards against future verifier refactors that
        // could be steered into alg-confusion (RFC 8725 §3.1).
        if (!EXPECTED_ALG.equals(decoded.getAlgorithm())) {
            throw new WorkerJwtInvalidException("alg not allowed: " + decoded.getAlgorithm(), "alg");
        }
        String kid = decoded.getKeyId();
        if (kid == null || kid.isBlank()) {
            throw new WorkerJwtInvalidException("missing kid header", "kid");
        }
        JWTVerifier verifier = verifiersByKid.get(kid);
        if (verifier == null) {
            throw new WorkerJwtInvalidException("unknown kid: " + kid, "kid");
        }
        DecodedJWT verified;
        try {
            verified = verifier.verify(token);
        } catch (JWTVerificationException e) {
            throw new WorkerJwtInvalidException("verify failed: " + e.getClass().getSimpleName(), "sig", e);
        }
        String workerId = verified.getSubject();
        String jti = verified.getId();
        Instant expiresAt = verified.getExpiresAtAsInstant();
        if (workerId == null || workerId.isBlank()) {
            throw new WorkerJwtInvalidException("missing sub claim", "claim");
        }
        if (jti == null || jti.isBlank()) {
            throw new WorkerJwtInvalidException("missing jti claim", "claim");
        }
        if (expiresAt == null) {
            throw new WorkerJwtInvalidException("missing exp claim", "claim");
        }
        if (denylist.isRevoked(jti)) {
            throw new WorkerJwtInvalidException("token revoked", "revoked");
        }
        return new WorkerJwt(workerId, jti, expiresAt);
    }
}
