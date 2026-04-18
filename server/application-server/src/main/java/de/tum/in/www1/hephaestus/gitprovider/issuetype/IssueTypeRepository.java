package de.tum.in.www1.hephaestus.gitprovider.issuetype;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
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

    /**
     * Find an enabled issue type by organization and name (case-insensitive).
     * <p>
     * Used by GitLab issue sync to resolve {@code issue.issue_type_id} from the
     * GraphQL {@code Issue.type} enum ({@code ISSUE}, {@code TASK}, {@code INCIDENT}, …).
     */
    @Query(
        """
        SELECT it
        FROM IssueType it
        WHERE it.organization.id = :organizationId
        AND lower(it.name) = lower(:name)
        AND it.isEnabled = true
        """
    )
    java.util.Optional<IssueType> findByOrganizationIdAndNameIgnoreCase(
        @Param("organizationId") Long organizationId,
        @Param("name") String name
    );
}
