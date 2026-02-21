package de.tum.in.www1.hephaestus.gitprovider.user;

import de.tum.in.www1.hephaestus.SecurityUtils;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import java.time.Instant;
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
            SELECT u
            FROM User u
            WHERE u.email ILIKE :email
        """
    )
    Optional<User> findByEmail(@Param("email") String email);

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
     * Try to acquire a transaction-scoped advisory lock on the given login.
     * <p>
     * Returns {@code true} if the lock was acquired, {@code false} if it is
     * already held by another transaction. Unlike {@link #acquireLoginLock},
     * this method never blocks and cannot participate in a deadlock cycle.
     * <p>
     * The lock key is derived from {@code hashtext(LOWER(login))}, so only
     * operations on the same (case-insensitive) login contend. The lock is
     * automatically released when the enclosing transaction commits or rolls back.
     *
     * @return true if the lock was acquired, false if another transaction holds it
     */
    @Query(value = "SELECT pg_try_advisory_xact_lock(hashtext(LOWER(:login)))", nativeQuery = true)
    boolean tryAcquireLoginLock(@Param("login") String login);

    /**
     * Acquire a transaction-scoped advisory lock on the given login.
     * <p>
     * The lock key is derived from {@code hashtext(LOWER(login))}, so only
     * operations on the same (case-insensitive) login contend. The lock is
     * automatically released when the enclosing transaction commits or rolls back.
     * <p>
     * <b>Warning:</b> This method blocks until the lock is available and can
     * participate in deadlock cycles when multiple transactions acquire locks
     * in different orders. Prefer {@link #tryAcquireLoginLock} with retry logic
     * to avoid deadlocks.
     * <p>
     * Must be called before {@link #freeLoginConflicts} and {@link #upsertUser}
     * to prevent cross-scope race conditions.
     */
    @Query(value = "SELECT pg_advisory_xact_lock(hashtext(LOWER(:login)))", nativeQuery = true)
    void acquireLoginLock(@Param("login") String login);

    /**
     * Rename any user that currently holds the target login (other than the given id)
     * by setting their login to {@code RENAMED_<their_id>}.
     * <p>
     * This resolves login conflicts before the actual upsert. Must be called
     * after {@link #acquireLoginLock} and before {@link #upsertUser} within the
     * same transaction.
     */
    @Modifying
    @Query(
        value = """
        UPDATE "user" SET login = 'RENAMED_' || id
        WHERE LOWER("user".login) = LOWER(:login) AND "user".id != :id
        """,
        nativeQuery = true
    )
    void freeLoginConflicts(@Param("login") String login, @Param("id") Long id);

    /**
     * Insert or update a user via {@code INSERT ... ON CONFLICT (id) DO UPDATE}.
     * <p>
     * Must be called after {@link #freeLoginConflicts} within the same
     * transaction to avoid unique constraint violations on {@code uk_user_login_lower}.
     * <p>
     * The type field is updated on conflict to correct misclassified users
     * (e.g., bots stored as USER). Optional fields (email, created_at, updated_at)
     * use {@code COALESCE} so null parameters preserve existing database values,
     * allowing webhooks (which lack timestamps) and GraphQL sync (which has full data)
     * to share the same upsert path.
     *
     * @param id the primary key (GitHub database ID)
     * @param login the user login
     * @param name the display name
     * @param avatarUrl the avatar URL
     * @param htmlUrl the HTML URL
     * @param type the user type (USER, BOT, ORGANIZATION)
     * @param email the user email (nullable — null preserves existing value)
     * @param createdAt the user creation timestamp (nullable — null preserves existing value)
     * @param updatedAt the user update timestamp (nullable — null preserves existing value)
     */
    @Modifying
    @Query(
        value = """
        INSERT INTO "user" (id, login, name, avatar_url, html_url, type, email, created_at, updated_at)
        VALUES (:id, :login, :name, :avatarUrl, :htmlUrl, :type, :email, :createdAt, :updatedAt)
        ON CONFLICT (id) DO UPDATE SET
            login = EXCLUDED.login,
            name = EXCLUDED.name,
            avatar_url = EXCLUDED.avatar_url,
            html_url = EXCLUDED.html_url,
            type = EXCLUDED.type,
            email = COALESCE(EXCLUDED.email, "user".email),
            created_at = COALESCE(EXCLUDED.created_at, "user".created_at),
            updated_at = COALESCE(EXCLUDED.updated_at, "user".updated_at)
        """,
        nativeQuery = true
    )
    void upsertUser(
        @Param("id") Long id,
        @Param("login") String login,
        @Param("name") String name,
        @Param("avatarUrl") String avatarUrl,
        @Param("htmlUrl") String htmlUrl,
        @Param("type") String type,
        @Param("email") String email,
        @Param("createdAt") Instant createdAt,
        @Param("updatedAt") Instant updatedAt
    );
}
