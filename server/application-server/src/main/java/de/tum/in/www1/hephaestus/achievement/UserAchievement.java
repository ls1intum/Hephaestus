package de.tum.in.www1.hephaestus.achievement;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tracks achievement unlocks per user.
 *
 * <p>Each record represents a single achievement unlocked by a specific user.
 * The combination of user + achievementId is unique (users can only unlock
 * each achievement once).
 *
 * <h3>Idempotency</h3>
 * <p>The unique constraint on (user_id, achievement_id) ensures that duplicate
 * unlock attempts are safely ignored at the database level.
 *
 * <h3>Achievement Lookup</h3>
 * <p>The {@code achievementId} stores the string ID from {@link AchievementType}.
 * To get the full achievement metadata, use {@link AchievementType#fromId(String)}.
 *
 * @see AchievementType
 * @see AchievementService#checkAndUnlock
 */
@Entity
@Table(
    name = "user_achievement",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_user_achievement_user_achievement",
            columnNames = { "user_id", "achievement_id" }
        ),
    },
    indexes = {
        // User achievements lookup: profile display, achievement progress
        @Index(name = "idx_user_achievement_user", columnList = "user_id"),
        // Achievement holders: leaderboard of users with specific achievement
        @Index(name = "idx_user_achievement_achievement", columnList = "achievement_id"),
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAchievement {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    /**
     * The user who unlocked the achievement.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * The achievement ID (references {@link AchievementType#getId()}).
     *
     * <p>We store the string ID rather than the enum directly because:
     * <ul>
     *   <li>Enum values may be renamed in code without breaking data</li>
     *   <li>Achievement definitions are in code, not database</li>
     *   <li>String matching is simple and explicit</li>
     * </ul>
     */
    @NotNull
    @Column(name = "achievement_id", nullable = false, length = 64)
    private String achievementId;

    /**
     * When the achievement was unlocked.
     */
    @NotNull
    @Column(name = "unlocked_at", nullable = false)
    private Instant unlockedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (unlockedAt == null) {
            unlockedAt = Instant.now();
        }
    }

    /**
     * Get the full achievement type metadata.
     *
     * @return the achievement type, or throws if the ID is unknown
     * @throws IllegalArgumentException if achievementId is not a valid AchievementType
     */
    public AchievementType getAchievementType() {
        return AchievementType.fromId(achievementId);
    }
}
