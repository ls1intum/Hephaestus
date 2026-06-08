package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Durable guard against test classes that execute in <em>zero</em> tiers.
 *
 * <p><b>Why this rule exists.</b> CI runs each tier by JUnit tag — {@code unit}, {@code architecture},
 * {@code integration}, {@code live} (see {@code ci-tests.yml} and {@code pom.xml}). A concrete test
 * class that carries none of those tags (directly or via a tagged base such as {@code BaseUnitTest} /
 * {@code BaseIntegrationTest} / {@code HephaestusArchitectureTest}) is excluded from every tier and
 * silently never runs while looking like coverage. This actually happened — three unit tests
 * (incl. a security-relevant resolver) and five live tests rotted undetected because they were
 * untagged. {@link IntegrationTestNamingConventionTest} only covers direct {@code BaseIntegrationTest}
 * subclasses; this rule covers the general case.
 *
 * <p><b>Scope.</b> A source scan (same approach as {@link IntegrationTestNamingConventionTest}):
 * every concrete class containing test methods ({@code @Test}/{@code @ParameterizedTest}/
 * {@code @RepeatedTest}/{@code @TestFactory}/{@code @Nested}) must resolve to a tier tag through its
 * own {@code @Tag} or its superclass chain. Abstract bases are exempt (never executed directly).
 */
@Tag("architecture")
class TestTierTagCompletenessTest {

    private static final Set<String> TIER_TAGS = Set.of("unit", "architecture", "integration", "live");

    private static final Pattern CLASS_DECL = Pattern.compile(
        "\\b(abstract\\s+)?class\\s+(\\w+)\\b([^\\n{]*)\\{",
        Pattern.DOTALL
    );
    private static final Pattern EXTENDS = Pattern.compile("\\bextends\\s+(\\w+)");
    private static final Pattern TAG = Pattern.compile("@Tag\\s*\\(\\s*\"(\\w+)\"");
    private static final Pattern TEST_METHOD = Pattern.compile(
        "@(Test|ParameterizedTest|RepeatedTest|TestFactory|Nested)\\b"
    );
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    private record TypeInfo(Set<String> tags, String superType, boolean isAbstract, boolean hasTestMethods) {}

    @Test
    void everyConcreteTestClassResolvesToATierTag() {
        Path testRoot = locateTestRoot();
        Map<String, TypeInfo> types = new HashMap<>();

        try (Stream<Path> sources = Files.walk(testRoot)) {
            sources
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(p -> parse(p, types));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Set<String> violations = new TreeSet<>();
        for (Map.Entry<String, TypeInfo> e : types.entrySet()) {
            TypeInfo t = e.getValue();
            if (t.isAbstract() || !t.hasTestMethods()) {
                continue;
            }
            if (!resolvesToTier(e.getKey(), types, new HashSet<>())) {
                violations.add("  " + e.getKey());
            }
        }

        assertThat(violations)
            .as(
                "These concrete test classes carry no tier tag (unit/architecture/integration/live) " +
                    "directly or via a tagged base, so no CI tier runs them — they never execute. " +
                    "Add the correct @Tag (or extend a tagged base):\n" +
                    String.join("\n", violations)
            )
            .isEmpty();
    }

    private static boolean resolvesToTier(String type, Map<String, TypeInfo> types, Set<String> seen) {
        if (type == null || !seen.add(type)) {
            return false;
        }
        TypeInfo info = types.get(type);
        if (info == null) {
            return false; // base outside the test tree (e.g. a JDK/library class) carries no tier tag
        }
        if (info.tags().stream().anyMatch(TIER_TAGS::contains)) {
            return true;
        }
        return resolvesToTier(info.superType(), types, seen);
    }

    private static void parse(Path file, Map<String, TypeInfo> types) {
        String raw;
        try {
            raw = Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // Strip block comments so commented-out code / Javadoc samples don't register as declarations.
        String content = BLOCK_COMMENT.matcher(raw).replaceAll("");
        String simpleName = file.getFileName().toString().replace(".java", "");

        Matcher m = CLASS_DECL.matcher(content);
        while (m.find()) {
            String name = m.group(2);
            // Only record the file's primary (filename-matching) type; nested classes inherit the
            // enclosing class's tags in JUnit, so resolution at the top level is sufficient.
            if (!name.equals(simpleName)) {
                continue;
            }
            boolean isAbstract = m.group(1) != null;
            String header = m.group(3);
            Matcher em = EXTENDS.matcher(header);
            String superType = em.find() ? em.group(1) : null;

            // Tags + test methods are searched over the whole (comment-stripped) file: the annotation
            // sits just above the class, and test methods/@Nested live inside it.
            Set<String> tags = new HashSet<>();
            Matcher tm = TAG.matcher(content);
            while (tm.find()) {
                tags.add(tm.group(1));
            }
            boolean hasTestMethods = TEST_METHOD.matcher(content).find();
            types.put(name, new TypeInfo(tags, superType, isAbstract, hasTestMethods));
        }
    }

    private static Path locateTestRoot() {
        Path serverDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path testRoot = serverDir.resolve("src/test/java/de/tum/cit/aet/hephaestus");
        if (!Files.isDirectory(testRoot)) {
            testRoot = serverDir.resolve("server/src/test/java/de/tum/cit/aet/hephaestus");
        }
        if (!Files.isDirectory(testRoot)) {
            throw new IllegalStateException("Could not locate test source root from user.dir=" + serverDir);
        }
        return testRoot;
    }
}
