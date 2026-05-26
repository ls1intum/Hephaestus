package de.tum.cit.aet.hephaestus.integration.spi;

import de.tum.cit.aet.hephaestus.integration.connection.GitProviderType;

/**
 * Identifies a specific external system Hephaestus integrates with.
 *
 * <p>Each value belongs to exactly one {@link IntegrationFamily}. The family
 * narrowing lets cross-cutting code dispatch by family while vendor-specific code
 * dispatches by kind.
 *
 * <p>This is the cross-module enum. Vendor-private code may use further narrowing.
 */
public enum IntegrationKind {
    /** GitHub.com or GitHub Enterprise Server (host disambiguates). */
    GITHUB(IntegrationFamily.SCM),
    /** GitLab.com or self-hosted GitLab (host disambiguates). */
    GITLAB(IntegrationFamily.SCM),
    /** Slack workspace. */
    SLACK(IntegrationFamily.MESSAGING),
    /** Outline (getoutline.com) knowledge base. */
    OUTLINE(IntegrationFamily.KNOWLEDGE);

    private final IntegrationFamily family;

    IntegrationKind(IntegrationFamily family) {
        this.family = family;
    }

    public IntegrationFamily family() {
        return family;
    }

    /**
     * Narrow this kind to a {@link GitProviderType} for legacy SCM callers that still
     * dispatch on the high-level provider identity (live discriminator on the
     * {@code GitProvider} entity, sync orchestrators, DTOs that pre-date the unified
     * {@code IntegrationKind} enum).
     *
     * <p>Throws on non-SCM kinds rather than guessing — callers asking for a
     * provider-type from a Slack/Outline Connection have a bug at the call site, not
     * here.
     *
     * @return GITHUB or GITLAB
     * @throws IllegalStateException if this kind is not an SCM kind
     */
    public GitProviderType toGitProviderType() {
        return switch (this) {
            case GITHUB -> GitProviderType.GITHUB;
            case GITLAB -> GitProviderType.GITLAB;
            case SLACK, OUTLINE -> throw new IllegalStateException(
                "IntegrationKind " + this + " is not an SCM kind and has no GitProviderType"
            );
        };
    }
}
