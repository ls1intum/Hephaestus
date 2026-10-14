package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Drift guard for the prose contract in {@link MentorContextKeys}: the JS runner's hand-maintained
 * {@code FETCH_CONTEXT_ALLOWED} whitelist (pi-mentor-protocol.ts) must mirror
 * {@link MentorContextKeys#ALLOWED_OUTPUT_KEYS}. Java is authoritative (MentorChatService re-checks),
 * so a divergence only weakens the runner's defense-in-depth — this test makes the mirror enforced.
 */
@Tag("unit")
class MentorContextKeysRunnerMirrorTest {

    private static final Path PROTOCOL = Path.of("src", "main", "resources", "agent", "pi-mentor-protocol.ts");
    private static final Pattern ALLOWED_BLOCK =
            Pattern.compile("const FETCH_CONTEXT_ALLOWED = new Set\\(\\[(.*?)\\]\\);", Pattern.DOTALL);
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]+)\"");

    @Test
    @DisplayName("runner FETCH_CONTEXT_ALLOWED mirrors MentorContextKeys.ALLOWED_OUTPUT_KEYS")
    void runnerWhitelistMirrorsJavaSource() throws IOException {
        String source = Files.readString(PROTOCOL, StandardCharsets.UTF_8);
        Matcher block = ALLOWED_BLOCK.matcher(source);
        assertThat(block.find())
                .as("FETCH_CONTEXT_ALLOWED block present in pi-mentor-protocol.ts")
                .isTrue();

        Set<String> jsKeys = STRING_LITERAL
                .matcher(block.group(1))
                .results()
                .map(m -> m.group(1))
                .collect(Collectors.toSet());

        assertThat(jsKeys)
                .as("runner JS whitelist must equal the Java context output keys")
                .isEqualTo(MentorContextKeys.ALLOWED_OUTPUT_KEYS);
    }

    @Test
    @DisplayName("a result detail key is one canonical lowercase id, mirrored by the runner")
    void practiceResultDetailKeyIsCanonical() throws IOException {
        String id = "0b7e1c9a-3f5d-4a8e-9c21-6d4f8e2a1b3c";
        assertThat(ResultHistoryContentSource.resultIdOf("inputs/context/results_history/" + id + ".json"))
                .contains(UUID.fromString(id));
        assertThat(ResultHistoryContentSource.resultIdOf(
                        "inputs/context/results_history/" + id.toUpperCase(Locale.ROOT) + ".json"))
                .isEmpty();
        assertThat(ResultHistoryContentSource.resultIdOf("inputs/context/results_history/../user.json"))
                .isEmpty();
        assertThat(ResultHistoryContentSource.resultIdOf("inputs/context/results_history/42.json"))
                .isEmpty();
    }
}
