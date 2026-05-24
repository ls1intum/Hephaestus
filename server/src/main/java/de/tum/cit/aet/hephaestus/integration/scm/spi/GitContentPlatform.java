package de.tum.cit.aet.hephaestus.integration.scm.spi;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;

/**
 * SCM-family-specific clone + token access SPI. Capability-gated on
 * {@code Capability.GIT_CONTENT_ACCESS}. Slack / Outline / Linear etc. do
 * not implement.
 *
 * <p>Replaces the GitLab-branch in today's {@code PullRequestContentProvider} —
 * agent code no longer reaches into {@code GitLabTokenService} or
 * GitHub-specific clone URL construction.
 */
public interface GitContentPlatform {

    IntegrationKind kind();

    /** Resolves an HTTPS clone URL for the repository. */
    String resolveCloneUrl(IntegrationRef ref, String repositoryFullName);

    /** Resolves a usable access token (PAT, installation token, or app password). */
    String resolveAccessToken(IntegrationRef ref);
}
