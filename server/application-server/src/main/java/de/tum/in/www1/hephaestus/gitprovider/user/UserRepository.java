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
import org.springframework.transaction.annotation.Transactional;

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
     * <p>
     * <b>Note:</b> This method can cause deadlocks when concurrent transactions
     * try to upsert different users in different orders. For deadlock-free
     * concurrent inserts, prefer {@link #insertIgnore} combined with a
     * subsequent update via JPA.
     *
     * @param id the primary key (GitHub database ID)
     * @param login the user login
     * @param name the display name
     * @param avatarUrl the avatar URL
     * @param htmlUrl the HTML URL
     * @param type the user type (USER, BOT, ORGANIZATION)
     */
    @Modifying
    @Transactional
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

    /**
     * Insert a user, ignoring conflicts on ID (ON CONFLICT DO NOTHING).
     * <p>
     * This is the preferred method for concurrent inserts because it avoids
     * deadlocks that can occur with ON CONFLICT DO UPDATE. When multiple
     * transactions try to insert the same user:
     * <ul>
     *   <li>One transaction succeeds with the insert</li>
     *   <li>Other transactions skip (DO NOTHING) without blocking</li>
     * </ul>
     * <p>
     * After calling this method, fetch the user and update via JPA if needed.
     * This pattern separates insert (no locks) from update (row-level lock on
     * specific row), avoiding the lock escalation that causes deadlocks.
     * <p>
     * <b>Note:</b> This method only handles conflicts on the ID column. If a
     * conflict occurs on the login column (unique constraint uk_user_login),
     * a DataIntegrityViolationException will be thrown. The caller should catch
     * this and handle the login conflict (e.g., by updating the existing user's
     * login first to free up the username).
     *
     * @param id the primary key (GitHub database ID)
     * @param login the user login
     * @param name the display name
     * @param avatarUrl the avatar URL
     * @param htmlUrl the HTML URL
     * @param type the user type (USER, BOT, ORGANIZATION)
     * @throws org.springframework.dao.DataIntegrityViolationException if login conflicts with existing user
     */
    @Modifying
    @Transactional
    @Query(
        value = """
        INSERT INTO "user" (id, login, name, avatar_url, html_url, type)
        VALUES (:id, :login, :name, :avatarUrl, :htmlUrl, :type)
        ON CONFLICT (id) DO NOTHING
        """,
        nativeQuery = true
    )
    void insertIgnore(
        @Param("id") Long id,
        @Param("login") String login,
        @Param("name") String name,
        @Param("avatarUrl") String avatarUrl,
        @Param("htmlUrl") String htmlUrl,
        @Param("type") String type
    );

    /**
     * Update a user's login by their ID.
     * <p>
     * This is used when a GitHub user renames their account. The login field
     * has a unique constraint, so when processing a new user who has taken
     * an old username, we first need to update the old user's login.
     *
     * @param id the user's ID
     * @param newLogin the new login to set (can be a placeholder like "RENAMED_123")
     */
    @Modifying
    @Transactional
    @Query(
        value = """
        UPDATE "user" SET login = :newLogin WHERE id = :id
        """,
        nativeQuery = true
    )
    void updateLogin(@Param("id") Long id, @Param("newLogin") String newLogin);

    /**
     * Atomically free up a login by renaming any user who currently holds it,
     * excluding the specified user.
     * <p>
     * This is used during user updates when the target login is already taken by
     * a different user row (e.g., GitHub bot accounts like "Copilot" that appear
     * with different internal IDs, or users who renamed their accounts).
     * <p>
     * The conflicting user's login is changed to "RENAMED_&lt;their_id&gt;" to free
     * the login for the target user. Uses a native query to avoid loading the
     * conflicting entity into the Hibernate session (which could cause stale-state
     * issues during flush).
     *
     * @param login the login to free up
     * @param excludeId the user ID that should keep the login (excluded from rename)
     * @return the number of rows updated (0 or 1)
     */
    @Modifying(flushAutomatically = true)
    @Transactional
    @Query(
        value = """
        UPDATE "user" SET login = 'RENAMED_' || id
        WHERE LOWER(login) = LOWER(:login) AND id != :excludeId
        """,
        nativeQuery = true
    )
    int freeLogin(@Param("login") String login, @Param("excludeId") Long excludeId);
}
