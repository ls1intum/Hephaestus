package de.tum.in.www1.hephaestus.gitprovider.issuetype;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@WorkspaceAgnostic("Queried by organization ID or workspaceId - workspace scoping explicit in queries")
public interface IssueTypeRepository extends JpaRepository<IssueType, String> {
    @Query(
        """
        SELECT it
        FROM IssueType it
        WHERE it.organization.id = :organizationId
        AND it.isEnabled = true
        ORDER BY it.name ASC
        """
    )
    List<IssueType> findEnabledByOrganizationId(@Param("organizationId") long organizationId);

    @Query(
        """
        SELECT it
        FROM IssueType it
        WHERE it.organization.workspaceId = :workspaceId
        AND it.isEnabled = true
        ORDER BY it.name ASC
        """
    )
    List<IssueType> findEnabledByWorkspaceId(@Param("workspaceId") Long workspaceId);

    /**
     * Find all issue types for an organization (including disabled).
     * Used for cleanup of deleted issue types during sync.
     */
    @Query(
        """
        SELECT it
        FROM IssueType it
        WHERE it.organization.id = :organizationId
        ORDER BY it.name ASC
        """
    )
    List<IssueType> findAllByOrganizationId(@Param("organizationId") Long organizationId);
}
