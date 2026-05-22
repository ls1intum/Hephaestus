package de.tum.cit.aet.hephaestus.core.runtime.hub.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class JavaJwtWorkerJwtVerifier implements WorkerJwtVerifier {

    private static final String EXPECTED_ALG = "RS256";

    private final Map<String, JWTVerifier> verifiersByKid;
    private final WorkerTokenDenylist denylist;

    public JavaJwtWorkerJwtVerifier(
        WorkerKeyRing keyRing,
        WorkerTokenProperties properties,
        WorkerTokenDenylist denylist
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
    }

    @Override
    public WorkerJwt verify(String token) throws WorkerJwtInvalidException {
        if (token == null || token.isBlank()) {
            throw new WorkerJwtInvalidException("token missing");
        }
        DecodedJWT decoded;
        try {
            decoded = JWT.decode(token);
        } catch (JWTVerificationException e) {
            throw new WorkerJwtInvalidException("decode failed: " + e.getClass().getSimpleName(), e);
        }
        // Allowlist alg before key dispatch — guards against future verifier refactors that
        // could be steered into alg-confusion (e.g. accidentally accepting HS256 with a public
        // key bytestring). RFC 8725 §3.1.
        if (!EXPECTED_ALG.equals(decoded.getAlgorithm())) {
            throw new WorkerJwtInvalidException("alg not allowed: " + decoded.getAlgorithm());
        }
        String kid = decoded.getKeyId();
        if (kid == null || kid.isBlank()) {
            throw new WorkerJwtInvalidException("missing kid header");
        }
        JWTVerifier verifier = verifiersByKid.get(kid);
        if (verifier == null) {
            throw new WorkerJwtInvalidException("unknown kid: " + kid);
        }
        DecodedJWT verified;
        try {
            verified = verifier.verify(token);
        } catch (JWTVerificationException e) {
            throw new WorkerJwtInvalidException("verify failed: " + e.getClass().getSimpleName(), e);
        }
        String workerId = verified.getSubject();
        String jti = verified.getId();
        Instant expiresAt = verified.getExpiresAtAsInstant();
        if (workerId == null || workerId.isBlank()) {
            throw new WorkerJwtInvalidException("missing sub claim");
        }
        if (jti == null || jti.isBlank()) {
            throw new WorkerJwtInvalidException("missing jti claim");
        }
        if (expiresAt == null) {
            throw new WorkerJwtInvalidException("missing exp claim");
        }
        if (denylist.isRevoked(jti)) {
            throw new WorkerJwtInvalidException("token revoked");
        }
        return new WorkerJwt(workerId, jti, expiresAt);
    }
}
