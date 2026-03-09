package de.tum.in.www1.hephaestus.gitprovider.team;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for team entities.
 *
 * <p>Teams are scoped through their organization field which carries scope
 * through the Team.organization relationship.
 */
@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {
    Optional<Team> findByNativeIdAndProviderId(Long nativeId, Long providerId);

    List<Team> findAllByName(String name);

    List<Team> findAllByOrganizationIgnoreCase(String organization);

    /**
     * Find a team by its natural key (organization + name).
     *
     * @param organization the organization login (case-insensitive)
     * @param name the team name
     * @return the team if found
     */
    Optional<Team> findByOrganizationIgnoreCaseAndName(String organization, String name);

    /**
     * Find a team by organization and slug (the provider-agnostic natural key).
     * <p>
     * For GitHub: slug = team slug. For GitLab: slug = relative path from root group.
     * <p>
     * WARNING: Does not scope by provider. Prefer
     * {@link #findByOrganizationIgnoreCaseAndSlugAndProviderId} when provider is known.
     *
     * @param organization the organization login (case-insensitive)
     * @param slug the team slug
     * @return the team if found
     */
    Optional<Team> findByOrganizationIgnoreCaseAndSlug(String organization, String slug);

    /**
     * Find a team by organization, slug, and provider (fully-scoped natural key).
     *
     * @param organization the organization login (case-insensitive)
     * @param slug the team slug
     * @param providerId the git provider ID
     * @return the team if found
     */
    Optional<Team> findByOrganizationIgnoreCaseAndSlugAndProviderId(String organization, String slug, Long providerId);

    /**
     * Fetch teams with collections eagerly loaded for DTO conversion.
     * Uses EntityGraph to fetch repoPermissions (with nested repository),
     * and memberships (with users).
     *
     * <p>Note: Repository labels are NOT included in EntityGraph to avoid
     * Cartesian product explosion. They are batch-fetched via @BatchSize(50)
     * on Repository.labels when accessed.
     *
     * <p>Note: Team labels are managed via scope-specific settings
     * (via consuming module) and are fetched separately.
     */
    @EntityGraph(
        attributePaths = { "repoPermissions", "repoPermissions.repository", "memberships", "memberships.user" }
    )
    List<Team> findWithCollectionsByOrganizationIgnoreCase(String organization);

    /**
     * Fetch a single team by ID with collections eagerly loaded for DTO conversion.
     *
     * <p>Note: Repository labels are NOT included in EntityGraph to avoid
     * Cartesian product explosion. They are batch-fetched via @BatchSize(50)
     * on Repository.labels when accessed.
     *
     * <p>Note: Team labels are managed via scope-specific settings
     * (via consuming module) and are fetched separately.
     */
    @EntityGraph(
        attributePaths = { "repoPermissions", "repoPermissions.repository", "memberships", "memberships.user" }
    )
    Optional<Team> findWithCollectionsById(Long id);
}
