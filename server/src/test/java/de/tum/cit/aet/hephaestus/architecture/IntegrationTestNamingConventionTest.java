package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Durable guard against silently-dead integration tests.
 *
 * <p><b>Why this rule exists.</b> The Maven Failsafe plugin is configured to discover integration
 * tests by the single filename pattern {@code **&#47;*IntegrationTest.java} (see {@code pom.xml}),
 * while Surefire runs only {@code @Tag("unit")}. A concrete {@code BaseIntegrationTest} subclass is
 * {@code @Tag("integration")} (inherited), so if it is named {@code *Test} (or anything not ending
 * in {@code IntegrationTest}) it matches <em>neither</em> runner and never executes in any build —
 * it asserts nothing while looking like coverage. This actually happened to three tests, including
 * two security guards, before this rule was added.
 *
 * <p><b>Scope.</b> A source scan (mirroring {@link NoMockingOwnedEntitiesTest}) of classes that
 * directly {@code extends BaseIntegrationTest}. Abstract bases are exempt (they are never executed
 * directly). Tests extending an intermediate abstract base are out of scope of this particular scan;
 * the convention is enforced where the defect occurred — direct subclasses.
 */
@Tag("architecture")
class IntegrationTestNamingConventionTest {

    private static final String INTEGRATION_BASE = "BaseIntegrationTest";

    /** {@code [abstract] class <Name> ... extends BaseIntegrationTest} — captures the modifier + name. */
    private static final Pattern CLASS_DECL = Pattern.compile(
        "\\b(abstract\\s+)?class\\s+(\\w+)[^\\n{]*\\bextends\\s+" + INTEGRATION_BASE + "\\b"
    );

    @Test
    void everyConcreteIntegrationTestIsNamedIntegrationTestSoFailsafeRunsIt() {
        Path testRoot = locateTestRoot();
        List<String> violations = new ArrayList<>();

        try (Stream<Path> sources = Files.walk(testRoot)) {
            sources
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                // The base type itself and this guard legitimately mention BaseIntegrationTest.
                .filter(p -> !p.getFileName().toString().equals(INTEGRATION_BASE + ".java"))
                .filter(p -> !p.getFileName().toString().equals("IntegrationTestNamingConventionTest.java"))
                .forEach(p -> scanFile(p, violations));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertThat(violations)
            .as(
                "Concrete BaseIntegrationTest subclasses must be named *IntegrationTest — otherwise Maven " +
                    "Failsafe (which discovers only **/*IntegrationTest.java) never runs them and Surefire " +
                    "(unit group only) excludes them, so they silently never execute. Rename the offenders:\n" +
                    String.join("\n", violations)
            )
            .isEmpty();
    }

    private static void scanFile(Path file, List<String> violations) {
        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Matcher m = CLASS_DECL.matcher(content);
        while (m.find()) {
            boolean isAbstract = m.group(1) != null;
            String className = m.group(2);
            if (!isAbstract && !className.endsWith("IntegrationTest")) {
                violations.add(
                    "  " + file.getFileName() + "  [" + className + "] should be " + className + "IntegrationTest"
                );
            }
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
