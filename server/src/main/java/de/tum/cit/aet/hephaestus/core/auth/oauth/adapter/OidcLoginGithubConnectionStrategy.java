package de.tum.cit.aet.hephaestus.core.auth.oauth.adapter;

import de.tum.cit.aet.hephaestus.core.auth.oauth.IssuerDiscoveryProbe;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import org.springframework.stereotype.Component;

/** Binds {@link OidcLoginConnectionStrategy} to {@link IntegrationKind#OIDC_LOGIN_GITHUB}. */
@Component
public class OidcLoginGithubConnectionStrategy extends OidcLoginConnectionStrategy {

    public OidcLoginGithubConnectionStrategy(IssuerDiscoveryProbe probe) {
        super(probe);
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.OIDC_LOGIN_GITHUB;
    }
}
