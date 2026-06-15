package de.tum.cit.aet.hephaestus.core.auth.web;

import de.tum.cit.aet.hephaestus.core.auth.AuthSessionService;
import de.tum.cit.aet.hephaestus.core.auth.dev.DevLoginService;
import de.tum.cit.aet.hephaestus.core.auth.jwt.HephaestusJwtIssuer;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Passwordless dev/test sign-in. Mints the production cookie-session for an arbitrary local
 * {@link de.tum.cit.aet.hephaestus.core.auth.domain.Account} without an OAuth IdP, so local dev and
 * live (Playwright) E2E can authenticate over real HTTP (which {@code @WithMockUser}/{@code TestAuthUtils}
 * cannot do). {@code @Hidden}: an operator/dev lever, not part of the public client surface.
 *
 * <p>Gated by {@code hephaestus.auth.dev-login-enabled} — disabled by default (404, invisible) and
 * fail-closed in production (see {@link DevLoginService}). Permitted in {@code SecurityConfig} only when
 * the flag is on.
 */
@Hidden
@ConditionalOnServerRole
@RestController
@RequestMapping("/auth")
// Public by design: access is gated by the dev-login flag + SecurityConfig (fail-closed in prod), not by
// roles. The explicit permitAll declaration satisfies the "every endpoint declares its security" arch rule
// (mirrors DevTriggerController).
@PreAuthorize("permitAll()")
public class DevLoginController {

    private final DevLoginService devLoginService;
    private final AuthSessionService authSessionService;

    public DevLoginController(DevLoginService devLoginService, AuthSessionService authSessionService) {
        this.devLoginService = devLoginService;
        this.authSessionService = authSessionService;
    }

    public record DevLoginRequestDTO(@NotBlank String username, @Nullable String displayName, boolean admin) {}

    @PostMapping("/dev-login")
    public ResponseEntity<Void> devLogin(
        @Valid @RequestBody DevLoginRequestDTO body,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        HephaestusJwtIssuer.Token token = devLoginService.devLogin(
            body.username(),
            body.displayName(),
            body.admin(),
            request
        );
        authSessionService.setCookie(response, token);
        return ResponseEntity.noContent().build();
    }
}
