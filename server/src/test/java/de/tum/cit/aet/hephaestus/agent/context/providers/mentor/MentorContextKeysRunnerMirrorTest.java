package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Drift guard for the prose contract in {@link MentorContextKeys}: the JS runner's hand-maintained
 * {@code FETCH_CONTEXT_ALLOWED} whitelist (pi-mentor-runner.mjs) must mirror the basenames of
 * {@link MentorContextKeys#ALLOWED_OUTPUT_KEYS}. Java is authoritative (MentorChatService re-checks),
 * so a divergence only weakens the runner's defense-in-depth — this test makes the mirror enforced.
 */
@Tag("unit")
class MentorContextKeysRunnerMirrorTest {

    private static final Path RUNNER = Path.of("src", "main", "resources", "agent", "pi-mentor-runner.mjs");
    private static final Pattern ALLOWED_BLOCK = Pattern.compile(
        "const FETCH_CONTEXT_ALLOWED = new Set\\(\\[(.*?)\\]\\);",
        Pattern.DOTALL
    );
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]+)\"");

    @Test
    @DisplayName("runner FETCH_CONTEXT_ALLOWED mirrors MentorContextKeys.ALLOWED_OUTPUT_KEYS basenames")
    void runnerWhitelistMirrorsJavaSource() throws IOException {
        String source = Files.readString(RUNNER, StandardCharsets.UTF_8);
        Matcher block = ALLOWED_BLOCK.matcher(source);
        assertThat(block.find()).as("FETCH_CONTEXT_ALLOWED block present in pi-mentor-runner.mjs").isTrue();

        Set<String> jsKeys = STRING_LITERAL.matcher(block.group(1))
            .results()
            .map(m -> m.group(1))
            .collect(Collectors.toSet());

        Set<String> javaBasenames = MentorContextKeys.ALLOWED_OUTPUT_KEYS.stream()
            .map(key -> key.substring(key.lastIndexOf('/') + 1))
            .collect(Collectors.toSet());

        assertThat(jsKeys)
            .as("runner JS whitelist must equal the Java context output-key basenames")
            .isEqualTo(javaBasenames);
    }
}
