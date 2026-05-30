package de.tum.cit.aet.hephaestus.core.auth;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventLogger;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.jwt.HephaestusJwtIssuer;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwt;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwt.RevokedReason;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwtRepository;
import de.tum.cit.aet.hephaestus.core.auth.jwt.JwtPrincipalFactory;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
    private final AccountRepository accountRepository;
    private final HephaestusJwtIssuer jwtIssuer;
    private final AuthEventLogger authEventLogger;
    private final AuthProperties properties;
    private final Clock clock;

    public AuthSessionService(
        JwtPrincipalFactory principalFactory,
        IssuedJwtRepository issuedJwtRepository,
        AccountRepository accountRepository,
        HephaestusJwtIssuer jwtIssuer,
        AuthEventLogger authEventLogger,
        AuthProperties properties,
        Clock clock
    ) {
        this.principalFactory = principalFactory;
        this.issuedJwtRepository = issuedJwtRepository;
        this.accountRepository = accountRepository;
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
        // Atomically revoke the presenting token. The conditional UPDATE (revokedAt IS NULL) affects
        // 0 rows when a concurrent refresh/logout already rotated this jti — in that race we must NOT
        // mint a fresh token (it would resurrect a session the other request meant to end). No-op and
        // clear the cookie so the client re-authenticates.
        int revoked = issuedJwtRepository.revoke(jti, clock.instant(), IssuedJwt.RevokedReason.ROTATE);
        if (revoked == 0) {
            clearCookie(response);
            return;
        }
        // Account-status gate (ADR 0017). A SUSPENDED / DELETING / DELETED account must not be able to
        // rotate its session into a fresh JWT — that would keep a suspended/deleting principal alive
        // indefinitely. The presenting token is already revoked above; we simply do NOT re-mint, clear
        // the cookie, and end the session. (forAccountId would also reject as defense-in-depth.)
        Account account = accountRepository.findById(accountId).orElse(null);
        if (account == null || account.getStatus() != Account.Status.ACTIVE) {
            clearCookie(response);
            return;
        }
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
        return issuedJwtRepository.findActiveByAccountId(accountId, clock.instant());
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
        issuedJwtRepository.revokeAllForAccountExcept(
            accountId,
            currentJti,
            clock.instant(),
            RevokedReason.SIGN_OUT_EVERYWHERE
        );
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
