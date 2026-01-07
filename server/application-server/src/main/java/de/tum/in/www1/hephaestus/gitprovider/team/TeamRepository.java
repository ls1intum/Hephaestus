package de.tum.in.www1.hephaestus.gitprovider.team;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for team entities.
 *
 * <p>Teams are scoped through their organization field which carries workspace context
 * through the Team.organization -> Workspace.organization relationship.
 */
@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {
    List<Team> findAllByName(String name);

    List<Team> findAllByHiddenFalse();

    List<Team> findAllByOrganizationIgnoreCase(String organization);

    /**
     * Fetch teams with all collections eagerly loaded for DTO conversion.
     * Uses EntityGraph to fetch repoPermissions (with nested repository and its labels),
     * team labels, and memberships (with users) in one query.
     */
    @EntityGraph(
        attributePaths = {
            "repoPermissions",
            "repoPermissions.repository",
            "repoPermissions.repository.labels",
            "labels",
            "labels.repository",
            "memberships",
            "memberships.user",
        }
    )
    List<Team> findWithCollectionsByOrganizationIgnoreCase(String organization);

    /**
     * Fetch a single team by ID with all collections eagerly loaded for DTO conversion.
     */
    @EntityGraph(
        attributePaths = {
            "repoPermissions",
            "repoPermissions.repository",
            "repoPermissions.repository.labels",
            "labels",
            "labels.repository",
            "memberships",
            "memberships.user",
        }
    )
    Optional<Team> findWithCollectionsById(Long id);

    List<Team> findAllByOrganizationIgnoreCaseAndHiddenFalse(String organization);
}
