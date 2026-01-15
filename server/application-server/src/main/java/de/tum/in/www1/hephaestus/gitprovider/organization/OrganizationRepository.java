package de.tum.in.www1.hephaestus.gitprovider.organization;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for GitHub organization entities.
 *
 * <p>Organizations are linked to scopes via consuming modules. Lookups by
 * installation ID or login are used during sync/installation operations to resolve
 * organization identity.
 *
 * <p>Legitimately scope-agnostic: These lookups happen during webhook processing
 * BEFORE scope context is established - the organization lookup is used to
 * DISCOVER which scope the event belongs to.
 */
public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findByGithubId(Long githubId);
    Optional<Organization> findByLoginIgnoreCase(String login);
}
