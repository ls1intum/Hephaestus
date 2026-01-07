package de.tum.in.www1.hephaestus.gitprovider.organization;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for GitHub organization entities.
 *
 * <p>Organizations are linked to workspaces via the Workspace.organization relationship.
 * The workspace owns the relationship to organization, not vice versa. Lookups by
 * installation ID or login are used during sync/installation operations to resolve
 * which workspace an event belongs to (by joining through Workspace).
 *
 * <p>Legitimately workspace-agnostic: These lookups happen during webhook processing
 * BEFORE workspace context is established - the organization lookup is used to
 * DISCOVER which workspace the event belongs to.
 */
@WorkspaceAgnostic("Webhook resolution - lookup to discover workspace context")
public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findByInstallationId(Long installationId);
    Optional<Organization> findByGithubId(Long installationId);
    Optional<Organization> findByLoginIgnoreCase(String login);
}
