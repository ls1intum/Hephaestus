package de.tum.in.www1.hephaestus.gitprovider.organization;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for GitHub organization entities.
 *
 * <p>Workspace-agnostic: Organizations are linked to workspaces via the Workspace.organization
 * relationship. The workspace owns the relationship to organization, not vice versa.
 * Lookups by installation ID or login are used during sync/installation operations
 * to resolve which workspace an event belongs to (by joining through Workspace).
 */
@WorkspaceAgnostic("Organizations linked to workspaces via Workspace.organization - workspace owns the relationship")
public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findByInstallationId(Long installationId);
    Optional<Organization> findByGithubId(Long installationId);
    Optional<Organization> findByLoginIgnoreCase(String login);
}
