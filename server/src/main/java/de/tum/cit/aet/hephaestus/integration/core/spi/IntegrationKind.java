package de.tum.cit.aet.hephaestus.integration.core.spi;

/**
 * Identifies a specific external system Hephaestus integrates with.
 *
 * <p>Each value belongs to exactly one {@link IntegrationFamily}. The family
 * narrowing lets cross-cutting code dispatch by family while vendor-specific code
 * dispatches by kind.
 *
 * <p>This is the cross-module enum. Vendor-private code may use further narrowing
 * (e.g. {@code GitProviderType.from(kind)} for SCM-only paths) but the dependency
 * direction is one-way: the SPI does not know about vendor-private types.
 */
public enum IntegrationKind {
    /** GitHub.com or GitHub Enterprise Server (host disambiguates). */
    GITHUB(IntegrationFamily.SCM),
    /** GitLab.com or self-hosted GitLab (host disambiguates). */
    GITLAB(IntegrationFamily.SCM),
    /** Slack workspace. */
    SLACK(IntegrationFamily.MESSAGING),
    /** Outline (getoutline.com) knowledge base. */
    OUTLINE(IntegrationFamily.KNOWLEDGE),
    /**
     * Workspace-scoped GitHub OAuth login app — surfaces a "Sign in with this workspace's
     * GitHub" provider in addition to the env-default {@code github} registration. Distinct
     * from {@link #GITHUB} which represents SCM sync.
     */
    OIDC_LOGIN_GITHUB(IntegrationFamily.IDENTITY),
    /**
     * Workspace-scoped GitLab OAuth login app (self-hosted instances; e.g. gitlab.lrz.de
     * is the env default but other workspaces can register their own).
     */
    OIDC_LOGIN_GITLAB(IntegrationFamily.IDENTITY);

    private final IntegrationFamily family;

    IntegrationKind(IntegrationFamily family) {
        this.family = family;
    }

    public IntegrationFamily family() {
        return family;
    }
}
