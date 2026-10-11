package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Enforces the practice script contract: scripts surface FACTS / metrics / directions for the LLM to judge — they
 * NEVER emit the outcome the agent is supposed to reach independently (telescope, not cage). A {@code directions}
 * string that pre-decides an {@link Outcome} silently biases the reviewer; this test makes the boundary structural.
 */
class PracticeScriptPurityTest extends BaseUnitTest {

    private static final Path PRACTICE_SCRIPT_DIR = resolveDir(
            "src/main/resources/practices/scripts", "server/application/src/main/resources/practices/scripts");

    // Every observation outcome. A practice script produces leads, never observations, so it must launder none
    // of these. The match is anchored on identifier characters so the reported token is accurate: a plain
    // substring check would report "MET" for a "NOT_MET" leak.
    private static final List<String> OBSERVATION_TOKENS =
            Arrays.stream(Outcome.values()).map(Outcome::name).toList();

    @Test
    @DisplayName("no practice script emits an observation outcome outside its header comments")
    void noScriptLaundersAnObservation() throws IOException {
        for (Path script : scripts()) {
            for (String line : Files.readAllLines(script, StandardCharsets.UTF_8)) {
                String trimmed = line.strip();
                // Header comments legitimately state the "facts not observations" contract — skip them.
                // Note: this exempts full-line comments only (lines starting with // * /*); a trailing inline
                // comment is deliberately NOT exempt, so even a commented-out observation token in code is
                // flagged — a conservative stance that keeps the firewall structural.
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                    continue;
                }
                for (String token : OBSERVATION_TOKENS) {
                    // \b would treat the leading "_" / "." as a boundary, so anchor on the surrounding chars
                    // explicitly: the token must not be flanked by another identifier character.
                    boolean leaks = Pattern.compile("(?<![A-Z_])" + token + "(?![A-Z_])")
                            .matcher(line)
                            .find();
                    assertThat(leaks)
                            .as(
                                    "%s emits the outcome '%s' in code/directions — a practice script surfaces facts, "
                                            + "the LLM decides the outcome",
                                    script.getFileName(), token)
                            .isFalse();
                }
            }
        }
    }

    private static List<Path> scripts() throws IOException {
        try (Stream<Path> walk = Files.list(PRACTICE_SCRIPT_DIR)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(".ts")).toList();
        }
    }

    private static Path resolveDir(String moduleRelative, String repoRelative) {
        Path candidate = Path.of(moduleRelative);
        return Files.isDirectory(candidate) ? candidate : Path.of(repoRelative);
    }
}
