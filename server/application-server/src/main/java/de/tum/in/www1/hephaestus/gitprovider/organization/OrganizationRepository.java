package de.tum.in.www1.hephaestus.gitprovider.organization;

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
public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findByGithubId(Long githubId);
    Optional<Organization> findByLoginIgnoreCase(String login);
}
