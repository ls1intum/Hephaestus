package de.tum.cit.aet.hephaestus.core.auth.spi;

/**
 * Narrow SPI that resolves an OAuth client {@code registrationId} to the persistent
 * {@code git_provider} row id, creating the row on first sight. Implemented in
 * {@code integration} (which owns the {@code GitProvider} aggregate); consumed by
 * {@code core.auth} during JIT account provisioning so the {@code IdentityLink} can be
 * keyed by {@code (git_provider_id, subject)} without {@code core.auth} importing the
 * integration entity (which would invert the bounded-context dependency direction).
 *
 * <p>This is the auth-side counterpart of the
 * {@code de.tum.cit.aet.hephaestus.integration.identity.connect} OIDC-login adapters: auth
 * holds only the scalar {@code git_provider_id}; integration owns the {@code GitProvider}
 * row and the {@code registrationId → (type, server_url)} mapping.
 */
public interface GitProviderRegistry {
    /**
     * Resolve (and create on first sight) the {@code git_provider} row id for an OAuth
     * client registration id. {@code registrationId} comes from Spring's
     * {@code OAuth2AuthenticationToken.getAuthorizedClientRegistrationId()}.
     *
     * @param registrationId the Spring Security client registration id (e.g. {@code github},
     *                        {@code gitlab-lrz}, or a workspace-scoped {@code gh-ws-{id}} /
     *                        {@code gl-ws-{id}})
     * @return the persistent {@code git_provider} row id
     */
    long resolveProviderId(String registrationId);

    /**
     * Resolve the provider <em>type</em> name (e.g. {@code GITHUB}, {@code GITLAB}) for a
     * {@code git_provider} row id. Used by read-side auth surfaces (profile, GDPR export)
     * to label an {@link de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLink} without
     * navigating the integration-owned {@code GitProvider} entity.
     *
     * @param gitProviderId the {@code git_provider} row id, or {@code null}
     * @return the provider type name, or {@code "OIDC"} when the id is {@code null} or no row
     *         exists (matches the prior null-provider fallback)
     */
    String providerTypeName(Long gitProviderId);
}
