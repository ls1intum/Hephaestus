package de.tum.in.www1.hephaestus.achievement;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
}
