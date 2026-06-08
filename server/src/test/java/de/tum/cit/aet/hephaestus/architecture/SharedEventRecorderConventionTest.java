package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Durable guard against Spring test-context fragmentation from per-class event listeners.
 *
 * <p><b>Why this rule exists.</b> Each integration test that declared its own
 * {@code @Component} class with {@code @EventListener} methods (imported per class) produced a
 * <em>distinct</em> Spring {@code ApplicationContext} cache key, forcing a fresh, Testcontainers-backed
 * context boot (~10–36s) for every such class. Nine handler/processor tests did this — ~9 of the
 * suite's ~20 context boots. They now share {@link de.tum.cit.aet.hephaestus.testconfig.RecordingScmEventListener},
 * imported once by {@code BaseIntegrationTest}, so they reuse one context. This rule keeps it that way:
 * record SCM / GitHub-project domain events via the shared recorder, never a test-local listener.
 *
 * <p>Source scan (same approach as {@link TestTierTagCompletenessTest}); the shared recorder itself is
 * the only allowed declaration site.
 */
@Tag("architecture")
class SharedEventRecorderConventionTest {

    private static final String ALLOWED = "RecordingScmEventListener.java";

    /** {@code @EventListener} on a method whose parameter is an SCM / GitHub-project domain event. */
    private static final Pattern PER_CLASS_LISTENER = Pattern.compile(
        "@EventListener\\b[\\s\\S]{0,200}?\\((?:final\\s+)?(?:ScmDomainEvent|GitHubProjectEvent)\\.",
        Pattern.DOTALL
    );

    @Test
    void scmDomainEventsAreRecordedViaTheSharedRecorderNotPerClassListeners() {
        Path testRoot = locateTestRoot();
        Set<String> violations = new TreeSet<>();

        try (Stream<Path> sources = Files.walk(testRoot)) {
            sources
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.getFileName().toString().equals(ALLOWED))
                .forEach(p -> {
                    String content;
                    try {
                        content = Files.readString(p);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    if (PER_CLASS_LISTENER.matcher(content).find()) {
                        violations.add("  " + p.getFileName());
                    }
                });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertThat(violations)
            .as(
                "These tests declare a per-class @EventListener for SCM / GitHub-project domain events, " +
                    "which forks a fresh Spring context (a Testcontainers boot) per class. Record via the " +
                    "shared RecordingScmEventListener (imported by BaseIntegrationTest) instead — " +
                    "recorder.ofType(SomeEvent.class):\n" +
                    String.join("\n", violations)
            )
            .isEmpty();
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
