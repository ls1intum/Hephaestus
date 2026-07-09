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
 * {@code FETCH_CONTEXT_ALLOWED} whitelist (pi-mentor-runner.mjs) must mirror
 * {@link MentorContextKeys#ALLOWED_OUTPUT_KEYS}. Java is authoritative (MentorChatService re-checks),
 * so a divergence only weakens the runner's defense-in-depth — this test makes the mirror enforced.
 */
@Tag("unit")
class MentorContextKeysRunnerMirrorTest {

    private static final Path RUNNER = Path.of("src", "main", "resources", "agent", "pi-mentor-runner.mjs");
    private static final Path SYSTEM_PROMPT = Path.of("src", "main", "resources", "agent", "mentor", "system.md");
    private static final Pattern ALLOWED_BLOCK = Pattern.compile(
        "const FETCH_CONTEXT_ALLOWED = new Set\\(\\[(.*?)\\]\\);",
        Pattern.DOTALL
    );
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]+)\"");

    @Test
    @DisplayName("runner FETCH_CONTEXT_ALLOWED mirrors MentorContextKeys.ALLOWED_OUTPUT_KEYS")
    void runnerWhitelistMirrorsJavaSource() throws IOException {
        String source = Files.readString(RUNNER, StandardCharsets.UTF_8);
        Matcher block = ALLOWED_BLOCK.matcher(source);
        assertThat(block.find()).as("FETCH_CONTEXT_ALLOWED block present in pi-mentor-runner.mjs").isTrue();

        Set<String> jsKeys = STRING_LITERAL.matcher(block.group(1))
            .results()
            .map(m -> m.group(1))
            .collect(Collectors.toSet());

        assertThat(jsKeys)
            .as("runner JS whitelist must equal the Java context output keys")
            .isEqualTo(MentorContextKeys.ALLOWED_OUTPUT_KEYS);
    }

    @Test
    @DisplayName("system prompt lists every mentor context file path")
    void systemPromptListsContextBasenames() throws IOException {
        String prompt = Files.readString(SYSTEM_PROMPT, StandardCharsets.UTF_8);
        String perTurnInputSection = prompt.substring(
            prompt.indexOf("## Per-turn input"),
            prompt.indexOf("### Reading `inputs/context/practice_standing.json`")
        );

        assertThat(MentorContextKeys.ALLOWED_OUTPUT_KEYS).allSatisfy(key ->
            assertThat(perTurnInputSection)
                .as("system prompt should document context file %s in the per-turn input list", key)
                .contains("`" + key + "`")
        );
    }

    @Test
    @DisplayName("system prompt uses full context paths as the canonical mentor context contract")
    void systemPromptUsesCanonicalContextPaths() throws IOException {
        String prompt = Files.readString(SYSTEM_PROMPT, StandardCharsets.UTF_8);

        assertThat(prompt)
            .contains("`fetch_context` using the full canonical path")
            .contains("call `fetch_context`\nwith `inputs/context/recent_authored_work.json`")
            .contains("first fetch `inputs/context/prepared_conversation_feedback.json`")
            .contains("fetch\n`inputs/context/slack_conversations.json`")
            .contains("`inputs/context/current_thread_history.json`")
            .contains("not `recent_authored_work.json` or `inputs/recent_authored_work.json`")
            .doesNotContain("exact basename")
            .doesNotContain("`read` / `grep` / `bash`");
    }

    @Test
    @DisplayName("runner exposes only mentor-specific tools and requires canonical context paths")
    void runnerUsesLeastPrivilegeContextToolSurface() throws IOException {
        String source = Files.readString(RUNNER, StandardCharsets.UTF_8);

        assertThat(source)
            .contains("tools: [\"fetch_context\", \"link_finding\"]")
            .contains("\"inputs/context/recent_authored_work.json\"")
            .doesNotContain("tools: [\"fetch_context\", \"link_finding\", \"read\", \"bash\", \"grep\"]");
    }
}
