package de.tum.cit.aet.hephaestus.core.auth.web;

import java.util.UUID;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Resolves the current Hephaestus account from the validated JWT in the security context.
 *
 * <p>Our JWTs carry {@code sub = Account.id} (decimal), {@code jti} (UUID), and optionally
 * {@code act} (impersonator id). This is the single place those are parsed so controllers
 * stay declarative.
 *
 * <p>Becomes live at cutover (commit 16): until then the resource-server chain validates
 * Keycloak tokens whose {@code sub} is a UUID, not an account id — these accessors will
 * 401 on a Keycloak token, which is the expected pre-cutover behaviour for the new endpoints.
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
