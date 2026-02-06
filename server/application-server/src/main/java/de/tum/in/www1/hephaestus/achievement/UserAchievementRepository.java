package de.tum.in.www1.hephaestus.achievement;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for managing user achievement unlocks.
 *
 * <p>Provides queries for checking existing achievements, retrieving user
 * progress, and supporting achievement leaderboards.
 */
@Repository
public interface UserAchievementRepository extends JpaRepository<UserAchievement, UUID> {
    /**
     * Find all achievements unlocked by a specific user.
     *
     * @param userId the user's ID
     * @return list of achievements ordered by unlock time (newest first)
     */
    @Query(
        """
        SELECT ua
        FROM UserAchievement ua
        WHERE ua.user.id = :userId
        ORDER BY ua.unlockedAt DESC
        """
    )
    List<UserAchievement> findByUserId(@Param("userId") Long userId);

    /**
     * Get the set of achievement IDs already unlocked by a user.
     *
     * <p>Used for efficient checking to avoid re-unlocking achievements.
     *
     * @param userId the user's ID
     * @return set of achievement ID strings
     */
    @Query(
        """
        SELECT ua.achievementId
        FROM UserAchievement ua
        WHERE ua.user.id = :userId
        """
    )
    Set<String> findAchievementIdsByUserId(@Param("userId") Long userId);

    /**
     * Check if a specific achievement is already unlocked for a user.
     *
     * @param userId the user's ID
     * @param achievementId the achievement ID
     * @return true if the achievement is already unlocked
     */
    @Query(
        """
        SELECT COUNT(ua) > 0
        FROM UserAchievement ua
        WHERE ua.user.id = :userId
        AND ua.achievementId = :achievementId
        """
    )
    boolean existsByUserIdAndAchievementId(@Param("userId") Long userId, @Param("achievementId") String achievementId);

    /**
     * Find a specific achievement unlock for a user.
     *
     * @param userId the user's ID
     * @param achievementId the achievement ID
     * @return the achievement if unlocked
     */
    Optional<UserAchievement> findByUserIdAndAchievementId(Long userId, String achievementId);

    /**
     * Count how many users have unlocked a specific achievement.
     *
     * <p>Useful for rarity indicators in UI (e.g., "Only 5% of users have this").
     *
     * @param achievementId the achievement ID
     * @return count of users who have this achievement
     */
    @Query(
        """
        SELECT COUNT(ua)
        FROM UserAchievement ua
        WHERE ua.achievementId = :achievementId
        """
    )
    long countByAchievementId(@Param("achievementId") String achievementId);

    /**
     * Find recent achievement unlocks across all users.
     *
     * <p>Useful for activity feeds showing recent achievements.
     *
     * @param limit maximum number of results
     * @return recent achievements ordered by unlock time (newest first)
     */
    @Query(
        """
        SELECT ua
        FROM UserAchievement ua
        JOIN FETCH ua.user
        ORDER BY ua.unlockedAt DESC
        LIMIT :limit
        """
    )
    List<UserAchievement> findRecentUnlocks(@Param("limit") int limit);

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
}
