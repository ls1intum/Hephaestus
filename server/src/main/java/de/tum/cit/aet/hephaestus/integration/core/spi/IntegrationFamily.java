package de.tum.cit.aet.hephaestus.integration.core.spi;

/** Family the integration belongs to. Used to gate practice availability per workspace. */
public enum IntegrationFamily {
    SCM,
    MESSAGING,
    KNOWLEDGE,
    /**
     * Identity provider — workspace-scoped OIDC login providers (self-hosted GitLab, GHE).
     * Members of this family DO NOT participate in sync; they back the
     * {@code core.auth} module's composite ClientRegistrationRepository.
     */
    IDENTITY,
}
