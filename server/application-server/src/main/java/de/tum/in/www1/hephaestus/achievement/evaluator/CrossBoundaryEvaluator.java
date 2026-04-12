package de.tum.in.www1.hephaestus.achievement.evaluator;

import de.tum.in.www1.hephaestus.achievement.UserAchievement;
import de.tum.in.www1.hephaestus.achievement.progress.BinaryAchievementProgress;
import de.tum.in.www1.hephaestus.activity.ActivitySavedEvent;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.LanguageExtensions;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Evaluator for the "Cross-Boundary Dev" achievement:
 * a single commit that touches files in 2 or more programming languages.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrossBoundaryEvaluator implements AchievementEvaluator {

    private static final int MIN_LANGUAGES = 2;

    private final CommitRepository commitRepository;

    @Override
    public boolean updateProgress(UserAchievement userAchievement, ActivitySavedEvent event) {
        if (userAchievement.getUnlockedAt() != null) {
            return false;
        }

        return commitRepository
            .findByIdWithFileChanges(event.targetId())
            .map(commit -> {
                Set<String> languages = commit
                    .getFileChanges()
                    .stream()
                    .map(fc -> LanguageExtensions.detectLanguage(fc.getFilename()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

                if (languages.size() >= MIN_LANGUAGES) {
                    userAchievement.setProgressData(new BinaryAchievementProgress(true));
                    return true;
                }
                return false;
            })
            .orElse(false);
    }
}
