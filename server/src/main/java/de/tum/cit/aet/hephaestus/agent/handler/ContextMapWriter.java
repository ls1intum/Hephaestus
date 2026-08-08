package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes {@code inputs/context-map.md}: where to look for the code a changed line depends on.
 *
 * <p>An agent given only a mount point pays a search cost bounded by the size of the tree before it
 * learns anything. The map converts that search into a handful of named starting points — the
 * neighbours of each changed file, its likely test counterpart, and the files elsewhere that mention
 * it — so one targeted read replaces an exploration.
 *
 * <p>Structural only: paths, extensions, basenames, and literal mentions. It never claims what a file
 * means, so it behaves the same on any language or layout and cannot state something untrue about code
 * it has not parsed.
 */
final class ContextMapWriter {

    /** Enough neighbours to orient in a directory; past this the listing stops being a pointer. */
    private static final int MAX_NEIGHBOURS = 12;

    /** Referencing files worth naming per changed file. Beyond a few, "widely used" is the finding. */
    private static final int MAX_REFERENCES = 5;

    private static final Pattern DIFF_HEADER = Pattern.compile("^diff --git a/.* b/(.+)$", Pattern.MULTILINE);
    private static final Pattern TEST_NAME = Pattern.compile("(?i)(^|[/_.-])(test|spec)s?([/_.-]|$)");

    private ContextMapWriter() {}

    static void write(Map<String, byte[]> files) {
        List<String> changed = changedPaths(files);
        if (changed.isEmpty()) {
            return;
        }
        Set<String> tree = treePaths(files);
        StringBuilder map = new StringBuilder("# Context map\n\n");
        map
            .append("Where to look for the code the change depends on. Paths are workspace-relative.\n")
            .append("Findings still quote `inputs/context/diff.patch`; this is for judging what you read there.\n\n")
            .append("## The change\n\n- `")
            .append(SandboxLayout.CONTEXT_PREFIX)
            .append("diff.patch` — ")
            .append(changed.size())
            .append(changed.size() == 1 ? " changed file\n" : " changed files\n");
        for (String path : changed) {
            map.append("  - `").append(path).append("`\n");
        }
        if (tree.isEmpty()) {
            map.append(
                "\n## The repository\n\nNot staged for this review. The diff and the context files are all the code evidence there is.\n"
            );
        } else {
            appendRepository(map, changed, tree);
        }
        files.put(SandboxLayout.CONTEXT_PREFIX + "context-map.md", map.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void appendRepository(StringBuilder map, List<String> changed, Set<String> tree) {
        map
            .append("\n## The repository at the reviewed commit\n\n`")
            .append(SandboxLayout.REPO_MOUNT_RELATIVE)
            .append("` — ")
            .append(tree.size())
            .append(" files.\n");
        for (String path : changed) {
            List<String> neighbours = neighbours(path, tree);
            List<String> counterparts = testCounterparts(path, tree);
            List<String> references = references(path, tree);
            if (neighbours.isEmpty() && counterparts.isEmpty() && references.isEmpty()) {
                continue;
            }
            map.append("\n### `").append(path).append("`\n");
            if (!neighbours.isEmpty()) {
                map.append("- Alongside it: ").append(joinCode(neighbours)).append("\n");
            }
            map.append(
                counterparts.isEmpty()
                    ? "- No test named after it. If this practice is about tests, that absence is the evidence.\n"
                    : "- Named like a test for it: " + joinCode(counterparts) + "\n"
            );
            if (!references.isEmpty()) {
                map.append("- Mentions it elsewhere: ").append(joinCode(references)).append("\n");
            }
        }
    }

    /** Files sharing a directory with the changed file, so a hunk can be read against its surroundings. */
    private static List<String> neighbours(String changedPath, Set<String> tree) {
        String directory = changedPath.contains("/") ? changedPath.substring(0, changedPath.lastIndexOf('/') + 1) : "";
        List<String> siblings = new ArrayList<>();
        for (String candidate : tree) {
            if (
                candidate.startsWith(directory) &&
                !candidate.equals(changedPath) &&
                candidate.indexOf('/', directory.length()) < 0
            ) {
                siblings.add(candidate);
            }
            if (siblings.size() > MAX_NEIGHBOURS) {
                siblings.set(MAX_NEIGHBOURS, "…");
                return siblings;
            }
        }
        return siblings;
    }

    /**
     * Files whose path carries a test marker and whose name contains the changed file's stem.
     *
     * <p>Matching on the stem rather than a language's convention keeps this working for
     * {@code FooTest.java}, {@code foo_test.go}, {@code foo.spec.ts} and {@code tests/test_foo.py} alike.
     */
    private static List<String> testCounterparts(String changedPath, Set<String> tree) {
        String stem = stem(changedPath);
        if (stem.isBlank() || TEST_NAME.matcher(changedPath).find()) {
            return List.of();
        }
        List<String> matches = new ArrayList<>();
        for (String candidate : tree) {
            if (
                !candidate.equals(changedPath) &&
                TEST_NAME.matcher(candidate).find() &&
                fileName(candidate).contains(stem)
            ) {
                matches.add(candidate);
            }
            if (matches.size() >= MAX_REFERENCES) {
                break;
            }
        }
        return matches;
    }

    /** Files that name the changed file's stem, which is where a rename or deletion breaks something. */
    private static List<String> references(String changedPath, Set<String> tree) {
        String stem = stem(changedPath);
        if (stem.isBlank()) {
            return List.of();
        }
        List<String> matches = new ArrayList<>();
        for (String candidate : tree) {
            if (!candidate.equals(changedPath) && fileName(candidate).contains(stem)) {
                matches.add(candidate);
            }
            if (matches.size() >= MAX_REFERENCES) {
                break;
            }
        }
        return matches;
    }

    private static List<String> changedPaths(Map<String, byte[]> files) {
        byte[] diff = files.get(SandboxLayout.CONTEXT_PREFIX + "diff.patch");
        if (diff == null || diff.length == 0) {
            return List.of();
        }
        Set<String> paths = new LinkedHashSet<>();
        Matcher matcher = DIFF_HEADER.matcher(new String(diff, StandardCharsets.UTF_8));
        while (matcher.find()) {
            paths.add(matcher.group(1).trim());
        }
        return List.copyOf(paths);
    }

    private static Set<String> treePaths(Map<String, byte[]> files) {
        Set<String> paths = new TreeSet<>();
        for (String path : new LinkedHashMap<>(files).keySet()) {
            if (path.startsWith(SandboxLayout.REPO_MOUNT_RELATIVE)) {
                paths.add(path.substring(SandboxLayout.REPO_MOUNT_RELATIVE.length()));
            }
        }
        return paths;
    }

    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String stem(String path) {
        String name = fileName(path);
        int dot = name.indexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    private static String joinCode(List<String> paths) {
        return paths
            .stream()
            .map(path -> "…".equals(path) ? "…" : "`" + path + "`")
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    }
}
