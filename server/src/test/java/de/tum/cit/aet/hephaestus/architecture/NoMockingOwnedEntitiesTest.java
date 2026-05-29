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
 * Durable guard against re-introducing "bullshit mocking" of owned JPA {@code @Entity} POJOs
 * in unit tests.
 *
 * <p><b>Why this rule exists.</b> Mocking an owned entity and stubbing its getters is
 * tautological: {@code when(ws.getId()).thenReturn(1L)} followed by an assertion on
 * {@code 1L} tests the stub, not the system under test. It also couples the test to getter
 * names instead of the real entity wiring (relationships, equals/hashCode). Every entity
 * guarded here carries Lombok {@code @NoArgsConstructor + @Setter}, so the real object is
 * both cheaper and strictly more faithful. Construct the real object via
 * {@code de.tum.cit.aet.hephaestus.testconfig.TestEntities} (or {@code new Entity()} + setters)
 * instead of mocking it.
 *
 * <p><b>What is NOT covered.</b> Spring Data {@code *Repository} interfaces, GraphQL /
 * WebClient / NATS / Docker SDKs, {@code Clock}, {@code ApplicationEventPublisher}, SPI
 * interfaces, etc. are legitimate boundary mocks. This rule deliberately targets only the
 * concrete owned {@code @Entity} classes listed in {@link #GUARDED_ENTITIES}.
 *
 * <p><b>Implementation.</b> A source scan (rather than ArchUnit bytecode inspection) because
 * {@code mock(X.class)} compiles to a {@code Class<T>} literal whose entity identity is hard
 * to resolve reliably from bytecode, whereas the source form is unambiguous and the failure
 * message can point straight at the offending file/line.
 */
@Tag("architecture")
class NoMockingOwnedEntitiesTest {

    /**
     * Simple names of owned {@code @Entity} classes that must never be Mockito-mocked in tests.
     * These all have {@code @NoArgsConstructor + @Setter}; build the real object instead.
     */
    private static final List<String> GUARDED_ENTITIES = List.of(
        "Repository",
        "Workspace",
        "PullRequest",
        "AgentJob",
        "Commit",
        "Organization",
        "GitProvider",
        "User"
    );

    /** {@code mock(Entity.class)} or {@code Mockito.mock(Entity.class)}, with optional settings arg. */
    private static final Pattern MOCK_CALL = Pattern.compile(
        "\\bmock\\s*\\(\\s*(" + String.join("|", GUARDED_ENTITIES) + ")\\.class\\b"
    );

    /** {@code spy(Entity.class)} / {@code spy(new Entity())} / {@code Mockito.spy(...)}. */
    private static final Pattern SPY_CALL = Pattern.compile(
        "\\bspy\\s*\\(\\s*(?:new\\s+)?(" + String.join("|", GUARDED_ENTITIES) + ")\\b"
    );

    /** {@code @Mock [modifiers] Entity field;} — the field-injection form. */
    private static final Pattern MOCK_FIELD = Pattern.compile(
        "@Mock\\b[^;\\n]*?\\b(" + String.join("|", GUARDED_ENTITIES) + ")\\s+\\w+\\s*;"
    );

    @Test
    void noTestMocksAnOwnedEntity() {
        Path testRoot = locateTestRoot();
        List<String> violations = new ArrayList<>();

        try (Stream<Path> sources = Files.walk(testRoot)) {
            sources
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                // Don't flag this guard itself, nor the object-mother that names the entities.
                .filter(p -> !p.getFileName().toString().equals("NoMockingOwnedEntitiesTest.java"))
                .filter(p -> !p.getFileName().toString().equals("TestEntities.java"))
                .forEach(p -> scanFile(p, violations));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertThat(violations)
            .as(
                "Owned JPA @Entity classes must NOT be Mockito-mocked in tests — mocking them and " +
                    "stubbing getters is tautological (tests the stub, not the SUT) and couples tests to " +
                    "getter names instead of real entity wiring. Build the real object via " +
                    "de.tum.cit.aet.hephaestus.testconfig.TestEntities (or new Entity() + setters). " +
                    "Guarded entities: " +
                    GUARDED_ENTITIES +
                    ". Offending sites:\n" +
                    String.join("\n", violations)
            )
            .isEmpty();
    }

    private static void scanFile(Path file, List<String> violations) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // Join for multi-line @Mock field declarations, but report per-line for mock()/spy().
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher mockMatcher = MOCK_CALL.matcher(line);
            if (mockMatcher.find()) {
                violations.add(format(file, i + 1, "mock(" + mockMatcher.group(1) + ".class)", line));
            }
            Matcher spyMatcher = SPY_CALL.matcher(line);
            if (spyMatcher.find()) {
                violations.add(format(file, i + 1, "spy(" + spyMatcher.group(1) + ")", line));
            }
        }
        // @Mock-on-field can span lines; scan the whole content.
        String content = String.join("\n", lines);
        Matcher fieldMatcher = MOCK_FIELD.matcher(content);
        while (fieldMatcher.find()) {
            violations.add(
                format(file, lineOf(content, fieldMatcher.start()), "@Mock " + fieldMatcher.group(1) + " field", "")
            );
        }
    }

    private static String format(Path file, int line, String what, String snippet) {
        String tail = snippet.isBlank() ? "" : " -> " + snippet.trim();
        return "  " + file.getFileName() + ":" + line + "  [" + what + "]" + tail;
    }

    private static int lineOf(String content, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
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
