package de.tum.cit.aet.hephaestus.core.auth.web;

import de.tum.cit.aet.hephaestus.core.auth.AccountService;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Super-admin account management, guarded by the {@code app_admin} scope. Thin adapter over
 * {@link AccountService}.
 */
@RestController
@RequestMapping("/admin/users")
@Tag(name = "Admin", description = "Super-admin account management")
@PreAuthorize("hasAuthority('admin')")
public class AccountAdminController {

    private final AccountService accountService;

    public AccountAdminController(AccountService accountService) {
        this.accountService = accountService;
    }

    public record AdminAccountViewDTO(Long id, String displayName, String primaryEmail, String appRole, String status) {}

    public record UpdateAccountRequestDTO(@Nullable String appRole) {}

    @GetMapping
    @Operation(summary = "List accounts (paged)", operationId = "adminListUsers")
    public ResponseEntity<List<AdminAccountViewDTO>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        List<AdminAccountViewDTO> views = accountService
            .adminList(page, size)
            .stream()
            .map(AccountAdminController::toView)
            .toList();
        return ResponseEntity.ok(views);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update an account's app role", operationId = "adminUpdateUser")
    public ResponseEntity<AdminAccountViewDTO> update(@PathVariable Long id, @RequestBody UpdateAccountRequestDTO body) {
        Account account = accountService.adminSetRole(id, body.appRole(), CurrentAccount.requireId());
        return ResponseEntity.ok(toView(account));
    }

    private static AdminAccountViewDTO toView(Account a) {
        return new AdminAccountViewDTO(
            a.getId(),
            a.getDisplayName(),
            a.getPrimaryEmail(),
            a.getAppRole().name(),
            a.getStatus().name()
        );
    }
}
