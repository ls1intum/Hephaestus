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
            SELECT u
            FROM User u
            LEFT JOIN FETCH u.teams
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
            SELECT u
            FROM User u
            JOIN FETCH u.teams
            WHERE u.type = 'USER'
        """
    )
    List<User> findAllHumanInTeams();

    @Query(
        """
            SELECT u
            FROM User u
            JOIN u.teams t
            WHERE t.id = :teamId
            AND u.type = 'USER'
        """
    )
    List<User> findAllByTeamId(@Param("teamId") Long teamId);

    @Query(
        """
            SELECT DISTINCT pr.author
            FROM PullRequest pr
            JOIN Team t ON pr.repository MEMBER OF t.repositories
            WHERE t.id = :teamId
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
        return currentUserLogin.flatMap(this::findByLogin);
    }

    /**
     * @return existing user object by current user login
     */
    default User getCurrentUserElseThrow() {
        return getCurrentUser().orElseThrow(() -> new EntityNotFoundException("User", "current authenticated user"));
    }

    @Query("""
    SELECT u
    FROM User u
    WHERE LOWER(u.login) IN :logins
""")
    List<User> findAllByLoginLowerIn(@Param("logins") Set<String> logins);}
