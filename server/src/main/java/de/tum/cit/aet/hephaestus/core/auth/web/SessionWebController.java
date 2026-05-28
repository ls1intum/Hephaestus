package de.tum.cit.aet.hephaestus.core.auth.web;

import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwt;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwtRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Active-session inventory + revocation. Each non-revoked, non-expired {@link IssuedJwt}
 * row for the current account is a "session". Backs the SPA's "Active sessions" /
 * "Sign out everywhere" UI.
 */
@RestController
@RequestMapping("/user/sessions")
@Tag(name = "Account", description = "Active sessions")
@PreAuthorize("isAuthenticated()")
public class SessionWebController {

    private final IssuedJwtRepository issuedJwtRepository;
    private final Clock clock;

    public SessionWebController(IssuedJwtRepository issuedJwtRepository, Clock clock) {
        this.issuedJwtRepository = issuedJwtRepository;
        this.clock = clock;
    }

    public record SessionView(
        UUID jti,
        Instant issuedAt,
        Instant expiresAt,
        String userAgent,
        String ip,
        boolean current
    ) {}

    @GetMapping
    @Operation(summary = "List active sessions for the current user", operationId = "listSessions")
    public ResponseEntity<List<SessionView>> list() {
        Long accountId = CurrentAccount.requireId();
        UUID currentJti = CurrentAccount.requireJti();
        Instant now = clock.instant();
        List<SessionView> views = issuedJwtRepository
            .findAll()
            .stream()
            .filter(j ->
                j.getAccountId().equals(accountId) && j.getRevokedAt() == null && j.getExpiresAt().isAfter(now)
            )
            .map(j ->
                new SessionView(
                    j.getJti(),
                    j.getIssuedAt(),
                    j.getExpiresAt(),
                    j.getUserAgent(),
                    j.getIpInet(),
                    j.getJti().equals(currentJti)
                )
            )
            .toList();
        return ResponseEntity.ok(views);
    }

    @DeleteMapping("/{jti}")
    @Operation(summary = "Revoke a single session", operationId = "revokeSession")
    @Transactional
    public ResponseEntity<Void> revoke(@PathVariable UUID jti) {
        // Only the owner may revoke; findActive scopes by jti and we re-check account ownership.
        Long accountId = CurrentAccount.requireId();
        issuedJwtRepository
            .findById(jti)
            .filter(j -> j.getAccountId().equals(accountId))
            .ifPresent(j -> issuedJwtRepository.revoke(jti, clock.instant(), IssuedJwt.RevokedReason.ADMIN_REVOKE));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @Operation(summary = "Revoke all sessions except the current one", operationId = "revokeOtherSessions")
    @Transactional
    public ResponseEntity<Void> revokeAllOthers() {
        Long accountId = CurrentAccount.requireId();
        UUID currentJti = CurrentAccount.requireJti();
        Instant now = clock.instant();
        issuedJwtRepository
            .findAll()
            .stream()
            .filter(j ->
                j.getAccountId().equals(accountId) &&
                j.getRevokedAt() == null &&
                !j.getJti().equals(currentJti)
            )
            .forEach(j -> issuedJwtRepository.revoke(j.getJti(), now, IssuedJwt.RevokedReason.SIGN_OUT_EVERYWHERE));
        return ResponseEntity.noContent().build();
    }
}
