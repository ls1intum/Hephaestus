package de.tum.in.www1.hephaestus.achievement;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for managing user achievement unlocks.
 *
 * <p>Provides queries for checking existing achievements, retrieving user
 * progress, and supporting achievement leaderboards.
 */
@Repository
@WorkspaceAgnostic("Achievements are per-user lifetime accomplishments, not workspace-scoped")
public interface UserAchievementRepository extends JpaRepository<UserAchievement, UUID> {
    /**
     * Find all achievement progress records for a specific user.
     *
     * <p>Returns both in-progress (unlockedAt is null) and unlocked records.
     * Unlocked achievements appear first, followed by in-progress ones.
     *
     * @param userId the user's ID
     * @return list of all achievement progress records for the user
     */
    @Query(
        """
        SELECT ua
        FROM UserAchievement ua
        WHERE ua.user.id = :userId
        ORDER BY ua.unlockedAt DESC NULLS LAST
        """
    )
    List<UserAchievement> findByUserId(@Param("userId") Long userId);

    /**
     * Find a specific achievement unlock for a user.
     *
     * @param userId the user's ID
     * @param achievementId the achievement ID
     * @return the achievement if unlocked
     */
    Optional<UserAchievement> findByUserIdAndAchievementId(Long userId, String achievementId);

    /**
     * Find all achievements for a user that match any of the given achievement IDs.
     *
     * <p>Used to efficiently get progress for achievements triggered by a specific event type.
     *
     * @param userId the user's ID
     * @param achievementIds the achievement IDs to look for
     * @return list of matching achievements with their current progress
     */
    @Query(
        """
        SELECT ua
        FROM UserAchievement ua
        WHERE ua.user.id = :userId
        AND ua.achievementId IN :achievementIds
        """
    )
    List<UserAchievement> findByUserIdAndAchievementIdIn(
        @Param("userId") Long userId,
        @Param("achievementIds") Set<String> achievementIds
    );

    /**
     * Delete all achievement progress records for a specific user.
     *
     * <p>Used by the recalculation/backfill service to clear state before
     * replaying historical events chronologically.
     *
     * @param userId the user's ID
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM UserAchievement ua WHERE ua.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /**
     * Insert a new user-achievement progress row idempotently.
     *
     * <p>Uses Postgres {@code INSERT ... ON CONFLICT (user_id, achievement_id) DO NOTHING}
     * so that two concurrent AFTER_COMMIT async listeners evaluating the same user +
     * achievement cannot race each other into a unique-constraint violation on
     * {@code uk_user_achievement_user_achievement}.
     *
     * <p>Callers must treat a return value of {@code 0} as "another transaction won
     * the race" and re-fetch via {@link #findByUserIdAndAchievementId(Long, String)}
     * to apply their own progress delta on top of the already-persisted row.
     *
     * <p>This method deliberately bypasses Hibernate's dirty-checking so that the
     * SQL driver never surfaces a {@code DataIntegrityViolationException} that
     * Hibernate would otherwise log at ERROR level before the caller can react.
     *
     * @param id the UUID to assign to the new row
     * @param userId the owning user id
     * @param achievementId the achievement definition id
     * @param progressDataJson progress payload serialized as JSON (goes into {@code jsonb})
     * @param unlockedAt unlock timestamp or {@code null} when still in-progress
     * @return {@code 1} if the row was inserted, {@code 0} if a row for
     *         {@code (user_id, achievement_id)} already existed
     */
    @Modifying
    @Transactional
    @Query(
        value = """
        INSERT INTO user_achievement (id, user_id, achievement_id, progress_data, unlocked_at, version)
        VALUES (
            CAST(:id AS uuid),
            :userId,
            :achievementId,
            CAST(:progressDataJson AS jsonb),
            :unlockedAt,
            0
        )
        ON CONFLICT ON CONSTRAINT uk_user_achievement_user_achievement DO NOTHING
        """,
        nativeQuery = true
    )
    int insertIfAbsent(
        @Param("id") UUID id,
        @Param("userId") Long userId,
        @Param("achievementId") String achievementId,
        @Param("progressDataJson") String progressDataJson,
        @Param("unlockedAt") @Nullable Instant unlockedAt
    );
}
