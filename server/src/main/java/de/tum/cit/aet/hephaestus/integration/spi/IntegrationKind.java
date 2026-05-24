package de.tum.cit.aet.hephaestus.integration.spi;

/**
 * Identifies a specific external system Hephaestus integrates with.
 *
 * <p>Each value belongs to exactly one {@link IntegrationFamily.Family}. The family
 * narrowing lets cross-cutting code dispatch by family while vendor-specific code
 * dispatches by kind.
 *
 * <p>This is the cross-module enum. Vendor-private code may use further narrowing.
 */
public enum IntegrationKind {
    /** GitHub.com or GitHub Enterprise Server (host disambiguates). */
    GITHUB(IntegrationFamily.Family.SCM),
    /** GitLab.com or self-hosted GitLab (host disambiguates). */
    GITLAB(IntegrationFamily.Family.SCM),
    /** Slack workspace. */
    SLACK(IntegrationFamily.Family.MESSAGING),
    /** Outline (getoutline.com) knowledge base. */
    OUTLINE(IntegrationFamily.Family.KNOWLEDGE);

    private final IntegrationFamily.Family family;

    IntegrationKind(IntegrationFamily.Family family) {
        this.family = family;
    }

    public IntegrationFamily.Family family() {
        return family;
    }
}
