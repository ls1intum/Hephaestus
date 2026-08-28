package de.tum.cit.aet.hephaestus.core.auth.spi;

import java.util.List;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

public interface IdentityProviderCatalog {
    List<ClientRegistration> listRegistrations();

    boolean hasEnabledPrimarySignInProvider();
}
