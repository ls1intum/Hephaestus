package de.tum.cit.aet.hephaestus.core.auth.spi;

import java.util.List;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

/**
 * Enumerates every enabled sign-in option a user may pick, drawn from the instance-scoped
 * {@code login_provider} store. Backs the public identity-provider discovery endpoint.
 *
 * <p>Implemented by {@code core.auth.provider.LoginProviderClientRegistrationRepository} (the
 * DB-backed {@code ClientRegistrationRepository} that displaces Boot's in-memory bean); consumed by
 * {@code core.auth}'s discovery controller through this port. Returns Spring Security
 * {@link ClientRegistration}s — a framework-neutral type both sides already speak.
 */
public interface IdentityProviderCatalog {
    /**
     * @return the client registrations for all enabled login providers. Never {@code null}.
     */
    List<ClientRegistration> listRegistrations();
}
