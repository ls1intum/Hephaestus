package de.tum.cit.aet.hephaestus.core.auth.web;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves the current Hephaestus account from the validated JWT in the security context.
 *
 * <p>Our JWTs carry {@code sub = Account.id} (decimal), {@code jti} (UUID), and optionally
 * {@code act} (impersonator id). This is the single place those are parsed so controllers
 * stay declarative.
 *
 * <p>The resource-server chain validates our own ES256 JWTs via {@code RevocationAwareJwtDecoder},
 * whose {@code sub} is the decimal account id; these accessors parse it directly.
 */
public final class CurrentAccount {

    private CurrentAccount() {}

    /** The authenticated account id ({@code sub}), or throws 401 if absent / unparseable. */
    public static Long requireId() {
        Jwt jwt = requireJwt();
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "token subject is not an account id");
        }
    }

    /** The jti of the current token, for revocation on logout / refresh. */
    public static UUID requireJti() {
        Jwt jwt = requireJwt();
        String id = jwt.getId();
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "token has no jti");
        }
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "token jti is malformed");
        }
    }

    /** Roles from the JWT's flat {@code roles} claim; empty if absent. */
    public static java.util.List<String> roles() {
        Jwt jwt = jwtOrNull();
        if (jwt == null) {
            return java.util.List.of();
        }
        if (jwt.getClaims().get("roles") instanceof java.util.List<?> roles) {
            return roles.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return java.util.List.of();
    }

    /** Impersonator account id if this is an impersonation session, else null. */
    @Nullable
    public static Long impersonatorId() {
        Jwt jwt = jwtOrNull();
        if (jwt == null || !jwt.hasClaim("act")) {
            return null;
        }
        Object act = jwt.getClaim("act");
        if (act instanceof java.util.Map<?, ?> map && map.get("sub") instanceof String sub) {
            try {
                return Long.parseLong(sub);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Absolute impersonation expiry ({@code imp_exp} claim, epoch seconds) if present, else null.
     * Carries the time-box ceiling so {@code refresh} can auto-exit once it passes — the per-token
     * {@code exp} only bounds a single token, not the whole impersonation session.
     */
    @Nullable
    public static Instant impersonationExpiresAt() {
        Jwt jwt = jwtOrNull();
        if (jwt == null) {
            return null;
        }
        Object impExp = jwt.getClaim("imp_exp");
        if (impExp instanceof Number n) {
            return Instant.ofEpochSecond(n.longValue());
        }
        return null;
    }

    private static Jwt requireJwt() {
        Jwt jwt = jwtOrNull();
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not authenticated");
        }
        return jwt;
    }

    @Nullable
    private static Jwt jwtOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        return null;
    }
}
