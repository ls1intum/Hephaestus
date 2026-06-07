package de.tum.cit.aet.hephaestus.core.auth.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.core.auth.metrics.AuthMetrics;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * {@link JwtDecoder} that validates signatures against {@link JwtSigningKeyService}'s
 * {@code JWKSource} and then short-circuits revoked {@code jti}s via a Caffeine-cached
 * negative lookup on {@link IssuedJwtRepository}.
 *
 * <h2>Cache strategy (negative cache)</h2>
 * The {@code auth_jwt_revoked} Spring cache stores ONLY the REVOKED verdict, keyed by {@code jti}.
 * An ACTIVE token is never cached: every request does the indexed primary-key lookup on
 * {@code issued_jwt(jti)} (cheap), so a logout / admin-revoke takes effect on every pod within DB
 * visibility lag rather than the cache TTL. Because a revocation is monotonic ({@code revoked_at}
 * is never cleared), a cached REVOKED entry can never become a false positive — the cache can only
 * ever be MORE restrictive than the DB, never less. No cross-pod eviction protocol is needed; the
 * cache fills on the first DB-confirmed revocation and the TTL (see {@code CacheConfig}) merely
 * bounds how long a revocation is remembered locally to shed token-replay load.
 *
 * <h2>Failure mode</h2>
 * If the DB is unreachable, this decoder fails closed — it surfaces an {@code invalid_token}
 * {@link BadJwtException}. The {@link BadJwtException} subtype matters: Spring's
 * {@code JwtAuthenticationProvider} maps it to {@code InvalidBearerTokenException} → 401, whereas a
 * bare {@link JwtException} would map to {@code AuthenticationServiceException} → 500. The
 * alternative (fail open, accept any signature-valid JWT) would defeat "sign out everywhere."
 */
public class RevocationAwareJwtDecoder implements JwtDecoder {

    public static final String CACHE_NAME = "auth_jwt_revoked";
    private static final Logger log = LoggerFactory.getLogger(RevocationAwareJwtDecoder.class);

    private final NimbusJwtDecoder delegate;
    private final IssuedJwtRepository repository;
    private final Cache cache;
    private final Clock clock;
    private final AuthMetrics metrics;

    public RevocationAwareJwtDecoder(
        JwtSigningKeyService keyService,
        IssuedJwtRepository repository,
        AuthProperties properties,
        CacheManager cacheManager,
        Clock clock,
        AuthMetrics metrics
    ) {
        this.delegate = localSignatureDecoder(keyService, properties);
        this.repository = repository;
        this.cache = cacheManager.getCache(CACHE_NAME);
        this.clock = clock;
        this.metrics = metrics;
    }

    /**
     * The LOCAL half of the verification: ES256 signature + the default timestamp/iss/aud validators,
     * with NO revocation (DB) check. This is what {@link #decode} wraps before the {@code issued_jwt}
     * lookup. Exposed so {@code StaleAuthCookieFilter} can cheaply decide whether a present cookie is
     * structurally valid (and thus worth authenticating) WITHOUT a per-request DB hit — a stale cookie
     * that fails here is evicted before it can 401 a public endpoint.
     */
    public static NimbusJwtDecoder localSignatureDecoder(JwtSigningKeyService keyService, AuthProperties properties) {
        ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        JWSKeySelector<SecurityContext> selector = new JWSVerificationKeySelector<>(JWSAlgorithm.ES256, keyService);
        processor.setJWSKeySelector(selector);
        NimbusJwtDecoder nimbus = new NimbusJwtDecoder(processor);
        nimbus.setJwtValidator(buildValidator(properties));
        return nimbus;
    }

    private static OAuth2TokenValidator<Jwt> buildValidator(AuthProperties properties) {
        // Wrap createDefault() WHOLE — it bundles JwtTimestampValidator (exp/nbf with a 60s clock-skew
        // default, which absorbs multi-pod clock drift) AND X509CertificateThumbprintValidator. We do
        // NOT recompose the list by hand: X509CertificateThumbprintValidator is package-private in
        // spring-security-oauth2-jose, and hand-rolling the default set would silently drop it
        // (spring-security#18230). issuer + audience are added on top.
        OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefault();
        OAuth2TokenValidator<Jwt> issuer = new JwtClaimValidator<String>(
            JwtClaimNames.ISS,
            iss -> iss != null && iss.equals(properties.issuer().toString())
        );
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD,
            aud -> aud != null && aud.contains(properties.audience())
        );
        return new DelegatingOAuth2TokenValidator<>(defaults, issuer, audience);
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        Jwt jwt = delegate.decode(token);
        String jtiClaim = jwt.getId();
        if (jtiClaim == null) {
            // BadJwtException (not bare JwtException): a signature-valid token with a bad jti is a
            // client error → 401, not a 500. See revokedException() and the class Javadoc.
            throw new BadJwtException("missing jti");
        }
        UUID jti;
        try {
            jti = UUID.fromString(jtiClaim);
        } catch (IllegalArgumentException ex) {
            throw new BadJwtException("malformed jti");
        }
        // Negative cache: only the REVOKED verdict is stored; ACTIVE always re-reads. See class Javadoc.
        Boolean revoked = (cache != null) ? cache.get(jti, Boolean.class) : null;
        if (Boolean.TRUE.equals(revoked)) {
            throw revokedException();
        }
        try {
            boolean active = repository.findActive(jti, clock.instant()).isPresent();
            if (!active) {
                if (cache != null) {
                    cache.put(jti, Boolean.TRUE);
                }
                throw revokedException();
            }
            return jwt;
        } catch (JwtException rethrow) {
            // Let our own revokedException() (a JwtException) pass through unchanged; it must NOT fall
            // into the RuntimeException handler below, which exists only to remap real DB failures.
            throw rethrow;
        } catch (RuntimeException dbError) {
            metrics.recordRevocationCheckFailed();
            log.error("auth.jwt: revocation lookup failed for jti={}", jti, dbError);
            // BadJwtException (not bare JwtException) so the provider maps this to a 401, not a 500.
            throw new BadJwtException("revocation check failed", dbError);
        }
    }

    private static JwtException revokedException() {
        OAuth2Error error = new OAuth2Error("invalid_token", "token has been revoked or expired", null);
        // BadJwtException (a JwtException subtype): Spring's JwtAuthenticationProvider maps it to
        // InvalidBearerTokenException → 401. A bare JwtException would surface as a 500 instead.
        return new BadJwtException(error.getDescription());
    }
}
