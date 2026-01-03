package de.tum.in.www1.hephaestus.gitprovider.organization;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for GitHub organization entities.
 *
 * <p>Workspace-agnostic: Organizations contain the {@code workspaceId} field directly,
 * making them the root of workspace scope. Lookups by installation ID or login are
 * used during sync/installation operations to resolve which workspace an event belongs to.
 */
@WorkspaceAgnostic("Organizations contain workspaceId directly - they ARE the workspace scope root")
public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findByInstallationId(Long installationId);
    Optional<Organization> findByGithubId(Long installationId);
    Optional<Organization> findByLoginIgnoreCase(String login);
}
