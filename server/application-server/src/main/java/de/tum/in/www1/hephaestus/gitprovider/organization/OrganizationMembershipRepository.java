package de.tum.in.www1.hephaestus.gitprovider.organization;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for organization membership records.
 *
 * <p>Workspace-agnostic: All queries filter by Organization ID which has
 * workspace context through the Workspace.organization relationship. Used during sync operations.
 */
@WorkspaceAgnostic("Queries filter by Organization ID which has workspace through Workspace.organization")
public interface OrganizationMembershipRepository
    extends JpaRepository<OrganizationMembership, OrganizationMembershipId> {
    @Query("SELECT m.userId FROM OrganizationMembership m WHERE m.organizationId = :orgId")
    List<Long> findUserIdsByOrganizationId(@Param("orgId") Long organizationId);

    @Modifying
    @Query(
        value = """
        INSERT INTO organization_membership (organization_id, user_id, role, joined_at)
        VALUES (:orgId, :userId, :role, now())
        ON CONFLICT (organization_id, user_id)
        DO UPDATE SET role = EXCLUDED.role
        """,
        nativeQuery = true
    )
    void upsertMembership(
        @Param("orgId") Long organizationId,
        @Param("userId") Long userId,
        @Param("role") String role
    );

    @Modifying
    @Query("DELETE FROM OrganizationMembership m WHERE m.organizationId = :orgId AND m.userId IN :userIds")
    void deleteByOrganizationIdAndUserIdIn(
        @Param("orgId") Long organizationId,
        @Param("userIds") Collection<Long> userIds
    );
}
