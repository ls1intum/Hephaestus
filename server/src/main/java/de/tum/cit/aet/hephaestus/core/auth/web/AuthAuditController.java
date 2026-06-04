package de.tum.cit.aet.hephaestus.core.auth.web;

import de.tum.cit.aet.hephaestus.core.auth.audit.AuthAuditService;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only instance-admin viewer over the append-only {@code auth_event} log (see {@link AuthEvent}).
 * Guarded by the namespaced {@code app_admin} authority. Surfaces the {@code (account_id,
 * acting_account_id)} pair so impersonated actions stay attributable to their operator.
 */
@RestController
@RequestMapping("/admin/audit")
@Tag(name = "Admin", description = "Instance-admin account management")
@PreAuthorize("hasAuthority('app_admin')")
public class AuthAuditController {

    /** Hard cap on page size so a malicious/typo'd {@code size} can't scan a whole partition. */
    private static final int MAX_PAGE_SIZE = 200;

    private final AuthAuditService authAuditService;

    public AuthAuditController(AuthAuditService authAuditService) {
        this.authAuditService = authAuditService;
    }

    /** One audit row, flattened for the admin viewer. */
    public record AuthEventViewDTO(
        @NonNull Long id,
        @NonNull Instant occurredAt,
        @NonNull String eventType,
        @NonNull String result,
        @Nullable Long accountId,
        @Nullable Long actingAccountId,
        @Nullable String failureReason,
        @Nullable Long workspaceId,
        @Nullable String ipAddress,
        @Nullable String userAgent,
        @Nullable String details
    ) {}

    @GetMapping
    @Operation(summary = "List auth audit events (paged, newest first)", operationId = "adminListAuthEvents")
    public ResponseEntity<Page<AuthEventViewDTO>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @RequestParam(required = false) @Nullable Long accountId,
        @RequestParam(required = false) AuthEvent.@Nullable EventType eventType
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        // The query carries its own ORDER BY occurred_at DESC; keep the Pageable sort empty.
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<AuthEventViewDTO> events = authAuditService
            .list(accountId, eventType, pageable)
            .map(AuthAuditController::toView);
        return ResponseEntity.ok(events);
    }

    private static AuthEventViewDTO toView(AuthEvent e) {
        return new AuthEventViewDTO(
            e.getId().getId(),
            e.getId().getOccurredAt(),
            e.getEventType().name(),
            e.getResult().name(),
            e.getAccountId(),
            e.getActingAccountId(),
            e.getFailureReason(),
            e.getWorkspaceId(),
            e.getIpInet(),
            e.getUserAgent(),
            e.getDetails()
        );
    }
}
