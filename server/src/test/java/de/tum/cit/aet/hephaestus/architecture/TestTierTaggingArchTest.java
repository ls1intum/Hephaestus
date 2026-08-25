package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
 * Durable guard against a test that belongs to no tier.
 *
 * <p><b>What breaks without it.</b> Surefire runs {@code groups=unit}, Failsafe runs
 * {@code groups=integration}, and both set {@code excludedGroups=live}; the {@code architecture-tests}
 * and {@code live-tests} profiles swap the included group (see {@code pom.xml}). A test class carrying
 * none of the four tier tags therefore matches no runner's group filter — it executes in no tier, fails
 * no build, and reports no skip. It is indistinguishable from a passing test.
 *
 * <p><b>Why the scan is not a grep.</b> A tier tag is usually inherited rather than written:
 * {@code BaseUnitTest} is {@code @Tag("unit")} and {@code BaseIntegrationTest} is
 * {@code @Tag("integration")}. Tags also arrive through <em>meta-annotations</em> —
 * {@code @LiveGitHubTest} is itself {@code @Tag("live")}. Resolve only one of the two routes and the
 * answer is wrong in both directions: orphans invented for classes that are fine, and the one real
 * orphan missed. Carrying more than one tier is legal and deliberate — the GitHub live suite is
 * {@code integration} from {@code BaseIntegrationTest} <em>and</em> {@code live} from
 * {@code @LiveGitHubTest}, and Failsafe's {@code excludedGroups=live} is what keeps it out of
 * {@code mvn verify} — so the rule is "at least one", not "exactly one".
 *
 * <p><b>Why it reads source rather than the compiled class graph.</b> Half of the invariant is about
 * <em>file names</em>, which Failsafe matches and a class graph cannot see.
 * {@link #anIntegrationTaggedTestIsNamedSoFailsafeRunsIt()} generalises
 * {@link IntegrationTestNamingConventionTest}, which follows only chains rooted at
 * {@code BaseIntegrationTest} and so is blind to a class that spells its tag out and extends nothing.
 * The narrower rule is kept because its failure message names the chain.
 */
@Tag("architecture")
class TestTierTaggingArchTest {

    private static final Set<String> TIERS = Set.of("unit", "architecture", "database", "integration", "live");

    /** The two suffixes Failsafe is configured to discover. Anything else is dead on arrival. */
    private static final Set<String> FAILSAFE_SUFFIXES = Set.of("IntegrationTest", "LiquibaseTest");

    /**
     * Surefire's default discovery patterns. A class matching none of them is not picked up by either
     * runner whatever its tags, so it is not an orphan — it is not a test class at all.
     */
    private static final Pattern DISCOVERABLE_NAME = Pattern.compile("^(Test\\w+|\\w+Test|\\w+Tests|\\w+TestCase)$");

    /** Anchored at column 0, so nested types (always indented) and javadoc lines can never match. */
    private static final Pattern TOP_LEVEL_TYPE_DECL = Pattern.compile(
        "(?m)^(\\w[\\w\\s-]*?\\s)?(class|@interface)\\s+(\\w+)"
    );

    private static final Pattern EXTENDS_CLAUSE = Pattern.compile("\\bextends\\s+(\\w+)");
    private static final Pattern TAG_VALUE = Pattern.compile("@Tag\\(\\s*\"([^\"]+)\"\\s*\\)");
    private static final Pattern ANNOTATION_USE = Pattern.compile("@(\\w+)");
    private static final Pattern TEST_METHOD = Pattern.compile(
        "@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\\b"
    );

    /**
     * Block and line comments. Javadoc on the rules in this very package quotes {@code @Tag("...")} to
     * explain itself, so a scan that reads the raw header attributes those quotes to the class and
     * reports the rule as its own violation.
     */
    private static final Pattern COMMENT = Pattern.compile("(?s)/\\*.*?\\*/|//[^\\n]*");

    private record TypeDecl(
        String name,
        boolean isAbstract,
        boolean isAnnotation,
        @org.jspecify.annotations.Nullable String superName,
        Set<String> tags,
        Set<String> annotations,
        boolean declaresTests,
        Path file
    ) {}

    @Test
    void everyDiscoverableTestClassCarriesATierTag() {
        Map<String, TypeDecl> types = scanTestSources();

        List<String> violations = types
            .values()
            .stream()
            .filter(decl -> isRunnable(decl, types))
            .filter(decl -> resolveTiers(decl, types).isEmpty())
            .map(decl -> "  " + decl.file().getFileName() + "  [" + decl.name() + "]")
            .sorted()
            .toList();

        assertThat(violations)
            .as(
                "Every discoverable test class must resolve to at least one of %s — directly, through its " +
                    "extends chain, or through a meta-annotation. Surefire filters on groups=unit and Failsafe " +
                    "on groups=integration, so a class with no tier tag runs in no tier and reports no skip: it " +
                    "is silently dead. Extend BaseUnitTest / BaseIntegrationTest, or add the @Tag the test's " +
                    "behaviour calls for — a test that boots a Spring context is not a unit test:%n%s",
                new TreeSet<>(TIERS),
                String.join("\n", violations)
            )
            .isEmpty();
    }

    @Test
    void anIntegrationTaggedTestIsNamedSoFailsafeRunsIt() {
        Map<String, TypeDecl> types = scanTestSources();

        List<String> violations = types
            .values()
            .stream()
            .filter(decl -> isRunnable(decl, types))
            .filter(decl -> resolveTiers(decl, types).contains("integration"))
            .filter(decl -> FAILSAFE_SUFFIXES.stream().noneMatch(suffix -> decl.name().endsWith(suffix)))
            .map(
                decl ->
                    "  " + decl.name() + " → rename to " + decl.name().replaceFirst("Tests?$", "") + "IntegrationTest"
            )
            .sorted()
            .toList();

        assertThat(violations)
            .as(
                "Failsafe discovers only **/*IntegrationTest.java and **/*LiquibaseTest.java, so an " +
                    "@Tag(\"integration\") class named anything else is never executed by `mvn verify` — and " +
                    "Surefire excludes it too, because it runs groups=unit. Rename:%n%s",
                String.join("\n", violations)
            )
            .isEmpty();
    }

    /**
     * Failsafe matches a <em>file</em> while JUnit reads the tags off the <em>class</em>, so the two
     * names diverging is enough to make every other rule here describe a file that is not the one being
     * run. It also silently breaks {@code -Dtest=<name>}, which takes the class name.
     */
    @Test
    void everyTestFileIsNamedAfterTheClassItDeclares() {
        Map<String, TypeDecl> types = scanTestSources();

        List<String> violations = types
            .values()
            .stream()
            .filter(decl -> !decl.isAnnotation())
            .filter(decl -> {
                String fileName = decl.file().getFileName().toString();
                return !fileName.equals(decl.name() + ".java");
            })
            .map(decl -> "  " + decl.file().getFileName() + " declares class " + decl.name())
            .sorted()
            .toList();

        assertThat(violations)
            .as(
                "A test file must be named after the top-level class it declares. Java only enforces this " +
                    "for public classes, and every test class here is package-private, so the two drift apart " +
                    "silently on a rename — leaving `-Dtest=<file name>` matching nothing and every file-based " +
                    "scan (this one included) reporting the wrong name:%n%s",
                String.join("\n", violations)
            )
            .isEmpty();
    }

    /**
     * A concrete class a runner would discover by name, which declares test methods or inherits them
     * from an abstract base (the {@code *ManifestContractTest} family declares none of its own).
     */
    private static boolean isRunnable(TypeDecl decl, Map<String, TypeDecl> types) {
        if (decl.isAnnotation() || decl.isAbstract() || !DISCOVERABLE_NAME.matcher(decl.name()).matches()) {
            return false;
        }
        return walkSuperChain(decl, types).stream().anyMatch(TypeDecl::declaresTests);
    }

    /**
     * @return every tier tag reachable from {@code decl}: its own {@code @Tag}s, those of any annotation
     *     it applies that is declared in this tree and carries a {@code @Tag}, and the same for every
     *     supertype.
     */
    private static Set<String> resolveTiers(TypeDecl decl, Map<String, TypeDecl> types) {
        Set<String> tiers = new LinkedHashSet<>();
        for (TypeDecl type : walkSuperChain(decl, types)) {
            type.tags().stream().filter(TIERS::contains).forEach(tiers::add);
            for (String used : type.annotations()) {
                TypeDecl meta = types.get(used);
                if (meta != null && meta.isAnnotation()) {
                    meta.tags().stream().filter(TIERS::contains).forEach(tiers::add);
                }
            }
        }
        return tiers;
    }

    /**
     * @return {@code decl} and every supertype declared in this tree, nearest first. Cycles (impossible
     *     in valid Java, but reachable in a half-edited source tree) terminate instead of hanging.
     */
    private static List<TypeDecl> walkSuperChain(TypeDecl decl, Map<String, TypeDecl> types) {
        List<TypeDecl> chain = new java.util.ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        TypeDecl current = decl;
        while (current != null && visited.add(current.name())) {
            chain.add(current);
            current = current.superName() == null ? null : types.get(current.superName());
        }
        return chain;
    }

    private static Map<String, TypeDecl> scanTestSources() {
        Map<String, TypeDecl> types = new HashMap<>();
        try (Stream<Path> sources = Files.walk(locateTestRoot())) {
            sources
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(file -> {
                    TypeDecl decl = parse(file);
                    if (decl != null) {
                        types.put(decl.name(), decl);
                    }
                });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return types;
    }

    /**
     * @return the file's single top-level class or annotation, or {@code null} for one that declares
     *     neither (an enum, an interface, a {@code package-info}). Every test file in this tree declares
     *     exactly one top-level type, so the annotations preceding it are unambiguously its own —
     *     {@link #everyTestFileIsNamedAfterTheClassItDeclares()} is what keeps that true.
     */
    private static @org.jspecify.annotations.Nullable TypeDecl parse(Path file) {
        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Matcher decl = TOP_LEVEL_TYPE_DECL.matcher(content);
        if (!decl.find()) {
            return null;
        }
        String header = COMMENT.matcher(content.substring(0, decl.start())).replaceAll(" ");
        String afterName = content.substring(decl.end());

        Set<String> tags = new LinkedHashSet<>();
        Matcher tag = TAG_VALUE.matcher(header);
        while (tag.find()) {
            tags.add(tag.group(1));
        }
        Set<String> annotations = new LinkedHashSet<>();
        Matcher use = ANNOTATION_USE.matcher(header);
        while (use.find()) {
            annotations.add(use.group(1));
        }
        // The extends clause sits between the type name and the opening brace.
        int brace = afterName.indexOf('{');
        Matcher ext = EXTENDS_CLAUSE.matcher(brace < 0 ? "" : afterName.substring(0, brace));

        return new TypeDecl(
            decl.group(3),
            (decl.group(1) == null ? "" : decl.group(1)).contains("abstract"),
            "@interface".equals(decl.group(2)),
            ext.find() ? ext.group(1) : null,
            tags,
            annotations,
            TEST_METHOD.matcher(content).find(),
            file
        );
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
