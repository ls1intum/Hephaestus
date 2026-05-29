package de.tum.cit.aet.hephaestus.integration.identity.connect;

import de.tum.cit.aet.hephaestus.core.auth.spi.OAuthLoginDefaultsProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.CredentialBundleConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the integration-side composite {@link LoginClientRegistrationRepository}.
 *
 * <p>The env-configured default registrations ({@code github}, {@code gitlab-lrz}) are
 * contributed by {@code core.auth} through the {@link OAuthLoginDefaultsProvider} port
 * (built from {@code hephaestus.auth.*}); the workspace-scoped OIDC-login providers are
 * materialized here from {@code Connection} rows. Exposing the composite as a bean of the
 * concrete type registers it under {@code ClientRegistrationRepository} (consumed by Spring
 * Security's {@code oauth2Login}), {@code IdentityProviderCatalog} (consumed by the discovery
 * controller), and {@code Iterable<ClientRegistration>} — replacing Boot's auto-configured
 * {@code InMemoryClientRegistrationRepository}, which backs off once this bean is present.
 */
@Configuration
public class IdentityLoginConfiguration {

    @Bean
    public LoginClientRegistrationRepository clientRegistrationRepository(
        OAuthLoginDefaultsProvider defaultsProvider,
        ConnectionRepository connectionRepository,
        CredentialBundleConverter credentialConverter
    ) {
        return new LoginClientRegistrationRepository(
            defaultsProvider.defaultRegistrations(),
            connectionRepository,
            credentialConverter
        );
    }
}
