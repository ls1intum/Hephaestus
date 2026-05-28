package de.tum.cit.aet.hephaestus.core.auth.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * {@link JwtDecoder} that validates signatures against {@link JwtSigningKeyService}'s
 * {@link JWKSource} and then short-circuits revoked {@code jti}s via a Caffeine-cached
 * negative lookup on {@link IssuedJwtRepository}.
 *
 * <h2>Cache strategy</h2>
 * Lookups are cached by {@code jti} in the {@code auth_jwt_revoked} Spring cache (TTL ~1
 * minute — see {@code CacheConfig}). The cache stores either a {@link Status#ACTIVE} sentinel
 * or {@link Status#REVOKED}; both short-circuit the DB. Cross-pod invalidation lands in a
 * later commit via NATS subject {@code auth.jwt.revoked}.
 *
 * <h2>Failure mode</h2>
 * If the DB is unreachable, this decoder fails closed — it surfaces an
 * {@code invalid_token} {@link JwtException}, which downstream Spring maps to a 401. The
 * alternative (fail open, accept any signature-valid JWT) would defeat "sign out everywhere."
 */
public class RevocationAwareJwtDecoder implements JwtDecoder {

    public static final String CACHE_NAME = "auth_jwt_revoked";
    private static final Logger log = LoggerFactory.getLogger(RevocationAwareJwtDecoder.class);

    private final NimbusJwtDecoder delegate;
    private final IssuedJwtRepository repository;
    private final Cache cache;
    private final Clock clock;

    public RevocationAwareJwtDecoder(
        JwtSigningKeyService keyService,
        IssuedJwtRepository repository,
        AuthProperties properties,
        CacheManager cacheManager,
        Clock clock
    ) {
        this.delegate = buildNimbus(keyService, properties);
        this.repository = repository;
        this.cache = cacheManager.getCache(CACHE_NAME);
        this.clock = clock;
    }

    private static NimbusJwtDecoder buildNimbus(JwtSigningKeyService keyService, AuthProperties properties) {
        ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        JWSKeySelector<SecurityContext> selector = new JWSVerificationKeySelector<>(JWSAlgorithm.ES256, keyService);
        processor.setJWSKeySelector(selector);
        NimbusJwtDecoder nimbus = new NimbusJwtDecoder(processor);
        nimbus.setJwtValidator(buildValidator(properties));
        return nimbus;
    }

    private static OAuth2TokenValidator<Jwt> buildValidator(AuthProperties properties) {
        OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefault();
        OAuth2TokenValidator<Jwt> timestamp = new JwtTimestampValidator();
        OAuth2TokenValidator<Jwt> issuer = new JwtClaimValidator<String>(JwtClaimNames.ISS, iss ->
            iss != null && iss.equals(properties.issuer().toString())
        );
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<java.util.List<String>>(JwtClaimNames.AUD, aud ->
            aud != null && aud.contains(properties.audience())
        );
        return new DelegatingOAuth2TokenValidator<>(defaults, timestamp, issuer, audience);
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        Jwt jwt = delegate.decode(token);
        String jtiClaim = jwt.getId();
        if (jtiClaim == null) {
            throw new JwtException("missing jti");
        }
        UUID jti;
        try {
            jti = UUID.fromString(jtiClaim);
        } catch (IllegalArgumentException ex) {
            throw new JwtException("malformed jti");
        }
        Status cached = (cache != null) ? cache.get(jti, Status.class) : null;
        if (cached == Status.REVOKED) {
            throw revokedException();
        }
        if (cached == Status.ACTIVE) {
            return jwt;
        }
        try {
            boolean active = repository.findActive(jti, clock.instant()).isPresent();
            if (cache != null) {
                cache.put(jti, active ? Status.ACTIVE : Status.REVOKED);
            }
            if (!active) {
                throw revokedException();
            }
            return jwt;
        } catch (JwtException rethrow) {
            throw rethrow;
        } catch (RuntimeException dbError) {
            log.error("auth.jwt: revocation lookup failed for jti={}", jti, dbError);
            throw new JwtException("revocation check failed", dbError);
        }
    }

    private static JwtException revokedException() {
        OAuth2Error error = new OAuth2Error("invalid_token", "token has been revoked or expired", null);
        return new JwtException(error.getDescription());
    }

    /** Invalidate a single jti in the local cache — used by the NATS listener (later commit). */
    public void invalidate(UUID jti) {
        if (cache != null) {
            cache.evict(jti);
        }
    }

    /**
     * Visibility marker — kept off {@link Status#name()} on the wire because the cache stores
     * the enum reference directly (Caffeine-serialised in-process).
     */
    public enum Status {
        ACTIVE,
        REVOKED,
    }

    /** Defensive accessor for tests that need the public JWK set without rebuilding the decoder. */
    public static JWKSet publicJwkSet(JwtSigningKeyService keyService) {
        return keyService.publicJwkSet();
    }
}
