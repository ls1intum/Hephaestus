package de.tum.cit.aet.hephaestus.core.auth.spi;

import java.util.List;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

/**
 * Enumerates every sign-in option a user may pick — the env-configured defaults plus the
 * active workspace-scoped OIDC-login {@code Connection} rows. Backs the public
 * identity-provider discovery endpoint.
 *
 * <p>Owned by {@code integration} (the composite {@code ClientRegistrationRepository} lives
 * there with the secret-bearing {@code Connection} access); consumed by {@code core.auth}'s
 * discovery controller through this port so the controller never imports the integration
 * repository class. Returns Spring Security {@link ClientRegistration}s — a framework-neutral
 * type both sides already speak.
 */
public interface IdentityProviderCatalog {
    /**
     * @return all currently resolvable client registrations (env defaults + active workspace
     *         OIDC-login Connections). Never {@code null}.
     */
    List<ClientRegistration> listRegistrations();
}
