package de.tum.cit.aet.hephaestus.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.handler.ReviewResultParser;
import de.tum.cit.aet.hephaestus.agent.handler.composition.ComposedFeedbackUnit;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class AgentVocabularySyncTest extends BaseUnitTest {

    private static final Path CONTRACT = resolveRepoFile("docker/agents/practice-scripts/lib/contract.ts");
    private static final Path RUNNER = resolveResource("agent/pi-runner.ts");
    private static final Path ORCHESTRATOR = resolveResource("agent/pi-orchestrator.md");

    @Test
    void shouldBoundSummariesAlikeInRunnerAndAdmission() throws IOException {
        Matcher matcher =
                Pattern.compile("\\bsummaryChars: (\\d+),").matcher(Files.readString(CONTRACT, StandardCharsets.UTF_8));
        assertThat(matcher.find())
                .as("CANDIDATE_LIMITS.summaryChars is declared in lib/contract.ts")
                .isTrue();
        assertThat(Integer.parseInt(matcher.group(1)))
                .as("CANDIDATE_LIMITS.summaryChars vs ReviewResultParser.MAX_SUMMARY_LENGTH")
                .isEqualTo(ReviewResultParser.MAX_SUMMARY_LENGTH);
    }

    @Test
    void shouldKeepConversationNoteShapeInSync() throws IOException {
        List<String> javaFields = Arrays.stream(ComposedFeedbackUnit.ConversationBrief.class.getRecordComponents())
                .map(component -> component.getName())
                .toList();
        // A component the record allows to be null is a field the runner may omit, so it belongs in the
        // schema's properties but not in its required list. Keeping it out of both would let the two
        // vocabularies drift apart silently, which is the whole point of this test.
        List<String> requiredFields = Arrays.stream(ComposedFeedbackUnit.ConversationBrief.class.getRecordComponents())
                .filter(component -> component.getAnnotatedType().getAnnotation(Nullable.class) == null)
                .map(component -> component.getName())
                .toList();
        String required = "required: ["
                + requiredFields.stream().map(field -> "\"" + field + "\"").collect(Collectors.joining(", "))
                + "]";

        String runner = Files.readString(RUNNER, StandardCharsets.UTF_8);
        assertThat(runner)
                .as("pi-runner.ts conversation-note schema vs ConversationBrief")
                .contains(required);
        assertThat(javaFields)
                .as("every note field the record carries is declared in the runner schema")
                .allSatisfy(field -> assertThat(runner).contains(field + ": {"));
        assertThat(Files.readString(resolveResource("agent/feedback-composer.md"), StandardCharsets.UTF_8))
                .as("feedback-composer.md conversation-note shape vs ConversationBrief")
                .contains("notes: { " + String.join(", ", javaFields) + " }");
    }

    @Test
    void orchestratorPromptCoversEveryOutcome() throws IOException {
        String body = Files.readString(ORCHESTRATOR, StandardCharsets.UTF_8);
        assertThat(body)
                .contains("MET", "NOT_MET", "NOT_APPLICABLE", "UNDETERMINED")
                .doesNotContain("BEHAVIOR_PRESENT_", "NO_REVIEW_OCCASION", "INSUFFICIENT_EVIDENCE", "INCONCLUSIVE");
    }

    @Test
    void shouldKeepRetiredPracticeWordsOutOfGenerationInstructions() throws IOException {
        // docs/contributor/practice-feedback-language.md retires this word for practices and what recurs.
        Pattern retired = Pattern.compile("\\bhabits?\\b", Pattern.CASE_INSENSITIVE);
        List<Path> instructions;
        try (Stream<Path> agent = Files.walk(resolveResource("agent"))) {
            instructions = Stream.concat(
                            agent.filter(Files::isRegularFile),
                            Stream.of(resolveResource("practices/default-catalog.json")))
                    .toList();
        }
        assertThat(instructions)
                .isNotEmpty()
                .allSatisfy(file -> assertThat(retired.matcher(Files.readString(file, StandardCharsets.UTF_8))
                                .find())
                        .as("%s uses a retired practice word", file)
                        .isFalse());
    }

    private static Path resolveRepoFile(String relativePath) {
        Path candidate = Path.of("..", "..").resolve(relativePath);
        return Files.exists(candidate) ? candidate : Path.of(relativePath);
    }

    private static Path resolveResource(String relativePath) {
        Path candidate = Path.of("src/main/resources").resolve(relativePath);
        return Files.exists(candidate)
                ? candidate
                : Path.of("server/application/src/main/resources").resolve(relativePath);
    }
}
