package de.tum.cit.aet.hephaestus.core.auth;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventLogger;
import de.tum.cit.aet.hephaestus.core.auth.jwt.HephaestusJwtIssuer;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwt;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwtRepository;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwt.RevokedReason;
import de.tum.cit.aet.hephaestus.core.auth.jwt.JwtPrincipalFactory;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the access-token cookie lifecycle: logout, refresh, and the low-level
 * set/clear of the {@code __Host-} cookie. Extracted from {@code AuthLifecycleController}
 * so the controller stays thin (≤5 deps) and the cookie/JWT mechanics live in one place.
 */
@Service
@WorkspaceAgnostic("Session lifecycle is account-scoped, not workspace-scoped")
public class AuthSessionService {

    private final JwtPrincipalFactory principalFactory;
    private final IssuedJwtRepository issuedJwtRepository;
    private final HephaestusJwtIssuer jwtIssuer;
    private final AuthEventLogger authEventLogger;
    private final AuthProperties properties;
    private final Clock clock;

    public AuthSessionService(
        JwtPrincipalFactory principalFactory,
        IssuedJwtRepository issuedJwtRepository,
        HephaestusJwtIssuer jwtIssuer,
        AuthEventLogger authEventLogger,
        AuthProperties properties,
        Clock clock
    ) {
        this.principalFactory = principalFactory;
        this.issuedJwtRepository = issuedJwtRepository;
        this.jwtIssuer = jwtIssuer;
        this.authEventLogger = authEventLogger;
        this.properties = properties;
        this.clock = clock;
    }

    /** Revoke the presenting token and clear the cookie. */
    @Transactional
    public void logout(Long accountId, UUID jti, HttpServletResponse response) {
        issuedJwtRepository.revoke(jti, clock.instant(), IssuedJwt.RevokedReason.LOGOUT);
        authEventLogger.event(AuthEvent.EventType.LOGOUT, AuthEvent.Result.SUCCESS).account(accountId).record();
        clearCookie(response);
    }

    /** Rotate: revoke the presenting token, mint a fresh one (preserving impersonation), set cookie. */
    @Transactional
    public void refresh(
        Long accountId,
        UUID jti,
        @Nullable Long impersonatorId,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        issuedJwtRepository.revoke(jti, clock.instant(), IssuedJwt.RevokedReason.ROTATE);
        HephaestusJwtIssuer.Token token = jwtIssuer.issue(
            principalFactory.forAccountId(accountId),
            impersonatorId,
            request
        );
        authEventLogger.event(AuthEvent.EventType.TOKEN_REFRESH, AuthEvent.Result.SUCCESS).account(accountId).record();
        setCookie(response, token);
    }

    /** Write a freshly-minted token to the {@code __Host-} access cookie. */
    public void setCookie(HttpServletResponse response, HephaestusJwtIssuer.Token token) {
        long maxAge = token.expiresAt().getEpochSecond() - clock.instant().getEpochSecond();
        Cookie cookie = new Cookie(properties.cookieName(), token.value());
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) Math.max(0, maxAge));
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    /** Active (non-revoked, non-expired) sessions for an account. */
    public List<IssuedJwt> activeSessions(Long accountId) {
        Instant now = clock.instant();
        return issuedJwtRepository
            .findAll()
            .stream()
            .filter(j -> j.getAccountId().equals(accountId) && j.getRevokedAt() == null && j.getExpiresAt().isAfter(now))
            .toList();
    }

    /** Revoke a single session, only if it belongs to {@code accountId}. */
    @Transactional
    public void revokeSession(Long accountId, UUID jti) {
        issuedJwtRepository
            .findById(jti)
            .filter(j -> j.getAccountId().equals(accountId))
            .ifPresent(j -> issuedJwtRepository.revoke(jti, clock.instant(), RevokedReason.ADMIN_REVOKE));
    }

    /** Sign out everywhere except the presenting session. */
    @Transactional
    public void revokeAllExcept(Long accountId, UUID currentJti) {
        Instant now = clock.instant();
        issuedJwtRepository
            .findAll()
            .stream()
            .filter(j -> j.getAccountId().equals(accountId) && j.getRevokedAt() == null && !j.getJti().equals(currentJti))
            .forEach(j -> issuedJwtRepository.revoke(j.getJti(), now, RevokedReason.SIGN_OUT_EVERYWHERE));
    }

    public void clearCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(properties.cookieName(), "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }
}
