package de.tum.cit.aet.hephaestus.integration.identity.connect;

import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderType;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration-side implementation of the {@link GitProviderRegistry} auth SPI: upserts the
 * {@code git_provider} row for a login provider so {@code core.auth}'s account provisioning can key
 * the {@code IdentityLink} by {@code (git_provider_id, subject)} without importing the
 * {@link GitProvider} entity. {@code core.auth} passes the provider's {@code (type, baseUrl)} (read
 * from its own {@code login_provider} store); this side owns the {@code GitProvider} row and
 * canonicalizes {@code baseUrl} to the server-url origin.
 */
@Component
public class RegistrationToGitProviderResolver implements GitProviderRegistry {

    private static final String UNKNOWN = "UNKNOWN";

    private final GitProviderRepository gitProviderRepository;

    public RegistrationToGitProviderResolver(GitProviderRepository gitProviderRepository) {
        this.gitProviderRepository = gitProviderRepository;
    }

    @Override
    @Transactional
    public long resolveProviderId(String providerTypeName, String baseUrl) {
        GitProviderType type = GitProviderType.valueOf(providerTypeName);
        return gitProviderRepository
            .findByTypeAndServerUrl(type, originOf(baseUrl))
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(type, originOf(baseUrl))))
            .getId();
    }

    @Override
    @Transactional(readOnly = true)
    public String providerTypeName(Long gitProviderId) {
        if (gitProviderId == null) {
            return UNKNOWN;
        }
        return gitProviderRepository
            .findById(gitProviderId)
            .map(p -> p.getType().name())
            .orElse(UNKNOWN);
    }

    @Override
    @Transactional(readOnly = true)
    public String providerServerUrl(Long gitProviderId) {
        if (gitProviderId == null) {
            return null;
        }
        return gitProviderRepository.findById(gitProviderId).map(GitProvider::getServerUrl).orElse(null);
    }

    /**
     * The scheme + host (+ explicit non-default port) of the base URL — the canonical server URL used
     * to key the {@code git_provider} row (e.g. {@code https://github.com}, {@code https://gitlab.lrz.de}).
     */
    private static String originOf(String baseUrl) {
        try {
            URI uri = new URI(baseUrl);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalStateException("login provider baseUrl has no scheme/host: " + baseUrl);
            }
            String origin = uri.getScheme() + "://" + uri.getHost();
            return uri.getPort() == -1 ? origin : origin + ":" + uri.getPort();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("malformed login provider baseUrl: " + baseUrl, e);
        }
    }
}
