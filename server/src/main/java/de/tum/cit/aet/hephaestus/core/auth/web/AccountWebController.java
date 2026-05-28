package de.tum.cit.aet.hephaestus.core.auth.web;

import de.tum.cit.aet.hephaestus.core.auth.AccountService;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLink;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The current-user singleton: identity, linked accounts, and GDPR account deletion.
 * Thin HTTP adapter — all data access lives in {@link AccountService}.
 */
@RestController
@RequestMapping("/user")
@Tag(name = "Account", description = "Current user identity, linked accounts, deletion")
@PreAuthorize("isAuthenticated()")
public class AccountWebController {

    private final AccountService accountService;

    public AccountWebController(AccountService accountService) {
        this.accountService = accountService;
    }

    public record CurrentUserView(
        Long id,
        String displayName,
        @Nullable String primaryEmail,
        String appRole,
        String status,
        boolean impersonating,
        @Nullable Long impersonatorId,
        // Identity fields the SPA's useAuth() surface needs (sourced from the primary IdentityLink).
        @Nullable String username,
        @Nullable String avatarUrl,
        @Nullable String profileUrl,
        @Nullable String identityProvider,
        @Nullable String gitProviderId,
        boolean hasGitLabIdentity,
        java.util.List<String> roles
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
        Account account = accountService.requireById(CurrentAccount.requireId());
        Long impersonatorId = CurrentAccount.impersonatorId();
        var identities = accountService.activeIdentities(account.getId());
        // Primary identity = most recently used active link (login source for the SPA).
        IdentityLink primary = identities.stream().findFirst().orElse(null);
        boolean hasGitLab = identities
            .stream()
            .anyMatch(il -> il.getGitProvider() != null && "GITLAB".equals(il.getGitProvider().getType().name()));
        return ResponseEntity.ok(
            new CurrentUserView(
                account.getId(),
                account.getDisplayName(),
                account.getPrimaryEmail(),
                account.getAppRole().name(),
                account.getStatus().name(),
                impersonatorId != null,
                impersonatorId,
                primary != null ? primary.getUsernameAtSignup() : account.getDisplayName(),
                primary != null ? primary.getAvatarUrl() : null,
                primary != null ? primary.getProfileUrl() : null,
                primary != null && primary.getGitProvider() != null
                    ? primary.getGitProvider().getType().name()
                    : null,
                primary != null ? primary.getSubject() : null,
                hasGitLab,
                CurrentAccount.roles()
            )
        );
    }

    @GetMapping("/identities")
    @Operation(summary = "List linked identity providers", operationId = "listLinkedIdentities")
    public ResponseEntity<List<IdentityView>> identities() {
        List<IdentityView> views = accountService
            .activeIdentities(CurrentAccount.requireId())
            .stream()
            .map(AccountWebController::toView)
            .toList();
        return ResponseEntity.ok(views);
    }

    @DeleteMapping
    @Operation(summary = "Delete the current account (GDPR Art. 17)", operationId = "deleteCurrentUser")
    public ResponseEntity<Void> deleteAccount(
        @RequestHeader(value = "X-Confirm-Delete", required = false) @Nullable String confirmHeader
    ) {
        Long accountId = CurrentAccount.requireId();
        if (confirmHeader == null || !confirmHeader.equals(String.valueOf(accountId))) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "X-Confirm-Delete header must equal your account id to confirm deletion"
            );
        }
        accountService.softDelete(accountId);
        return ResponseEntity.noContent().build();
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
