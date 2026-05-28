package de.tum.cit.aet.hephaestus.core.auth.impersonation;

import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventLogger;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.jwt.HephaestusJwtIssuer;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwt;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwtRepository;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Impersonation as JWT reissuance with the RFC 8693 {@code act} claim.
 *
 * <p>In a stateless cookie-JWT BFF there is no server session for {@code SwitchUserFilter}
 * to mutate — impersonation is "mint a token for the target carrying the operator's id in
 * {@code act}." {@link de.tum.cit.aet.hephaestus.core.auth.oauth.ImpersonationGuard}
 * enforces read-only-by-default on any request whose JWT carries {@code act}.
 *
 * <p>Both begin and exit are audited with the {@code (account_id=target,
 * acting_account_id=operator)} pair so every action taken under impersonation is
 * attributable to the operator.
 */
@Service
public class ImpersonationService {

    private static final Logger log = LoggerFactory.getLogger(ImpersonationService.class);

    private final AccountRepository accountRepository;
    private final HephaestusJwtIssuer jwtIssuer;
    private final IssuedJwtRepository issuedJwtRepository;
    private final AuthEventLogger authEventLogger;
    private final Clock clock;

    public ImpersonationService(
        AccountRepository accountRepository,
        HephaestusJwtIssuer jwtIssuer,
        IssuedJwtRepository issuedJwtRepository,
        AuthEventLogger authEventLogger,
        Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.jwtIssuer = jwtIssuer;
        this.issuedJwtRepository = issuedJwtRepository;
        this.authEventLogger = authEventLogger;
        this.clock = clock;
    }

    /** Result of begin/exit — the freshly-minted token the controller writes to the cookie. */
    public record Result(HephaestusJwtIssuer.Token token, Long targetAccountId, Long actingAccountId) {}

    /**
     * Begin impersonating {@code targetAccountId} as {@code operatorAccountId}. The operator
     * MUST be an {@code APP_ADMIN} (the controller enforces this via method security too;
     * we re-check here as defence in depth). A {@code reason} is mandatory and audited.
     */
    @Transactional
    public Result begin(Long operatorAccountId, Long targetAccountId, String reason, HttpServletRequest request) {
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "impersonation reason is required");
        }
        Account operator = accountRepository
            .findById(operatorAccountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "operator account not found"));
        if (operator.getAppRole() != Account.AppRole.APP_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only app admins may impersonate");
        }
        Account target = accountRepository
            .findById(targetAccountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "target account not found"));
        if (target.getId().equals(operator.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot impersonate yourself");
        }

        // Target always impersonated at USER scope — impersonation must never escalate.
        HephaestusJwtIssuer.Token token = jwtIssuer.issue(target.getId(), "user", operator.getId(), request);

        authEventLogger
            .event(AuthEvent.EventType.IMPERSONATION_BEGIN, AuthEvent.Result.SUCCESS)
            .account(target.getId())
            .actingAccount(operator.getId())
            .details("{\"reason\":\"" + escape(reason) + "\"}")
            .record();
        log.info("auth.impersonation: operator={} began impersonating target={}", operator.getId(), target.getId());

        return new Result(token, target.getId(), operator.getId());
    }

    /**
     * Exit impersonation: revoke the current impersonation JWT and mint a fresh operator
     * token. {@code currentJti} is the jti of the impersonation token (from the request's
     * validated JWT).
     */
    @Transactional
    public Result exit(Long operatorAccountId, Long targetAccountId, UUID currentJti, HttpServletRequest request) {
        issuedJwtRepository.revoke(currentJti, clock.instant(), IssuedJwt.RevokedReason.IMPERSONATION_EXIT);
        Account operator = accountRepository
            .findById(operatorAccountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "operator account not found"));
        String scope = operator.getAppRole() == Account.AppRole.APP_ADMIN ? "user app_admin" : "user";
        HephaestusJwtIssuer.Token token = jwtIssuer.issue(operator.getId(), scope, null, request);

        authEventLogger
            .event(AuthEvent.EventType.IMPERSONATION_END, AuthEvent.Result.SUCCESS)
            .account(targetAccountId)
            .actingAccount(operator.getId())
            .record();
        log.info("auth.impersonation: operator={} exited impersonation of target={}", operator.getId(), targetAccountId);

        return new Result(token, operator.getId(), null);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
