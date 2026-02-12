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
import org.springframework.lang.Nullable;

/**
 * Tracks achievement progress and unlocks per user.
 *
 * <p>Each record represents a user's progress toward a specific achievement.
 * The combination of user + achievementId is unique (one progress record per
 * user per achievement).
 *
 * <h3>Lifecycle</h3>
 * <p>A record is created when a user first triggers a relevant event for an
 * achievement (with {@code currentValue = 1} and {@code unlockedAt = null}).
 * The {@code currentValue} is incremented on each subsequent qualifying event.
 * When the threshold is met, {@code unlockedAt} is set to the current timestamp.
 *
 * <h3>Idempotency</h3>
 * <p>The unique constraint on (user_id, achievement_id) ensures that duplicate
 * progress records cannot be created at the database level.
 *
 * <h3>Achievement Lookup</h3>
 * <p>The {@code achievementId} stores the string ID from {@link AchievementDefinition}.
 * To get the full achievement metadata, use {@link AchievementDefinition#fromId(String)}.
 *
 * @see AchievementDefinition
 * @see AchievementService#checkAndUnlock
 */
@Entity
@Table(
    name = "user_achievement",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_achievement_user_achievement", columnNames = { "user_id", "achievement_id" }),
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
     * The achievement ID (references {@link AchievementDefinition#getId()}).
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
     * When the achievement was unlocked. {@code null} for achievements that are
     * in progress but not yet completed.
     */
    @Nullable
    @Column(name = "unlocked_at")
    private Instant unlockedAt;

    /**
     * Current progress value for this achievement.
     *
     * <p>This field stores the incremental count of qualifying events,
     * avoiding expensive COUNT(*) queries on the ActivityEvent table.
     * When an activity event is recorded, relevant achievement counters
     * are incremented rather than recounted from scratch.
     *
     * <p>For unlocked achievements, this represents the count at unlock time.
     * For in-progress achievements, this is the current progress toward the goal.
     */
    @Column(name = "current_value", nullable = false)
    @Builder.Default
    private int currentValue = 0;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    /**
     * Get the full achievement definition metadata.
     *
     * @return the achievement definition, or throws if the ID is unknown
     * @throws IllegalArgumentException if achievementId is not a valid AchievementDefinition
     */
    public AchievementDefinition getAchievementDefinition() {
        return AchievementDefinition.fromId(achievementId);
    }
}
