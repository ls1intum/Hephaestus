package de.tum.cit.aet.hephaestus.core.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Security regression suite for {@link RevocationAwareJwtDecoder}. Each test pins one verification
 * control; if the control is removed the test fails. Real ES256 keys are minted in-process; only the
 * true boundaries (key source, revocation store, clock) are stubbed.
 */
class RevocationAwareJwtDecoderTest extends BaseUnitTest {

    private static final URI ISSUER = URI.create("https://auth.example.test");
    private static final String AUDIENCE = "hephaestus-spa";
    // Anchor to real wall-clock: the NimbusJwtDecoder's default exp/nbf validators use the system
    // clock, so the token's iat/exp must straddle "now".
    private static final Instant NOW = Instant.now();

    private ECKey signingKey;
    private JWKSource<SecurityContext> jwkSource;
    private NimbusJwtEncoder encoder;
    private AuthProperties properties;
    private Clock clock;

    @BeforeEach
    void setUp() throws Exception {
        signingKey = new ECKeyGenerator(Curve.P_256)
            .keyID(UUID.randomUUID().toString())
            .keyUse(KeyUse.SIGNATURE)
            .generate();
        JWKSet set = new JWKSet(List.of(signingKey));
        jwkSource = (JWKSelector selector, SecurityContext ctx) -> selector.select(set);
        encoder = new NimbusJwtEncoder(jwkSource);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);

        properties = mock(AuthProperties.class);
        lenient().when(properties.issuer()).thenReturn(ISSUER);
        lenient().when(properties.audience()).thenReturn(AUDIENCE);
    }

    /** A JwtSigningKeyService stub that exposes the test JWK source — the decoder's only key boundary. */
    private JwtSigningKeyService keyService(JWKSource<SecurityContext> source) {
        JwtSigningKeyService svc = mock(JwtSigningKeyService.class);
        lenient()
            .when(svc.get(any(JWKSelector.class), any()))
            .thenAnswer(inv -> source.get(inv.getArgument(0), null));
        return svc;
    }

    private RevocationAwareJwtDecoder decoder(IssuedJwtRepository repo, CacheManager cacheManager) {
        return new RevocationAwareJwtDecoder(keyService(jwkSource), repo, properties, cacheManager, clock);
    }

    private static CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(RevocationAwareJwtDecoder.CACHE_NAME);
    }

    private String mint(UUID jti, String iss, String aud, Instant exp) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(iss)
            .subject("42")
            .audience(List.of(aud))
            .id(jti.toString())
            .issuedAt(exp.minus(Duration.ofMinutes(30))) // iat strictly before exp in every case
            .expiresAt(exp)
            .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.ES256).keyId(signingKey.getKeyID()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String validToken(UUID jti) {
        return mint(jti, ISSUER.toString(), AUDIENCE, NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void acceptsValidNonRevokedToken() {
        UUID jti = UUID.randomUUID();
        IssuedJwtRepository repo = mock(IssuedJwtRepository.class);
        IssuedJwt row = new IssuedJwt(jti, 42L, NOW.plus(Duration.ofMinutes(15)));
        when(repo.findActive(eq(jti), any())).thenReturn(Optional.of(row));

        Jwt decoded = decoder(repo, cacheManager()).decode(validToken(jti));

        assertThat(decoded.getId()).isEqualTo(jti.toString());
        assertThat(decoded.getSubject()).isEqualTo("42");
    }

    @Test
    void rejectsRevokedJti() {
        UUID jti = UUID.randomUUID();
        IssuedJwtRepository repo = mock(IssuedJwtRepository.class);
        when(repo.findActive(eq(jti), any())).thenReturn(Optional.empty()); // revoked/expired/unknown

        RevocationAwareJwtDecoder decoder = decoder(repo, cacheManager());

        assertThatThrownBy(() -> decoder.decode(validToken(jti)))
            .isInstanceOf(JwtException.class)
            .hasMessageContaining("revoked");
    }

    @Test
    void rejectsTamperedSignature() {
        UUID jti = UUID.randomUUID();
        IssuedJwtRepository repo = mock(IssuedJwtRepository.class);
        lenient()
            .when(repo.findActive(eq(jti), any()))
            .thenReturn(Optional.of(new IssuedJwt(jti, 42L, NOW.plus(Duration.ofMinutes(15)))));

        String token = validToken(jti);
        // Flip the last character of the signature segment.
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertThatThrownBy(() -> decoder(repo, cacheManager()).decode(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedByDifferentKey() throws Exception {
        // Sign with an attacker key; the decoder only trusts the real key source.
        ECKey attackerKey = new ECKeyGenerator(Curve.P_256)
            .keyID(signingKey.getKeyID())
            .keyUse(KeyUse.SIGNATURE)
            .generate();
        JWKSet attackerSet = new JWKSet(List.of(attackerKey));
        JWKSource<SecurityContext> attackerSource = (JWKSelector sel, SecurityContext ctx) -> sel.select(attackerSet);
        NimbusJwtEncoder attackerEncoder = new NimbusJwtEncoder(attackerSource);

        UUID jti = UUID.randomUUID();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(ISSUER.toString())
            .subject("42")
            .audience(List.of(AUDIENCE))
            .id(jti.toString())
            .issuedAt(NOW.minus(Duration.ofMinutes(1)))
            .expiresAt(NOW.plus(Duration.ofMinutes(15)))
            .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.ES256).keyId(signingKey.getKeyID()).build();
        String forged = attackerEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        IssuedJwtRepository repo = mock(IssuedJwtRepository.class);
        assertThatThrownBy(() -> decoder(repo, cacheManager()).decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        UUID jti = UUID.randomUUID();
        IssuedJwtRepository repo = mock(IssuedJwtRepository.class);
        String expired = mint(jti, ISSUER.toString(), AUDIENCE, NOW.minus(Duration.ofMinutes(1)));

        assertThatThrownBy(() -> decoder(repo, cacheManager()).decode(expired)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWrongIssuer() {
        UUID jti = UUID.randomUUID();
        IssuedJwtRepository repo = mock(IssuedJwtRepository.class);
        String wrongIss = mint(jti, "https://evil.example.test", AUDIENCE, NOW.plus(Duration.ofMinutes(15)));

        assertThatThrownBy(() -> decoder(repo, cacheManager()).decode(wrongIss)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWrongAudience() {
        UUID jti = UUID.randomUUID();
        IssuedJwtRepository repo = mock(IssuedJwtRepository.class);
        String wrongAud = mint(jti, ISSUER.toString(), "some-other-audience", NOW.plus(Duration.ofMinutes(15)));

        assertThatThrownBy(() -> decoder(repo, cacheManager()).decode(wrongAud)).isInstanceOf(JwtException.class);
    }

    @Test
    void failsClosedWhenRevocationStoreErrors() {
        UUID jti = UUID.randomUUID();
        IssuedJwtRepository repo = mock(IssuedJwtRepository.class);
        when(repo.findActive(eq(jti), any())).thenThrow(new RuntimeException("db down"));

        // Fail CLOSED: a DB outage must reject the token, never accept a signature-valid one.
        assertThatThrownBy(() -> decoder(repo, cacheManager()).decode(validToken(jti)))
            .isInstanceOf(JwtException.class)
            .hasMessageContaining("revocation check failed");
    }

    @Test
    void cachedRevokedStatusShortCircuitsWithoutHittingDb() {
        UUID jti = UUID.randomUUID();
        IssuedJwtRepository repo = mock(IssuedJwtRepository.class);
        CacheManager cm = cacheManager();
        Cache cache = cm.getCache(RevocationAwareJwtDecoder.CACHE_NAME);
        cache.put(jti, RevocationAwareJwtDecoder.Status.REVOKED);

        RevocationAwareJwtDecoder decoder = decoder(repo, cm);
        assertThatThrownBy(() -> decoder.decode(validToken(jti))).isInstanceOf(JwtException.class);
    }
}
