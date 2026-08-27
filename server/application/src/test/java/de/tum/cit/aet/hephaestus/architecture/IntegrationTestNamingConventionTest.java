package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Durable guard against silently-dead integration tests.
 *
 * <p><b>Why this rule exists.</b> The Maven Failsafe plugin is configured to discover integration
 * Spring integration tests by {@code **&#47;*IntegrationTest.java} and raw schema tests by
 * {@code **&#47;*LiquibaseTest.java} (see {@code pom.xml}), while Surefire runs only
 * {@code @Tag("unit")}. A concrete {@code BaseIntegrationTest} subclass is
 * {@code @Tag("integration")} (inherited), so it must use the {@code IntegrationTest} suffix or no
 * runner executes it — and a test no runner executes is indistinguishable from a passing one.
 *
 * <p>The scan resolves {@code extends} transitively. Most controller tests reach
 * {@code BaseIntegrationTest} through an intermediate base such as
 * {@code AbstractWorkspaceIntegrationTest}, so a scan that only matched a literal
 * {@code extends BaseIntegrationTest} would leave the bulk of the suite uncovered.
 *
 * <p>It is a source scan (mirroring {@link NoMockingOwnedEntitiesTest}) rather than an ArchUnit class
 * scan, because the defect is about <em>file names</em> — all Failsafe looks at — and a compiled class
 * graph cannot see them. Only top-level declarations count: Failsafe matches a file, so a nested
 * {@code @Nested} class is never independently discoverable and never independently dead.
 */
@Tag("architecture")
class IntegrationTestNamingConventionTest {

    private static final String INTEGRATION_BASE = "BaseIntegrationTest";

    /**
     * Anchored at column 0 so nested classes (always indented by the formatter) are skipped, and so
     * javadoc/comment lines — which start with {@code *} or {@code /} — can never match.
     */
    private static final Pattern TOP_LEVEL_CLASS_DECL = Pattern.compile(
        "(?m)^(\\w[\\w\\s-]*?\\s)?class\\s+(\\w+)([^\\n{]*)"
    );

    private static final Pattern EXTENDS_CLAUSE = Pattern.compile("\\bextends\\s+(\\w+)");

    private record TestClass(
        String name,
        boolean isAbstract,
        @org.jspecify.annotations.Nullable String superName,
        Path file
    ) {}

    @Test
    void everyConcreteIntegrationTestIsNamedIntegrationTestSoFailsafeRunsIt() {
        Map<String, TestClass> declarations = scanTestSources(locateTestRoot());

        List<String> violations = declarations
            .values()
            .stream()
            .filter(decl -> !decl.isAbstract())
            .filter(decl -> !decl.name().endsWith("IntegrationTest"))
            .filter(decl -> integrationBaseChain(decl, declarations) != null)
            .map(
                decl ->
                    "  " +
                    decl.file().getFileName() +
                    "  [" +
                    decl.name() +
                    "] should be " +
                    decl.name().replaceFirst("Test$", "") +
                    "IntegrationTest — inherits @Tag(\"integration\") via " +
                    integrationBaseChain(decl, declarations)
            )
            .sorted()
            .toList();

        assertThat(violations)
            .as(
                "Concrete BaseIntegrationTest subclasses — directly or through any abstract base — must be " +
                    "named *IntegrationTest. Otherwise Maven Failsafe (which discovers only " +
                    "**/*IntegrationTest.java) never runs them and Surefire (unit group only) excludes them, " +
                    "so they silently never execute. Rename the offenders:\n" +
                    String.join("\n", violations)
            )
            .isEmpty();
    }

    /**
     * @return the {@code SomeBase -> BaseIntegrationTest} chain, or {@code null} when {@code decl} does
     *     not transitively extend the integration base. Cycles (impossible in valid Java, but reachable
     *     in a half-edited source tree) terminate instead of hanging.
     */
    private static @org.jspecify.annotations.Nullable String integrationBaseChain(
        TestClass decl,
        Map<String, TestClass> declarations
    ) {
        Set<String> chain = new LinkedHashSet<>();
        String current = decl.superName();
        while (current != null && chain.add(current)) {
            if (current.equals(INTEGRATION_BASE)) {
                return String.join(" -> ", chain);
            }
            TestClass parent = declarations.get(current);
            current = parent != null ? parent.superName() : null;
        }
        return null;
    }

    private static Map<String, TestClass> scanTestSources(Path testRoot) {
        Map<String, TestClass> declarations = new HashMap<>();
        try (Stream<Path> sources = Files.walk(testRoot)) {
            sources
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(file -> parse(file).forEach(decl -> declarations.put(decl.name(), decl)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return declarations;
    }

    private static List<TestClass> parse(Path file) {
        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<TestClass> found = new ArrayList<>();
        Matcher m = TOP_LEVEL_CLASS_DECL.matcher(content);
        while (m.find()) {
            String modifiers = m.group(1) == null ? "" : m.group(1);
            Matcher ext = EXTENDS_CLAUSE.matcher(m.group(3));
            found.add(
                new TestClass(m.group(2), modifiers.contains("abstract"), ext.find() ? ext.group(1) : null, file)
            );
        }
        return found;
    }

    private static Path locateTestRoot() {
        Path serverDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path testRoot = serverDir.resolve("src/test/java/de/tum/cit/aet/hephaestus");
        if (!Files.isDirectory(testRoot)) {
            testRoot = serverDir.resolve("server/application/src/test/java/de/tum/cit/aet/hephaestus");
        }
        if (!Files.isDirectory(testRoot)) {
            throw new IllegalStateException("Could not locate test source root from user.dir=" + serverDir);
        }
        return testRoot;
    }
}
