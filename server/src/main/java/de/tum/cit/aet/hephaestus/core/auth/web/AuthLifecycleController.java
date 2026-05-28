package de.tum.cit.aet.hephaestus.core.auth.web;

import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventLogger;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.impersonation.ImpersonationService;
import de.tum.cit.aet.hephaestus.core.auth.jwt.HephaestusJwtIssuer;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwt;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwtRepository;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Session-lifecycle verbs: logout, refresh, impersonate, impersonate:exit. Strictly
 * session control — identity reads live on {@code /user}.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Session lifecycle")
public class AuthLifecycleController {

    private final AccountRepository accountRepository;
    private final IssuedJwtRepository issuedJwtRepository;
    private final HephaestusJwtIssuer jwtIssuer;
    private final ImpersonationService impersonationService;
    private final AuthEventLogger authEventLogger;
    private final AuthProperties properties;
    private final Clock clock;

    public AuthLifecycleController(
        AccountRepository accountRepository,
        IssuedJwtRepository issuedJwtRepository,
        HephaestusJwtIssuer jwtIssuer,
        ImpersonationService impersonationService,
        AuthEventLogger authEventLogger,
        AuthProperties properties,
        Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.issuedJwtRepository = issuedJwtRepository;
        this.jwtIssuer = jwtIssuer;
        this.impersonationService = impersonationService;
        this.authEventLogger = authEventLogger;
        this.properties = properties;
        this.clock = clock;
    }

    public record ImpersonateRequest(@NotNull Long targetAccountId, @NotBlank String reason) {}

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Log out — revoke the current token + clear the cookie", operationId = "logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        issuedJwtRepository.revoke(CurrentAccount.requireJti(), clock.instant(), IssuedJwt.RevokedReason.LOGOUT);
        authEventLogger.event(AuthEvent.EventType.LOGOUT, AuthEvent.Result.SUCCESS).account(CurrentAccount.requireId()).record();
        clearCookie(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Rotate the access token (new jti, old revoked)", operationId = "refresh")
    public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
        Long accountId = CurrentAccount.requireId();
        Account account = accountRepository
            .findById(accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "account not found"));
        // Rotate: revoke the presenting jti, mint a fresh one.
        issuedJwtRepository.revoke(CurrentAccount.requireJti(), clock.instant(), IssuedJwt.RevokedReason.ROTATE);
        Long impersonatorId = CurrentAccount.impersonatorId();
        String scope = account.getAppRole() == Account.AppRole.APP_ADMIN ? "user app_admin" : "user";
        HephaestusJwtIssuer.Token token = jwtIssuer.issue(accountId, scope, impersonatorId, request);
        authEventLogger.event(AuthEvent.EventType.TOKEN_REFRESH, AuthEvent.Result.SUCCESS).account(accountId).record();
        setCookie(response, token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/impersonate")
    @PreAuthorize("hasAuthority('SCOPE_app_admin')")
    @Operation(summary = "Begin impersonating another account", operationId = "impersonate")
    public ResponseEntity<Void> impersonate(
        @RequestBody ImpersonateRequest body,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        ImpersonationService.Result result = impersonationService.begin(
            CurrentAccount.requireId(),
            body.targetAccountId(),
            body.reason(),
            request
        );
        setCookie(response, result.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/impersonate:exit")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Exit impersonation, restore operator session", operationId = "exitImpersonation")
    public ResponseEntity<Void> exitImpersonation(HttpServletRequest request, HttpServletResponse response) {
        Long impersonatorId = CurrentAccount.impersonatorId();
        if (impersonatorId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "not currently impersonating");
        }
        ImpersonationService.Result result = impersonationService.exit(
            impersonatorId,
            CurrentAccount.requireId(),
            CurrentAccount.requireJti(),
            request
        );
        setCookie(response, result.token());
        return ResponseEntity.noContent().build();
    }

    private void setCookie(HttpServletResponse response, HephaestusJwtIssuer.Token token) {
        long maxAge = token.expiresAt().getEpochSecond() - clock.instant().getEpochSecond();
        Cookie cookie = new Cookie(properties.cookieName(), token.value());
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) Math.max(0, maxAge));
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    private void clearCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(properties.cookieName(), "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }
}
