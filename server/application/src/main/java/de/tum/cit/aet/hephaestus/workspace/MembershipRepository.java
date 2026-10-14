package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.workspace.Membership.WorkspaceRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@WorkspaceAgnostic("Queried by explicit workspace ID - membership queries always workspace-scoped")
public interface MembershipRepository extends JpaRepository<Membership, Membership.Id> {
    List<Membership> findByWorkspace_Id(Long workspaceId);

    @Query("""
            SELECT wm FROM Membership wm
            WHERE wm.workspace.id = :workspaceId
        """)
    Page<Membership> findAllByWorkspace_Id(@Param("workspaceId") Long workspaceId, Pageable pageable);

    @Query("""
            SELECT wm FROM Membership wm
            WHERE wm.workspace.id = :workspaceId AND wm.id.accountId = :accountId
        """)
    Optional<Membership> findByWorkspace_IdAndAccount_Id(
            @Param("workspaceId") Long workspaceId, @Param("accountId") Long accountId);

    @Query("SELECT m FROM Membership m WHERE m.id.accountId = :accountId")
    List<Membership> findByAccount_Id(@Param("accountId") Long accountId);

    /** Account memberships are independent of provider mirror retention. */
    @Query("SELECT m FROM Membership m WHERE m.id.accountId IN :accountIds")
    List<Membership> findByAccount_IdIn(@Param("accountIds") Collection<Long> accountIds);

    @Query("SELECT m FROM Membership m WHERE m.workspace.id = :workspaceId AND m.id.accountId IN" + " :accountIds")
    List<Membership> findByWorkspace_IdAndAccount_IdIn(
            @Param("workspaceId") Long workspaceId, @Param("accountIds") Collection<Long> accountIds);

    /** Account-owned actor memberships, with workspace data loaded for cross-module projections. */
    @Query("""
            SELECT wm FROM Membership wm
            JOIN FETCH wm.workspace
            WHERE wm.id.accountId IN :accountIds
            ORDER BY wm.workspace.id, wm.id.accountId
        """)
    List<Membership> findAllWithWorkspaceByAccountIdIn(@Param("accountIds") Collection<Long> accountIds);

    @Query("SELECT count(m) FROM Membership m WHERE m.workspace.id = :workspaceId AND m.role ="
            + " :role AND m.leftAt IS NULL")
    long countByWorkspace_IdAndRole(@Param("workspaceId") Long workspaceId, @Param("role") WorkspaceRole role);

    long countByWorkspace_Id(Long workspaceId);

    @Query("SELECT wm.id.accountId FROM Membership wm WHERE wm.workspace.id = :workspaceId AND"
            + " wm.role = :role AND wm.leftAt IS NULL ORDER BY wm.joinedAt, wm.id.accountId")
    List<Long> findAccountIdsByWorkspaceIdAndRole(
            @Param("workspaceId") Long workspaceId, @Param("role") WorkspaceRole role);

    @Query("SELECT wm.id.accountId FROM Membership wm WHERE wm.workspace.id = :workspaceId AND" + " wm.hidden = true")
    Set<Long> findHiddenAccountIdsByWorkspaceId(@Param("workspaceId") Long workspaceId);

    /** Does not replace an existing membership or its role under concurrent insertion. */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO membership (workspace_id, account_id, role, hidden, joined_at)
        VALUES (:workspaceId, :accountId, :role, :hidden, CURRENT_TIMESTAMP)
        ON CONFLICT (workspace_id, account_id) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(
            @Param("workspaceId") Long workspaceId,
            @Param("accountId") Long accountId,
            @Param("role") String role,
            @Param("hidden") boolean hidden);

    @Modifying
    @Transactional
    @Query("DELETE FROM Membership wm WHERE wm.workspace.id = :workspaceId")
    void deleteAllByWorkspaceId(@Param("workspaceId") Long workspaceId);

    @Modifying
    @Query("DELETE FROM Membership m WHERE m.id.accountId = :accountId")
    void deleteByAccountId(@Param("accountId") long accountId);

    @Modifying
    @Query("UPDATE Membership m SET m.reviewSelected = false WHERE m.workspace.id = :workspaceId")
    void clearReviewSelection(@Param("workspaceId") long workspaceId);

    @Modifying
    @Query("UPDATE Membership m SET m.reviewSelected = true WHERE m.workspace.id = :workspaceId"
            + " AND m.id.accountId IN :accountIds AND m.leftAt IS NULL")
    void selectForReview(@Param("workspaceId") long workspaceId, @Param("accountIds") Collection<Long> accountIds);

    @Query("SELECT m.id.accountId FROM Membership m WHERE m.workspace.id = :workspaceId AND"
            + " m.reviewSelected = true AND m.leftAt IS NULL")
    List<Long> findReviewSelectedAccountIds(@Param("workspaceId") long workspaceId);
}
