package de.tum.cit.aet.hephaestus.integration.scm.gitlab.identity;

import de.tum.cit.aet.hephaestus.integration.core.spi.GitProviderServerUrlResolver;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabProperties;
import org.springframework.stereotype.Component;

/**
 * GitLab implementation of {@link GitProviderServerUrlResolver}.
 *
 * <p>Reads from {@link GitLabProperties#defaultServerUrl()} and strips any trailing
 * slash so callers can safely append path segments. The property is bound from
 * {@code hephaestus.gitlab.default-server-url} (default {@code https://gitlab.com}).
 */
@Component
public class GitLabServerUrlResolver implements GitProviderServerUrlResolver {

    private final GitLabProperties gitLabProperties;

    public GitLabServerUrlResolver(GitLabProperties gitLabProperties) {
        this.gitLabProperties = gitLabProperties;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITLAB;
    }

    @Override
    public String defaultServerUrl() {
        String url = gitLabProperties.defaultServerUrl();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
