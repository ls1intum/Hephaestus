package de.tum.cit.aet.hephaestus.core.auth.web;

import de.tum.cit.aet.hephaestus.core.auth.AccountBootstrapService;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Break-glass endpoint to mint the first instance super-admin when one cannot be predicted ahead of
 * time (the preferred path is the {@code hephaestus.auth.bootstrap-admins} allowlist). The
 * authenticated caller presents the {@code hephaestus.auth.bootstrap-token}; promotion succeeds only
 * while no admin exists yet (see {@link AccountBootstrapService}). {@code @Hidden} keeps it out of the
 * OpenAPI spec — it is an operator lever, not part of the public client surface.
 */
@Hidden
@ConditionalOnServerRole
@RestController
@RequestMapping("/auth")
public class AccountBootstrapController {

    private final AccountBootstrapService bootstrapService;

    public AccountBootstrapController(AccountBootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    public record BootstrapAdminRequestDTO(@NotBlank String token) {}

    @PostMapping("/bootstrap-admin")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> bootstrapAdmin(@Valid @RequestBody BootstrapAdminRequestDTO body) {
        bootstrapService.bootstrapFirstAdmin(CurrentAccount.requireId(), body.token());
        return ResponseEntity.noContent().build();
    }
}
