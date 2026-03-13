package de.tum.in.www1.hephaestus.achievement.evaluator;

import de.tum.in.www1.hephaestus.achievement.UserAchievement;
import de.tum.in.www1.hephaestus.achievement.progress.BinaryAchievementProgress;
import de.tum.in.www1.hephaestus.activity.ActivitySavedEvent;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitRepository;
import java.util.Map;
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

    private static final Map<String, String> EXTENSION_TO_LANGUAGE = Map.ofEntries(
        Map.entry("java", "Java"),
        Map.entry("py", "Python"),
        Map.entry("ts", "TypeScript"),
        Map.entry("tsx", "TypeScript"),
        Map.entry("js", "JavaScript"),
        Map.entry("jsx", "JavaScript"),
        Map.entry("go", "Go"),
        Map.entry("rs", "Rust"),
        Map.entry("rb", "Ruby"),
        Map.entry("kt", "Kotlin"),
        Map.entry("kts", "Kotlin"),
        Map.entry("swift", "Swift"),
        Map.entry("c", "C"),
        Map.entry("h", "C"),
        Map.entry("cpp", "C++"),
        Map.entry("hpp", "C++"),
        Map.entry("cc", "C++"),
        Map.entry("cs", "C#"),
        Map.entry("scala", "Scala"),
        Map.entry("php", "PHP"),
        Map.entry("r", "R"),
        Map.entry("sql", "SQL"),
        Map.entry("sh", "Shell"),
        Map.entry("bash", "Shell"),
        Map.entry("zsh", "Shell"),
        Map.entry("yml", "YAML"),
        Map.entry("yaml", "YAML"),
        Map.entry("html", "HTML"),
        Map.entry("css", "CSS"),
        Map.entry("scss", "CSS"),
        Map.entry("vue", "Vue"),
        Map.entry("svelte", "Svelte"),
        Map.entry("dart", "Dart"),
        Map.entry("ex", "Elixir"),
        Map.entry("exs", "Elixir")
    );

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
                    .map(fc -> extractLanguage(fc.getFilename()))
                    .filter(lang -> lang != null)
                    .collect(Collectors.toSet());

                if (languages.size() >= MIN_LANGUAGES) {
                    userAchievement.setProgressData(new BinaryAchievementProgress(true));
                    return true;
                }
                return false;
            })
            .orElse(false);
    }

    private String extractLanguage(String filename) {
        if (filename == null) return null;
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) return null;
        String ext = filename.substring(lastDot + 1).toLowerCase();
        return EXTENSION_TO_LANGUAGE.get(ext);
    }
}
