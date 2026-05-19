package de.tum.in.www1.hephaestus.gitprovider.common;

import java.util.Map;
import org.springframework.lang.Nullable;

/**
 * Shared mapping from file extensions to programming language names.
 * Used by achievement evaluators (CrossBoundary, Polyglot) and potentially
 * commit analytics.
 */
public final class LanguageExtensions {

    private LanguageExtensions() {}

    public static final Map<String, String> EXTENSION_TO_LANGUAGE = Map.ofEntries(
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

    /**
     * Detect the programming language from a filename based on its extension.
     *
     * @param filename the filename (may include path)
     * @return the language name, or null if the extension is not recognized
     */
    @Nullable
    public static String detectLanguage(String filename) {
        if (filename == null) return null;
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) return null;
        String ext = filename.substring(lastDot + 1).toLowerCase();
        return EXTENSION_TO_LANGUAGE.get(ext);
    }
}
