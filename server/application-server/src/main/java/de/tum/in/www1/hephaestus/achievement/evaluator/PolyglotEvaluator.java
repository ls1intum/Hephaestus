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
 * Evaluator for the "Polyglot" achievement:
 * commit code in 3 or more distinct programming languages.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolyglotEvaluator implements AchievementEvaluator {

    private static final int MIN_LANGUAGES = 3;

    private final CommitRepository commitRepository;

    @Override
    public boolean updateProgress(UserAchievement userAchievement, ActivitySavedEvent event) {
        if (userAchievement.getUnlockedAt() != null) {
            return false;
        }

        Long authorId = userAchievement.getUser().getId();
        var extensions = commitRepository.findDistinctFileExtensionsByAuthorId(authorId, event.occurredAt());

        Set<String> languages = extensions
            .stream()
            .filter(Objects::nonNull)
            .map(ext -> LanguageExtensions.EXTENSION_TO_LANGUAGE.get(ext.toLowerCase()))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        if (languages.size() >= MIN_LANGUAGES) {
            userAchievement.setProgressData(new BinaryAchievementProgress(true));
            return true;
        }
        return false;
    }
}
