package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.Optional;

/**
 * Reports which SCM/messaging integrations are reachable for workspace-creation flows.
 *
 * <p>Replaces direct reads of vendor-specific {@code GitHubProperties} / {@code GitLabProperties}
 * by non-integration code (workspace UI flags, feature-flagged provider lists). The vendor
 * adapter that owns the credential bean implements this port; the workspace registry consumes
 * an aggregated {@code Map<IntegrationKind, WorkspaceProviderAvailability>} so it never has to
 * branch on a specific kind.
 *
 * <p>One impl per kind. Returning {@link Optional#empty()} means "this provider is not
 * available for workspace creation right now" — either disabled in config, missing
 * credentials, or feature-flagged off.
 */
public interface WorkspaceProviderAvailability {
    /** The integration kind this availability source describes. */
    IntegrationKind kind();

    /**
     * Returns the connection hint exposed to the workspace creation wizard, or empty if
     * the provider cannot be used for new workspaces.
     *
     * <p>The {@code hintUrl} carries vendor semantics:
     * <ul>
     *   <li>GitHub App: the installation URL ({@code https://github.com/apps/.../installations/new})</li>
     *   <li>GitLab PAT: the default server URL ({@code https://gitlab.com})</li>
     * </ul>
     * Callers must not parse the URL — it is consumed verbatim by the wizard.
     */
    Optional<ConnectionHint> connectionHint();

    /** Provider-creation hint for the workspace wizard. */
    record ConnectionHint(String hintUrl) {}
}
