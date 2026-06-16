package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Enforces the precompute contract: scripts surface FACTS / metrics / directions for the LLM to judge — they
 * NEVER emit the verdict the agent is supposed to reach independently (telescope, not cage). Observation-laundering
 * (a {@code directions} string that pre-decides POSITIVE / NEGATIVE / NOT_APPLICABLE) silently biases the grader
 * and has regressed twice; this test makes the boundary structural.
 */
class PrecomputeScriptPurityTest extends BaseUnitTest {

    private static final Path PRECOMPUTE_DIR = resolveDir(
        "src/main/resources/practices/precompute",
        "server/src/main/resources/practices/precompute"
    );

    // The sign-neutral verdict vocabulary (ADR 0021, F-6). A precompute script produces hints, never
    // verdicts, so it must launder none of these.
    private static final List<String> VERDICT_TOKENS = List.of("OBSERVED", "NOT_OBSERVED", "NOT_APPLICABLE");

    @Test
    @DisplayName("no precompute script emits a verdict token outside its header comments")
    void noScriptLaundersAVerdict() throws IOException {
        for (Path script : scripts()) {
            for (String line : Files.readAllLines(script, StandardCharsets.UTF_8)) {
                String trimmed = line.strip();
                // Header comments legitimately state the "facts not verdicts" contract — skip them.
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                    continue;
                }
                for (String token : VERDICT_TOKENS) {
                    assertThat(line)
                        .as(
                            "%s emits the verdict token '%s' in code/directions — precompute surfaces facts, " +
                                "the LLM decides the verdict",
                            script.getFileName(),
                            token
                        )
                        .doesNotContain(token);
                }
            }
        }
    }

    private static List<Path> scripts() throws IOException {
        try (Stream<Path> walk = Files.list(PRECOMPUTE_DIR)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(".ts")).toList();
        }
    }

    private static Path resolveDir(String moduleRelative, String repoRelative) {
        Path candidate = Path.of(moduleRelative);
        return Files.isDirectory(candidate) ? candidate : Path.of(repoRelative);
    }
}
