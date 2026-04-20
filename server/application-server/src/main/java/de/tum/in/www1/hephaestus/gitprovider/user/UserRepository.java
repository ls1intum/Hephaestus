package de.tum.in.www1.hephaestus.gitprovider.user;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
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
            WHERE LOWER(u.login) = LOWER(:login)
            ORDER BY u.id
        """
    )
    List<User> findAllByLogin(@Param("login") String login);

    /**
     * Finds a user by login. If multiple providers have the same login, returns the first match.
     */
    default Optional<User> findByLogin(String login) {
        List<User> users = findAllByLogin(login);
        return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
    }

    @Query(
        """
            SELECT u
            FROM User u
            WHERE LOWER(u.login) = LOWER(:login)
              AND u.provider.id = :providerId
        """
    )
    Optional<User> findByLoginAndProviderId(@Param("login") String login, @Param("providerId") Long providerId);

    @Query(
        """
            SELECT u
            FROM User u
            WHERE LOWER(u.email) = LOWER(:email)
            ORDER BY u.id
        """
    )
    List<User> findAllByEmail(@Param("email") String email);

    /**
     * Finds a user by email. If multiple providers have the same email, returns the first match.
     */
    default Optional<User> findByEmail(String email) {
        List<User> users = findAllByEmail(email);
        return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
    }

    @Query(
        """
            SELECT u
            FROM User u
            WHERE LOWER(u.email) = LOWER(:email)
              AND u.provider.id = :providerId
        """
    )
    Optional<User> findByEmailAndProviderId(@Param("email") String email, @Param("providerId") Long providerId);

    /**
     * Finds all users whose display name matches (case-insensitive) the given value.
     * <p>
     * Used by {@link de.tum.in.www1.hephaestus.gitprovider.commit.CommitAuthorResolver}
     * to resolve {@code firstname.lastname@tum.de} style commit author emails against
     * the GitLab-synced {@code User.name} field ("Firstname Lastname"). The resolver
     * only acts when exactly one candidate is returned so the match stays deterministic.
     */
    @Query(
        """
            SELECT u
            FROM User u
            WHERE LOWER(u.name) = LOWER(:name)
              AND u.provider.id = :providerId
              AND u.type = 'USER'
            ORDER BY u.id
        """
    )
    List<User> findAllByNameAndProviderId(@Param("name") String name, @Param("providerId") Long providerId);

    /**
     * Finds all users whose display name matches (case-insensitive) the given value
     * across every provider. Falls back to this variant when the caller cannot scope
     * the lookup to a specific provider.
     *
     * @see #findAllByNameAndProviderId
     */
    @Query(
        """
            SELECT u
            FROM User u
            WHERE LOWER(u.name) = LOWER(:name)
              AND u.type = 'USER'
            ORDER BY u.id
        """
    )
    List<User> findAllByName(@Param("name") String name);

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

    @Query(
        """
            SELECT u
            FROM User u
            WHERE LOWER(u.login) IN :logins
        """
    )
    List<User> findAllByLoginLowerIn(@Param("logins") Set<String> logins);

    Optional<User> findByNativeIdAndProviderId(Long nativeId, Long providerId);

    /**
     * Find all user rows that share a native id across every git provider of a given type.
     * <p>
     * A single native id may correspond to multiple provider rows when several GitLab
     * instances (e.g. gitlab.com and a self-hosted server) are configured with overlapping
     * numeric ids, so this returns a list rather than an {@link Optional}.
     */
    @Query(
        """
            SELECT u
            FROM User u
            WHERE u.provider.type = :providerType
              AND u.nativeId = :nativeId
            ORDER BY u.id
        """
    )
    List<User> findAllByProviderTypeAndNativeId(
        @Param("providerType") GitProviderType providerType,
        @Param("nativeId") Long nativeId
    );

    /**
     * Try to acquire a transaction-scoped advisory lock on the given login.
     * <p>
     * The lock key is derived from {@code hashtext(providerId || ':' || LOWER(login))}, so only
     * operations on the same provider instance and (case-insensitive) login contend.
     *
     * @return true if the lock was acquired, false if another transaction holds it
     */
    @Query(
        value = "SELECT pg_try_advisory_xact_lock(hashtext(CONCAT(:providerId\\:\\:text, ':', LOWER(:login))))",
        nativeQuery = true
    )
    boolean tryAcquireLoginLock(@Param("login") String login, @Param("providerId") Long providerId);

    /**
     * Acquire a transaction-scoped advisory lock on the given login.
     * <p>
     * Must be called before {@link #freeLoginConflicts} and {@link #upsertUser}
     * to prevent cross-scope race conditions.
     */
    @Query(
        value = "SELECT pg_advisory_xact_lock(hashtext(CONCAT(:providerId\\:\\:text, ':', LOWER(:login))))",
        nativeQuery = true
    )
    void acquireLoginLock(@Param("login") String login, @Param("providerId") Long providerId);

    /**
     * Rename any user that currently holds the target login (other than the given native_id
     * on the same provider) by setting their login to {@code RENAMED_<their_id>}.
     */
    @Modifying
    @Query(
        value = """
        UPDATE "user" SET login = 'RENAMED_' || id
        WHERE LOWER("user".login) = LOWER(:login)
          AND "user".native_id != :nativeId
          AND "user".provider_id = :providerId
        """,
        nativeQuery = true
    )
    void freeLoginConflicts(
        @Param("login") String login,
        @Param("nativeId") Long nativeId,
        @Param("providerId") Long providerId
    );

    /**
     * Insert or update a user via {@code INSERT ... ON CONFLICT (provider_id, native_id) DO UPDATE}.
     * <p>
     * Must be called after {@link #freeLoginConflicts} within the same transaction.
     * <p>
     * The {@code id} column is auto-generated on insert. On conflict, the existing row is updated.
     */
    @Modifying
    @Query(
        value = """
        INSERT INTO "user" (native_id, provider_id, login, name, avatar_url, html_url, type, email, created_at, updated_at)
        VALUES (:nativeId, :providerId, :login, :name, :avatarUrl, :htmlUrl, :type, :email, :createdAt, :updatedAt)
        ON CONFLICT (provider_id, native_id) DO UPDATE SET
            login = EXCLUDED.login,
            name = COALESCE(EXCLUDED.name, "user".name),
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
        @Param("nativeId") Long nativeId,
        @Param("providerId") Long providerId,
        @Param("login") String login,
        @Param("name") String name,
        @Param("avatarUrl") String avatarUrl,
        @Param("htmlUrl") String htmlUrl,
        @Param("type") String type,
        @Param("email") String email,
        @Param("createdAt") Instant createdAt,
        @Param("updatedAt") Instant updatedAt
    );

    /**
     * Backfills the email for a single user only when the current value is NULL.
     * <p>
     * Used by the GitLab commit→MR linker to enrich identities whose primary
     * email was not populated during the user sync (e.g. TUM accounts whose
     * {@code publicEmail} is hidden but whose commit identity reveals an
     * institutional address). Never overwrites an existing email.
     *
     * @return number of rows updated (0 when the user already has an email)
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE \"user\" SET email = :email WHERE id = :userId AND email IS NULL", nativeQuery = true)
    int backfillEmailIfNull(@Param("userId") Long userId, @Param("email") String email);
}
