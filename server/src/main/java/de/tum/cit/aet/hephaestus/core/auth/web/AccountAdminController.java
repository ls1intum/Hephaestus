package de.tum.cit.aet.hephaestus.core.auth.web;

import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventLogger;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Super-admin account management. Guarded by the {@code app_admin} scope (set in our JWT
 * for {@code APP_ADMIN} accounts). v1 surface: list + set app role. Feature-flag toggles +
 * cursor pagination layer on once {@code AccountFeatureRepository} and the cursor helper
 * land.
 */
@RestController
@RequestMapping("/admin/users")
@Tag(name = "Admin", description = "Super-admin account management")
@PreAuthorize("hasAuthority('SCOPE_app_admin')")
public class AccountAdminController {

    private final AccountRepository accountRepository;
    private final AuthEventLogger authEventLogger;

    public AccountAdminController(AccountRepository accountRepository, AuthEventLogger authEventLogger) {
        this.accountRepository = accountRepository;
        this.authEventLogger = authEventLogger;
    }

    public record AdminAccountView(Long id, String displayName, String primaryEmail, String appRole, String status) {}

    public record UpdateAccountRequest(@Nullable String appRole) {}

    @GetMapping
    @Operation(summary = "List accounts (paged)", operationId = "adminListUsers")
    public ResponseEntity<List<AdminAccountView>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        int capped = Math.min(Math.max(size, 1), 200);
        List<AdminAccountView> views = accountRepository
            .findAll(PageRequest.of(Math.max(page, 0), capped))
            .map(a ->
                new AdminAccountView(
                    a.getId(),
                    a.getDisplayName(),
                    a.getPrimaryEmail(),
                    a.getAppRole().name(),
                    a.getStatus().name()
                )
            )
            .getContent();
        return ResponseEntity.ok(views);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update an account's app role", operationId = "adminUpdateUser")
    @Transactional
    public ResponseEntity<AdminAccountView> update(@PathVariable Long id, @RequestBody UpdateAccountRequest body) {
        Account account = accountRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found"));
        if (body.appRole() != null) {
            Account.AppRole role;
            try {
                role = Account.AppRole.valueOf(body.appRole());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "unknown app role: " + body.appRole());
            }
            account.setAppRole(role);
            accountRepository.save(account);
            authEventLogger
                .event(AuthEvent.EventType.FEATURE_FLAG_CHANGED, AuthEvent.Result.SUCCESS)
                .account(account.getId())
                .actingAccount(CurrentAccount.requireId())
                .details("{\"appRole\":\"" + role.name() + "\"}")
                .record();
        }
        return ResponseEntity.ok(
            new AdminAccountView(
                account.getId(),
                account.getDisplayName(),
                account.getPrimaryEmail(),
                account.getAppRole().name(),
                account.getStatus().name()
            )
        );
    }
}
