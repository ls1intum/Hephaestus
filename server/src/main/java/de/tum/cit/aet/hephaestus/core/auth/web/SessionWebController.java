package de.tum.cit.aet.hephaestus.core.auth.web;

import de.tum.cit.aet.hephaestus.core.auth.AuthSessionService;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Active-session inventory + revocation. Each non-revoked, non-expired issued JWT for the
 * current account is a "session". Thin adapter over {@link AuthSessionService}.
 */
@ConditionalOnServerRole
@RestController
@RequestMapping("/user/sessions")
@Tag(name = "Account", description = "Active sessions")
@PreAuthorize("isAuthenticated()")
public class SessionWebController {

    private final AuthSessionService sessionService;

    public SessionWebController(AuthSessionService sessionService) {
        this.sessionService = sessionService;
    }

    public record SessionViewDTO(
        UUID jti,
        Instant issuedAt,
        Instant expiresAt,
        String userAgent,
        String ip,
        boolean current
    ) {}

    @GetMapping
    @Operation(summary = "List active sessions for the current user", operationId = "listSessions")
    public ResponseEntity<List<SessionViewDTO>> list() {
        UUID currentJti = CurrentAccount.requireJti();
        List<SessionViewDTO> views = sessionService
            .activeSessions(CurrentAccount.requireId())
            .stream()
            .map(j ->
                new SessionViewDTO(
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
    public ResponseEntity<Void> revoke(@PathVariable UUID jti) {
        sessionService.revokeSession(CurrentAccount.requireId(), jti);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @Operation(summary = "Revoke all sessions except the current one", operationId = "revokeOtherSessions")
    public ResponseEntity<Void> revokeAllOthers() {
        sessionService.revokeAllExcept(CurrentAccount.requireId(), CurrentAccount.requireJti());
        return ResponseEntity.noContent().build();
    }
}
