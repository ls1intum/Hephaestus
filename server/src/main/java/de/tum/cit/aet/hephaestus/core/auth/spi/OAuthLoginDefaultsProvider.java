package de.tum.cit.aet.hephaestus.core.auth.spi;

import java.util.List;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

/**
 * Supplies the env-configured default OAuth login providers ({@code github},
 * {@code gitlab-lrz}) built from {@code hephaestus.auth.*}. Owned by {@code core.auth}
 * (which owns {@code AuthProperties}); consumed by the {@code integration}-side composite
 * {@code ClientRegistrationRepository}, which overlays these statics with the DB-backed
 * workspace OIDC-login {@code Connection} rows.
 *
 * <p>Inverting the wiring this way keeps the dependency direction correct: the secret-bearing
 * {@code Connection} access (and the resulting {@code ClientRegistrationRepository}) lives in
 * {@code integration}, while {@code core.auth} contributes only the env defaults through this
 * narrow port and consumes the Spring {@link org.springframework.security.oauth2.client.registration.ClientRegistrationRepository}
 * interface — never the integration implementation class.
 */
public interface OAuthLoginDefaultsProvider {
    /**
     * @return the env-configured default registrations, possibly empty (dev / CI with no
     *         OAuth credentials configured). Never {@code null}.
     */
    List<ClientRegistration> defaultRegistrations();
}
