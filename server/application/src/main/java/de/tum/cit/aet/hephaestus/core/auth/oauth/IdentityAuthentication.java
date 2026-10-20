package de.tum.cit.aet.hephaestus.core.auth.oauth;

import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent;
import de.tum.cit.aet.hephaestus.core.auth.jwt.CookieBearerTokenResolver;
import de.tum.cit.aet.hephaestus.core.auth.jwt.RevocationAwareJwtDecoder;
import de.tum.cit.aet.hephaestus.core.auth.stepup.RecentSignInPolicy;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

/**
 * The stateless OAuth chain has no SecurityContext, so linking must validate the access cookie through
 * {@link CookieBearerTokenResolver} and {@link RevocationAwareJwtDecoder}, including recent sign-in.
 */
@ConditionalOnServerRole
@Service
public class IdentityAuthentication {
    private static final Logger log = LoggerFactory.getLogger(IdentityAuthentication.class);

    private final CookieBearerTokenResolver bearerTokenResolver;
    private final JwtDecoder jwtDecoder;
    private final Converter<Jwt, AbstractAuthenticationToken> authenticationConverter;
    private final RecentSignInPolicy recentSignInPolicy;

    public IdentityAuthentication(
            CookieBearerTokenResolver bearerTokenResolver,
            RevocationAwareJwtDecoder jwtDecoder,
            Converter<Jwt, AbstractAuthenticationToken> authenticationConverter,
            RecentSignInPolicy recentSignInPolicy) {
        this.bearerTokenResolver = bearerTokenResolver;
        this.jwtDecoder = jwtDecoder;
        this.authenticationConverter = authenticationConverter;
        this.recentSignInPolicy = recentSignInPolicy;
    }

    /**
     * The account id from the access cookie, or {@code null} when there is no token or the token is
     * invalid or revoked.
     *
     * @throws de.tum.cit.aet.hephaestus.core.auth.stepup.StepUpRequiredException when the session
     *     is valid but the sign-in behind it is no longer recent.
     */
    @Nullable
    public Long resolveAuthenticatedAccountId(HttpServletRequest request) {
        String token = bearerTokenResolver.resolve(request);
        if (token == null || token.isBlank()) {
            return null;
        }
        Jwt jwt;
        Long accountId;
        try {
            jwt = jwtDecoder.decode(token);
            accountId = Long.parseLong(jwt.getSubject());
        } catch (JwtException | NumberFormatException ex) {
            log.warn("auth.link: token rejected: errorType={}", ex.getClass().getName());
            return null;
        }
        recentSignInPolicy.require(
                authenticationConverter.convert(jwt), AuthEvent.EventType.IDENTITY_LINKED, accountId);
        return accountId;
    }
}
