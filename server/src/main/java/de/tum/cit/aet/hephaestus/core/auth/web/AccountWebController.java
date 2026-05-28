package de.tum.cit.aet.hephaestus.core.auth.web;

import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventLogger;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLink;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLinkRepository;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwt;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwtRepository;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The current-user singleton: identity, linked accounts, and GDPR account deletion.
 *
 * <p>Identity is resolved from the validated JWT ({@code sub = Account.id}) via
 * {@link CurrentAccount}. Becomes fully live at cutover (commit 16) when the resource-server
 * chain validates our tokens instead of Keycloak's.
 */
@RestController
@RequestMapping("/user")
@Tag(name = "Account", description = "Current user identity, linked accounts, deletion")
@PreAuthorize("isAuthenticated()")
public class AccountWebController {

    private final AccountRepository accountRepository;
    private final IdentityLinkRepository identityLinkRepository;
    private final IssuedJwtRepository issuedJwtRepository;
    private final AuthEventLogger authEventLogger;
    private final Clock clock;

    public AccountWebController(
        AccountRepository accountRepository,
        IdentityLinkRepository identityLinkRepository,
        IssuedJwtRepository issuedJwtRepository,
        AuthEventLogger authEventLogger,
        Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.identityLinkRepository = identityLinkRepository;
        this.issuedJwtRepository = issuedJwtRepository;
        this.authEventLogger = authEventLogger;
        this.clock = clock;
    }

    public record CurrentUserView(
        Long id,
        String displayName,
        @Nullable String primaryEmail,
        String appRole,
        String status,
        boolean impersonating,
        @Nullable Long impersonatorId
    ) {}

    public record IdentityView(
        Long id,
        String providerType,
        String subject,
        @Nullable String username,
        @Nullable String displayName,
        @Nullable String avatarUrl,
        @Nullable Instant lastLoginAt
    ) {}

    @GetMapping
    @Operation(summary = "Get the current user", operationId = "getCurrentUser")
    public ResponseEntity<CurrentUserView> currentUser() {
        Account account = loadCurrent();
        Long impersonatorId = CurrentAccount.impersonatorId();
        return ResponseEntity.ok(
            new CurrentUserView(
                account.getId(),
                account.getDisplayName(),
                account.getPrimaryEmail(),
                account.getAppRole().name(),
                account.getStatus().name(),
                impersonatorId != null,
                impersonatorId
            )
        );
    }

    @GetMapping("/identities")
    @Operation(summary = "List linked identity providers", operationId = "listLinkedIdentities")
    public ResponseEntity<List<IdentityView>> identities() {
        Account account = loadCurrent();
        List<IdentityView> views = identityLinkRepository
            .findAll()
            .stream()
            .filter(il -> il.getAccount().getId().equals(account.getId()) && il.getDisabledAt() == null)
            .map(AccountWebController::toView)
            .toList();
        return ResponseEntity.ok(views);
    }

    /**
     * GDPR Art. 17 deletion. v1 performs the soft-delete leg: status → DELETING, stamp
     * deleted_at (start of the 48h cooldown), revoke all sessions immediately. The hard
     * cascade + ExternalActor pseudonymization runs in a scheduled sweep once the cooldown
     * expires (sweep job lands with the GDPR commit). Requires a typed confirmation header
     * to defend against a leaked cookie triggering deletion.
     */
    @DeleteMapping
    @Operation(summary = "Delete the current account (GDPR Art. 17)", operationId = "deleteCurrentUser")
    @Transactional
    public ResponseEntity<Void> deleteAccount(
        @RequestHeader(value = "X-Confirm-Delete", required = false) @Nullable String confirmHeader,
        HttpServletResponse response
    ) {
        Account account = loadCurrent();
        if (confirmHeader == null || !confirmHeader.equals(String.valueOf(account.getId()))) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "X-Confirm-Delete header must equal your account id to confirm deletion"
            );
        }
        account.setStatus(Account.Status.DELETING);
        account.setDeletedAt(clock.instant());
        accountRepository.save(account);
        issuedJwtRepository.revokeAllForAccount(account.getId(), clock.instant(), IssuedJwt.RevokedReason.ACCOUNT_DELETED);
        authEventLogger.event(AuthEvent.EventType.ACCOUNT_DELETED, AuthEvent.Result.SUCCESS).account(account.getId()).record();
        return ResponseEntity.noContent().build();
    }

    private Account loadCurrent() {
        Long id = CurrentAccount.requireId();
        return accountRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found"));
    }

    private static IdentityView toView(IdentityLink il) {
        String providerType = il.getGitProvider() != null ? il.getGitProvider().getType().name() : "OIDC";
        return new IdentityView(
            il.getId(),
            providerType,
            il.getSubject(),
            il.getUsernameAtSignup(),
            il.getDisplayName(),
            il.getAvatarUrl(),
            il.getLastLoginAt()
        );
    }
}
