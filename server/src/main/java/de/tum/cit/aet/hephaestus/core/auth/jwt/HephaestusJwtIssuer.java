package de.tum.cit.aet.hephaestus.core.auth.jwt;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mints Hephaestus's cookie-bound access JWTs.
 *
 * <h2>Claim shape (strict OIDC subset — no proprietary names)</h2>
 * <pre>
 * iss   — {@link AuthProperties#issuer}
 * sub   — {@code Account.id} as a decimal string
 * aud   — default {@link AuthProperties#audience} (caller can override per-issue)
 * jti   — fresh UUID; also INSERTed into {@code issued_jwt} so revocation can short-circuit
 * iat   — {@link Clock#instant()} of the issuing pod
 * exp   — {@code iat + accessTtl}
 * scope — space-delimited; encodes app role + active feature flag keys
 * act   — RFC 8693 actor object {@code {"sub": "<impersonator_id>"}}; absent when not impersonating
 * </pre>
 *
 * Why a strict subset: lets us mount Spring Authorization Server as a second issuer later
 * (e.g. for Issue #1200 third-party clients) without touching resource-server validators.
 *
 * <h2>Issuance contract</h2>
 * Every successful {@link #issue} call is paired with an {@code issued_jwt} INSERT in the
 * same transaction. The {@code jti} is committed before the cookie is set on the response —
 * if the DB write fails, no JWT escapes.
 */
@Service
public class HephaestusJwtIssuer {

    private final JwtEncoder encoder;
    private final JwtSigningKeyService keyService;
    private final IssuedJwtRepository issuedJwtRepository;
    private final AuthProperties properties;
    private final Clock clock;

    public HephaestusJwtIssuer(
        JwtSigningKeyService keyService,
        IssuedJwtRepository issuedJwtRepository,
        AuthProperties properties,
        Clock clock
    ) {
        this.keyService = keyService;
        this.issuedJwtRepository = issuedJwtRepository;
        this.properties = properties;
        this.clock = clock;
        this.encoder = buildEncoder(keyService);
    }

    private static JwtEncoder buildEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * Mint a new access JWT for {@code principal} (optionally under impersonation). Records
     * the {@code jti} in {@code issued_jwt} in the same transaction.
     *
     * <p>Claim set: {@code preferred_username} = login (standard OIDC), {@code given_name} =
     * first name, {@code roles} = flat string array, plus {@code sub} = account id and
     * {@code jti} for the Hephaestus-native endpoints + revocation.
     *
     * @param principal      account id + login + roles to bake in.
     * @param impersonatorId if non-null, sets the RFC 8693 {@code act} claim.
     * @param request        used to capture {@code user_agent} + remote IP into the revocation row.
     */
    @Transactional
    public Token issue(JwtPrincipal principal, @Nullable Long impersonatorId, @Nullable HttpServletRequest request) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.accessTtl());
        UUID jti = UUID.randomUUID();
        JWK signingKey = keyService.currentSigningKey();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
            .issuer(properties.issuer().toString())
            .subject(String.valueOf(principal.accountId()))
            .audience(java.util.List.of(properties.audience()))
            .id(jti.toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            // Claims consumed by SecurityUtils + the authority converter. preferred_username is
            // the standard OIDC claim; roles is a flat string array (our own — no nested realm_access).
            .claim("preferred_username", principal.login())
            .claim("roles", java.util.List.copyOf(principal.roles()));
        if (principal.givenName() != null) {
            claims.claim("given_name", principal.givenName());
        }
        if (impersonatorId != null) {
            claims.claim("act", java.util.Map.of("sub", String.valueOf(impersonatorId)));
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.ES256).keyId(signingKey.getKeyID()).build();
        Jwt jwt = encoder.encode(JwtEncoderParameters.from(header, claims.build()));

        IssuedJwt row = new IssuedJwt(jti, principal.accountId(), expiresAt);
        if (request != null) {
            String ua = request.getHeader("User-Agent");
            if (ua != null && ua.length() > 512) {
                ua = ua.substring(0, 512);
            }
            row.setUserAgent(ua);
            row.setIpInet(request.getRemoteAddr());
        }
        issuedJwtRepository.save(row);

        return new Token(jwt.getTokenValue(), jti, expiresAt);
    }

    /**
     * The freshly-minted token. {@link #value()} is what goes into the cookie; {@link #jti()}
     * is exposed so callers can correlate audit rows.
     */
    public record Token(String value, UUID jti, Instant expiresAt) {}
}
