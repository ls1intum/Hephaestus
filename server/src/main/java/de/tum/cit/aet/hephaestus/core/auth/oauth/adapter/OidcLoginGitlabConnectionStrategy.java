package de.tum.cit.aet.hephaestus.core.auth.oauth.adapter;

import de.tum.cit.aet.hephaestus.core.auth.oauth.IssuerDiscoveryProbe;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import org.springframework.stereotype.Component;

/** Binds {@link OidcLoginConnectionStrategy} to {@link IntegrationKind#OIDC_LOGIN_GITLAB}. */
@Component
public class OidcLoginGitlabConnectionStrategy extends OidcLoginConnectionStrategy {

    public OidcLoginGitlabConnectionStrategy(IssuerDiscoveryProbe probe) {
        super(probe);
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.OIDC_LOGIN_GITLAB;
    }
}
