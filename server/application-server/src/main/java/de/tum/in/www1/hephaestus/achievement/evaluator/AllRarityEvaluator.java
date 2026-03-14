package de.tum.in.www1.hephaestus.achievement.evaluator;

import de.tum.in.www1.hephaestus.achievement.AchievementRegistry;
import de.tum.in.www1.hephaestus.achievement.UserAchievement;
import de.tum.in.www1.hephaestus.achievement.UserAchievementRepository;
import de.tum.in.www1.hephaestus.achievement.progress.BinaryAchievementProgress;
import de.tum.in.www1.hephaestus.activity.ActivitySavedEvent;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Evaluator for the "all rarity" milestone achievements:
 * <ul>
 *   <li>{@code milestone.all_rare} — all 5 rare main-line achievements unlocked</li>
 *   <li>{@code milestone.all_epic} — all 5 epic main-line achievements unlocked</li>
 *   <li>{@code milestone.all_legendary} — all 5 legendary main-line achievements unlocked</li>
 * </ul>
 *
 * A single evaluator class handles all three achievements by dispatching
 * on {@code userAchievement.getAchievementId()}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AllRarityEvaluator implements AchievementEvaluator {

    private static final Map<String, Set<String>> PREREQUISITES = Map.of(
        "milestone.all_rare",
        Set.of("pr.merged.rare", "commit.rare", "review.rare", "issue.open.rare", "issue.close.rare"),
        "milestone.all_epic",
        Set.of("pr.merged.epic", "commit.epic", "review.epic", "issue.open.epic", "issue.close.epic"),
        "milestone.all_legendary",
        Set.of(
            "pr.merged.legendary",
            "commit.legendary",
            "review.legendary",
            "issue.open.legendary",
            "issue.close.legendary"
        )
    );

    private final UserAchievementRepository userAchievementRepository;
    private final AchievementRegistry achievementRegistry;

    @PostConstruct
    void validatePrerequisites() {
        for (var entry : PREREQUISITES.entrySet()) {
            for (String prereqId : entry.getValue()) {
                try {
                    achievementRegistry.getById(prereqId);
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException(
                        "AllRarityEvaluator prerequisite '" +
                            prereqId +
                            "' for '" +
                            entry.getKey() +
                            "' not found in registry. Did achievements.yml change?",
                        e
                    );
                }
            }
        }
    }

    @Override
    public boolean updateProgress(UserAchievement userAchievement, ActivitySavedEvent event) {
        if (userAchievement.getUnlockedAt() != null) {
            return false;
        }

        Set<String> required = PREREQUISITES.get(userAchievement.getAchievementId());
        if (required == null) {
            log.warn("AllRarityEvaluator invoked for unknown achievement: {}", userAchievement.getAchievementId());
            return false;
        }

        Long userId = userAchievement.getUser().getId();
        var existing = userAchievementRepository.findByUserIdAndAchievementIdIn(userId, required);

        long unlockedCount = existing
            .stream()
            .filter(ua -> ua.getUnlockedAt() != null)
            .count();

        if (unlockedCount == required.size()) {
            userAchievement.setProgressData(new BinaryAchievementProgress(true));
            return true;
        }
        return false;
    }
}
