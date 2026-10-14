package de.tum.cit.aet.hephaestus.integration.scm.workspace;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

@WorkspaceAgnostic("SCM activity actors are selected through explicit account membership and exact identity")
public interface ScmMemberRepository extends Repository<User, Long> {
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

    default Optional<User> findMemberByLogin(long workspaceId, String login) {
        return findHumanUsersWithTeamsByWorkspaceId(workspaceId).stream()
                .filter(actor -> actor.getLogin().equalsIgnoreCase(login))
                .findFirst();
    }
}
