package de.tum.in.www1.hephaestus.gitprovider.user;

import de.tum.in.www1.hephaestus.SecurityUtils;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    @Query(
        """
            SELECT u
            FROM User u
            WHERE u.login ILIKE :login
        """
    )
    Optional<User> findByLogin(@Param("login") String login);

    @Query(
        """
            SELECT u
            FROM User u
            LEFT JOIN FETCH u.mergedPullRequests
            WHERE u.login ILIKE :login
        """
    )
    Optional<User> findByLoginWithEagerMergedPullRequests(@Param("login") String login);

    @Query(
        """
            SELECT DISTINCT u
            FROM User u
            LEFT JOIN FETCH u.teamMemberships m
            LEFT JOIN FETCH m.team t
            WHERE u.type = 'USER'
        """
    )
    List<User> findAllWithEagerTeams();

    @Query(
        """
            SELECT u
            FROM User u
            WHERE u.type = 'USER'
        """
    )
    List<User> findAllHuman();

    @Query(
        """
            SELECT DISTINCT u
            FROM User u
            JOIN FETCH u.teamMemberships m
            JOIN FETCH m.team t
            WHERE u.type = 'USER'
        """
    )
    List<User> findAllHumanInTeams();

    @Query(
        """
            SELECT DISTINCT u
            FROM User u
            JOIN u.teamMemberships m
            JOIN m.team t
            WHERE t.id = :teamId
            AND u.type = 'USER'
        """
    )
    List<User> findAllByTeamId(@Param("teamId") Long teamId);

    @Query(
        """
            SELECT DISTINCT pr.author
            FROM PullRequest pr
            JOIN TeamRepositoryPermission trp ON trp.repository = pr.repository
            JOIN Team t ON trp.team = t
            WHERE t.id = :teamId
            AND trp.hiddenFromContributions = false
            AND (
                NOT EXISTS (SELECT l
                    FROM t.labels l
                    WHERE l.repository = pr.repository
                )
                OR
                EXISTS (
                    SELECT l
                    FROM t.labels l
                    WHERE l.repository = pr.repository
                    AND l MEMBER OF pr.labels
                )
            )
        """
    )
    Set<User> findAllContributingToTeam(@Param("teamId") Long teamId);

    /**
     * @return existing user object by current user login
     */
    default Optional<User> getCurrentUser() {
        var currentUserLogin = SecurityUtils.getCurrentUserLogin();
        return currentUserLogin.map(this::findByLogin).orElse(Optional.empty());
    }

    /**
     * @return existing user object by current user login
     */
    default User getCurrentUserElseThrow() {
        return getCurrentUser().orElseThrow(() -> new EntityNotFoundException("User", "current authenticated user"));
    }
}
