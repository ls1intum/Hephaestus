package de.tum.in.www1.hephaestus.gitprovider.user;

import de.tum.in.www1.hephaestus.SecurityUtils;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
            SELECT DISTINCT u
            FROM User u
            LEFT JOIN FETCH u.mergedPullRequests mpr
            WHERE LOWER(u.login) = LOWER(:login)
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
            JOIN FETCH u.teamMemberships m
            JOIN FETCH m.team t
            WHERE u.type = 'USER'
            AND LOWER(t.organization) = LOWER(:organization)
        """
    )
    List<User> findAllHumanInTeamsOfOrganization(@Param("organization") String organization);

    @Query(
        """
            SELECT DISTINCT u
            FROM User u
            JOIN u.teamMemberships m
            JOIN m.team t
            WHERE t.id IN :teamIds
            AND u.type = 'USER'
        """
    )
    List<User> findAllByTeamIds(@Param("teamIds") Collection<Long> teamIds);

    default List<User> findAllByTeamId(Long teamId) {
        if (teamId == null) {
            return List.of();
        }
        return findAllByTeamIds(List.of(teamId));
    }

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

    @Query(
        """
            SELECT u
            FROM User u
            WHERE LOWER(u.login) IN :logins
        """
    )
    List<User> findAllByLoginLowerIn(@Param("logins") Set<String> logins);

    /**
     * Upsert a user using PostgreSQL ON CONFLICT.
     * This is thread-safe for concurrent inserts of the same user.
     * <p>
     * The type field is also updated on conflict to ensure that misclassified
     * users (e.g., bots stored as USER) get corrected when seen again with
     * proper type information.
     *
     * @param id the primary key (GitHub database ID)
     * @param login the user login
     * @param name the display name
     * @param avatarUrl the avatar URL
     * @param htmlUrl the HTML URL
     * @param type the user type (USER, BOT, ORGANIZATION)
     */
    @Modifying
    @Query(
        value = """
        INSERT INTO "user" (id, login, name, avatar_url, html_url, type)
        VALUES (:id, :login, :name, :avatarUrl, :htmlUrl, :type)
        ON CONFLICT (id) DO UPDATE SET
            login = EXCLUDED.login,
            name = EXCLUDED.name,
            avatar_url = EXCLUDED.avatar_url,
            html_url = EXCLUDED.html_url,
            type = EXCLUDED.type
        """,
        nativeQuery = true
    )
    void upsert(
        @Param("id") Long id,
        @Param("login") String login,
        @Param("name") String name,
        @Param("avatarUrl") String avatarUrl,
        @Param("htmlUrl") String htmlUrl,
        @Param("type") String type
    );
}
