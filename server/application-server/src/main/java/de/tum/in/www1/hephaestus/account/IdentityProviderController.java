package de.tum.in.www1.hephaestus.account;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public controller for identity provider discovery.
 * No authentication required — used by the login UI to show available sign-in options.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Authentication discovery (public)")
public class IdentityProviderController {

    private final AccountService accountService;

    public IdentityProviderController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/identity-providers")
    @Operation(
        summary = "List available identity providers",
        description = "Returns all enabled identity providers that can be used for login. Public endpoint — no authentication required."
    )
    public ResponseEntity<List<IdentityProviderDTO>> getIdentityProviders() {
        return ResponseEntity.ok(accountService.getAvailableIdentityProviders());
    }
}
