package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.practices.review.dto.GeneratedPathReviewDTO;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.util.AntPathMatcher;

/** Repository-root, case-sensitive Ant globs; never filesystem-dependent or provider-dependent. */
public final class GeneratedPaths {
    private static final AntPathMatcher MATCHER = new AntPathMatcher("/");

    private GeneratedPaths() {}

    public static List<String> normalize(List<String> patterns) {
        if (patterns == null || patterns.size() > 100) {
            throw new InvalidReviewScopeException("Use at most 100 generated-path patterns per repository");
        }
        return patterns.stream()
                .map(pattern -> {
                    if (pattern == null)
                        throw new InvalidReviewScopeException("A generated-path pattern must not be null");
                    String value = pattern.trim();
                    if (value.isEmpty()
                            || value.length() > 512
                            || value.startsWith("/")
                            || value.startsWith("!")
                            || value.contains("\\")
                            || value.contains("{")
                            || value.contains("}")
                            || Arrays.asList(value.split("/", -1)).contains("..")) {
                        throw new InvalidReviewScopeException(
                                "Use a repository-relative generated-path glob of 1 to 512 characters. Do not use"
                                        + " negation, parent paths, backslashes, or URI variables.");
                    }
                    return value;
                })
                .distinct()
                .sorted()
                .toList();
    }

    public static GeneratedPathReviewDTO review(List<String> patterns, Set<String> paths) {
        var normalized = normalize(patterns);
        var excluded = paths.stream()
                .filter(path -> matches(normalized, path))
                .sorted()
                .toList();
        boolean withheld = !paths.isEmpty() && excluded.size() == paths.size();
        return new GeneratedPathReviewDTO(normalized, excluded, withheld, withheld ? "ALL_PATHS_GENERATED" : null);
    }

    public static boolean matches(List<String> patterns, String path) {
        return patterns.stream().anyMatch(pattern -> MATCHER.match(pattern, path));
    }
}
