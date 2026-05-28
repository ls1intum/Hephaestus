package de.tum.cit.aet.hephaestus.integration.scm.github.identity;

import de.tum.cit.aet.hephaestus.integration.core.spi.GitProviderServerUrlResolver;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import org.springframework.stereotype.Component;

/**
 * GitHub implementation of {@link GitProviderServerUrlResolver}.
 *
 * <p>Returns the public github.com server URL. GitHub Enterprise Server support is
 * deferred until {@code GitHubProperties} grows a {@code serverUrl} field; once it
 * does, this resolver should read it (falling back to {@code https://github.com} when
 * unset) without any change to the SPI or its consumers.
 */
@Component
public class GitHubServerUrlResolver implements GitProviderServerUrlResolver {

    private static final String GITHUB_DEFAULT_SERVER_URL = "https://github.com";

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    @Override
    public String defaultServerUrl() {
        return GITHUB_DEFAULT_SERVER_URL;
    }
}
