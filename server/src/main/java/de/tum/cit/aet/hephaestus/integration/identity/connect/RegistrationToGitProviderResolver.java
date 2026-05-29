package de.tum.cit.aet.hephaestus.integration.identity.connect;

import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration-side implementation of the {@link GitProviderRegistry} auth SPI: maps a Spring
 * {@code ClientRegistration} id to the persistent {@link GitProvider} row id (creating the row
 * on first sight). Used by {@code core.auth}'s account-provisioning to key the
 * {@code IdentityLink} by {@code (git_provider_id, subject)} from the
 * {@code OAuth2AuthenticationToken} returned by {@code oauth2Login} — without {@code core.auth}
 * importing the {@link GitProvider} entity.
 *
 * <p>Hard-coded mapping for the two env-default registrations until the composite
 * {@link LoginClientRegistrationRepository} for workspace-scoped Connections lands. Workspace
 * registration ids will follow the {@code gh-ws-{id}} / {@code gl-ws-{id}} pattern — the
 * dispatch below leaves space for that without needing more entries here.
 */
@Component
public class RegistrationToGitProviderResolver implements GitProviderRegistry {

    private static final String GITHUB_COM = "https://github.com";
    private static final String GITLAB_LRZ = "https://gitlab.lrz.de";

    private final GitProviderRepository gitProviderRepository;

    public RegistrationToGitProviderResolver(GitProviderRepository gitProviderRepository) {
        this.gitProviderRepository = gitProviderRepository;
    }

    private static final String OIDC = "OIDC";

    @Override
    @Transactional
    public long resolveProviderId(String registrationId) {
        return resolve(registrationId).getId();
    }

    @Override
    @Transactional(readOnly = true)
    public String providerTypeName(Long gitProviderId) {
        if (gitProviderId == null) {
            return OIDC;
        }
        return gitProviderRepository
            .findById(gitProviderId)
            .map(p -> p.getType().name())
            .orElse(OIDC);
    }

    /**
     * Resolve (and create on first sight) the {@link GitProvider} row for an OAuth client
     * registration id. {@code registrationId} comes from Spring's
     * {@code OAuth2AuthenticationToken.getAuthorizedClientRegistrationId()}.
     */
    @Transactional
    public GitProvider resolve(String registrationId) {
        return switch (registrationId) {
            case "github" -> upsert(GitProviderType.GITHUB, GITHUB_COM);
            case "gitlab-lrz" -> upsert(GitProviderType.GITLAB, GITLAB_LRZ);
            default -> resolveWorkspaceScoped(registrationId);
        };
    }

    /**
     * Workspace-scoped registrations follow the form {@code gh-ws-{connectionId}} or
     * {@code gl-ws-{connectionId}}. Until the composite repository lands, this throws —
     * the AuthBeginController rejects unknown registrationIds before they reach this
     * resolver, so the path is unreachable in v1.
     */
    private GitProvider resolveWorkspaceScoped(String registrationId) {
        throw new IllegalStateException(
            "workspace-scoped OIDC registrations are not yet wired (registrationId=" + registrationId + ")"
        );
    }

    private GitProvider upsert(GitProviderType type, String serverUrl) {
        return gitProviderRepository
            .findByTypeAndServerUrl(type, serverUrl)
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(type, serverUrl)));
    }
}
