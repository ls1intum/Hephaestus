package de.tum.cit.aet.hephaestus.integration.scm.workspace;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.workspace.Membership;
import de.tum.cit.aet.hephaestus.workspace.MembershipSearch;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

@WorkspaceAgnostic("SCM activity actors are selected through explicit account membership and exact identity")
public interface ScmMemberRepository extends Repository<User, Long>, MembershipSearch {
    @Override
    @Query("""
        SELECT m FROM Membership m JOIN Account a ON a.id=m.id.accountId
        LEFT JOIN User u ON u.provider.id=:providerId AND u.type='USER' AND EXISTS (
          SELECT i.id FROM Identity i WHERE i.account.id=m.id.accountId AND i.disabledAt IS NULL
            AND i.teamKey='' AND i.providerId=u.provider.id AND i.providerUserKey=CAST(u.nativeId AS string))
        WHERE m.workspace.id=:workspaceId
          AND (CAST(:query AS string) IS NULL OR LOCATE(LOWER(CAST(:query AS string)),LOWER(a.displayName))>0
            OR LOCATE(LOWER(CAST(:query AS string)),LOWER(u.login))>0
            OR LOCATE(LOWER(CAST(:query AS string)),LOWER(u.name))>0)
          AND (:teamId IS NULL OR EXISTS (SELECT tm.id FROM TeamMembership tm
            WHERE tm.user.id=u.id AND tm.team.id=:teamId AND tm.team.provider.id=:providerId
              AND LOWER(tm.team.organization)=LOWER(m.workspace.accountLogin)))
        """)
    Page<Membership> findMembers(
            @Param("workspaceId") long workspaceId,
            @Param("providerId") @Nullable Long providerId,
            @Param("query") @Nullable String query,
            @Param("teamId") @Nullable Long teamId,
            Pageable pageable);

    @Query("""
        SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.teamMemberships tm LEFT JOIN FETCH tm.team
        WHERE u.type = 'USER' AND EXISTS (
            SELECT m.id.accountId FROM Membership m, Identity i WHERE m.workspace.id = :workspaceId
            AND m.leftAt IS NULL AND i.account.id = m.id.accountId AND i.disabledAt IS NULL
            AND i.teamKey = '' AND i.providerId = u.provider.id
            AND i.providerUserKey = CAST(u.nativeId AS string))
        """)
    List<User> findHumanUsersWithTeamsByWorkspaceId(@Param("workspaceId") Long workspaceId);

    @Query("""
        SELECT u.id FROM User u WHERE EXISTS (
            SELECT m.id.accountId FROM Membership m, Identity i WHERE m.workspace.id = :workspaceId
            AND m.hidden = true AND m.leftAt IS NULL AND i.account.id = m.id.accountId
            AND i.disabledAt IS NULL AND i.teamKey = '' AND i.providerId = u.provider.id
            AND i.providerUserKey = CAST(u.nativeId AS string))
        """)
    Set<Long> findHiddenUserIdsByWorkspaceId(@Param("workspaceId") Long workspaceId);

    @Query("""
        SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.teamMemberships tm LEFT JOIN FETCH tm.team
        WHERE u.type = 'USER' AND u.provider.id = :providerId AND LOWER(u.login) = LOWER(:login)
          AND EXISTS (
            SELECT m.id.accountId FROM Membership m, Identity i WHERE m.workspace.id = :workspaceId
            AND m.leftAt IS NULL AND i.account.id = m.id.accountId AND i.disabledAt IS NULL
            AND i.teamKey = '' AND i.providerId = u.provider.id
            AND i.providerUserKey = CAST(u.nativeId AS string))
        """)
    Optional<User> findMemberByLogin(
            @Param("workspaceId") long workspaceId, @Param("login") String login, @Param("providerId") long providerId);
}
