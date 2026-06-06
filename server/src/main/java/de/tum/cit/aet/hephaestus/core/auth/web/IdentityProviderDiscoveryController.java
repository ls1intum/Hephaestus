package de.tum.cit.aet.hephaestus.core.auth.web;

import de.tum.cit.aet.hephaestus.core.auth.spi.IdentityProviderCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public discovery of the identity providers a user can sign in with (replaces the former
 * Keycloak {@code /auth/identity-providers}; ADR 0017). The SPA login
 * page renders one button per entry; each button targets
 * {@code /auth/login?provider={registrationId}}.
 *
 * <p>Lists every enabled instance-scoped {@code login_provider} (GitHub, GitLab.com, self-hosted
 * GitLab) — one shared registration per provider, reused across all workspaces.
 */
@RestController
@Tag(name = "Auth discovery", description = "Identity provider discovery (public)")
public class IdentityProviderDiscoveryController {

    private final IdentityProviderCatalog identityProviderCatalog;

    public IdentityProviderDiscoveryController(IdentityProviderCatalog identityProviderCatalog) {
        this.identityProviderCatalog = identityProviderCatalog;
    }

    /**
     * One row per sign-in option. {@code providerType} drives the SPA's icon choice; {@code baseUrl} is
     * the SCM instance origin so the workspace-creation wizard can match a target instance to its login.
     */
    public record IdentityProviderViewDTO(
        String registrationId,
        String displayName,
        String providerType,
        String baseUrl
    ) {}

    @GetMapping("/identity-providers")
    @PreAuthorize("permitAll()")
    @Operation(summary = "List available identity providers", operationId = "listIdentityProviders")
    public ResponseEntity<List<IdentityProviderViewDTO>> list() {
        List<IdentityProviderViewDTO> views = new ArrayList<>();
        for (ClientRegistration reg : identityProviderCatalog.listRegistrations()) {
            views.add(
                new IdentityProviderViewDTO(
                    reg.getRegistrationId(),
                    reg.getClientName() != null ? reg.getClientName() : reg.getRegistrationId(),
                    providerTypeOf(reg),
                    baseUrlOf(reg)
                )
            );
        }
        return ResponseEntity.ok(views);
    }

    /**
     * The provider type drives the SPA's icon choice. Derived from the authorization endpoint host
     * (the only GitHub login target is github.com; everything else is a GitLab instance), so it works
     * for any admin-registered self-hosted GitLab without relying on the registration-id naming.
     */
    private static String providerTypeOf(ClientRegistration reg) {
        // Match on the parsed HOST, not a substring of the whole URI — "github.com" appearing in a
        // path/query of a GitLab instance (or a look-alike host) must not be misclassified as GitHub.
        return "github.com".equals(hostOf(reg)) ? "GITHUB" : "GITLAB";
    }

    /** Host of the authorization endpoint, or {@code null} if absent/malformed. */
    private static String hostOf(ClientRegistration reg) {
        String authorizationUri = reg.getProviderDetails().getAuthorizationUri();
        if (authorizationUri == null) {
            return null;
        }
        try {
            return new java.net.URI(authorizationUri).getHost();
        } catch (java.net.URISyntaxException e) {
            return null;
        }
    }

    /** The SCM instance origin (scheme + host[:port]) derived from the authorization endpoint. */
    private static String baseUrlOf(ClientRegistration reg) {
        String authorizationUri = reg.getProviderDetails().getAuthorizationUri();
        if (authorizationUri == null) {
            return "";
        }
        try {
            java.net.URI uri = new java.net.URI(authorizationUri);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return "";
            }
            String origin = uri.getScheme() + "://" + uri.getHost();
            return uri.getPort() == -1 ? origin : origin + ":" + uri.getPort();
        } catch (java.net.URISyntaxException e) {
            return "";
        }
    }
}
