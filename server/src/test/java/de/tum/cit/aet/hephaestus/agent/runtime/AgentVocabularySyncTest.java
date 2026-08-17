package de.tum.cit.aet.hephaestus.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.handler.composition.ComposedFeedbackUnit;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cross-language vocabulary sync. Every enum that crosses the sandbox boundary is written twice — once
 * as a Java enum the server persists, once as a JavaScript literal the in-sandbox runner validates
 * against — and nothing but this test holds the two spellings together.
 *
 * <p>A value the server accepts but the runner rejects gets refiled by the model under whatever value the
 * runner does accept — silently turning one presence into another in a permanent record of how a person
 * works. That failure mode is silent by construction, so the guard has to be structural: a value added on
 * one side and not the other fails here, in the same change.
 */
class AgentVocabularySyncTest extends BaseUnitTest {

    private static final Path NORMALIZER = resolveResource("agent/pi-observation-normalize.mjs");
    private static final Path RUNNER = resolveResource("agent/pi-runner.mjs");
    private static final Path ORCHESTRATOR = resolveResource("agent/pi-orchestrator.md");

    @Test
    @DisplayName("the runner's presence vocabulary is exactly Presence.values()")
    void presenceVocabularyMatches() throws IOException {
        assertThat(jsArray("PRESENCE_VALUES"))
            .as("PRESENCE_VALUES in pi-observation-normalize.mjs vs Presence.values()")
            .containsExactlyInAnyOrderElementsOf(names(Presence.values()));
    }

    @Test
    @DisplayName("the runner's assessment vocabulary is exactly Assessment.values()")
    void assessmentVocabularyMatches() throws IOException {
        assertThat(jsArray("ASSESSMENT_VALUES"))
            .as("ASSESSMENT_VALUES in pi-observation-normalize.mjs vs Assessment.values()")
            .containsExactlyInAnyOrderElementsOf(names(Assessment.values()));
    }

    @Test
    @DisplayName("the runner's severity vocabulary is exactly Severity.values()")
    void severityVocabularyMatches() throws IOException {
        assertThat(jsArray("SEVERITY_VALUES"))
            .as("SEVERITY_VALUES in pi-observation-normalize.mjs vs Severity.values()")
            .containsExactlyInAnyOrderElementsOf(names(Severity.values()));
    }

    @Test
    @DisplayName("the runner's carriesValence() decides the same presences as Presence.carriesValence()")
    void carriesValenceAgrees() throws IOException {
        // Not merely which values exist, but which of them demand an assessment. Disagreement here is
        // the same class of bug one level down: the runner would reject a valence-free presence for
        // lacking an assessment the server would have nulled anyway, and the model would refile it as
        // whatever the runner does accept.
        String body = Files.readString(NORMALIZER, StandardCharsets.UTF_8);
        Matcher fn = Pattern.compile(
            "export function carriesValence\\(presence\\) \\{(.*?)\\n\\}",
            Pattern.DOTALL
        ).matcher(body);
        assertThat(fn.find()).as("carriesValence() is declared in pi-observation-normalize.mjs").isTrue();

        Set<String> jsValenced = quotedStrings(fn.group(1));
        Set<String> javaValenced = Arrays.stream(Presence.values())
            .filter(Presence::carriesValence)
            .map(Enum::name)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        assertThat(jsValenced)
            .as("presences the JS carriesValence() accepts vs Presence::carriesValence")
            .containsExactlyInAnyOrderElementsOf(javaValenced);
    }

    @Test
    @DisplayName("the runner single-sources its vocabularies instead of re-declaring them")
    void runnerImportsTheVocabulary() throws IOException {
        // The tool schema the model sees and the normalizer that validates the result must be generated
        // from ONE list, or a value rejected by one and not the other is invisible from either file alone.
        String body = Files.readString(RUNNER, StandardCharsets.UTF_8);

        assertThat(body)
            .as("pi-runner.mjs imports the shared vocabularies from pi-observation-normalize.mjs")
            .contains("PRESENCE_VALUES")
            .contains("ASSESSMENT_VALUES")
            .contains("SEVERITY_VALUES");

        assertThat(body)
            .as("the final tool exposes only the assessed occurrence subset, not persisted refusal values")
            .contains("occurrence: { type: \"string\", enum: [\"PRESENT\", \"ABSENT\"] }")
            .doesNotContain("occurrence: { type: \"string\", enum: PRESENCE_VALUES }");
    }

    @Test
    @DisplayName("the runner and composer prompt use exactly the server's conversation-note fields")
    void conversationNoteShapeMatches() throws IOException {
        List<String> javaFields = Arrays.stream(ComposedFeedbackUnit.ConversationBrief.class.getRecordComponents())
            .map(component -> component.getName())
            .toList();
        String required =
            "required: [" +
            javaFields
                .stream()
                .map(field -> "\"" + field + "\"")
                .collect(java.util.stream.Collectors.joining(", ")) +
            "]";

        assertThat(Files.readString(RUNNER, StandardCharsets.UTF_8))
            .as("pi-runner.mjs conversation-note schema vs ConversationBrief")
            .contains(required);
        assertThat(Files.readString(resolveResource("agent/feedback-composer.md"), StandardCharsets.UTF_8))
            .as("feedback-composer.md conversation-note shape vs ConversationBrief")
            .contains("notes: { " + String.join(", ", javaFields) + " }");
    }

    @Test
    @DisplayName("the orchestrator teaches the final outcome union rather than persisted refusal names")
    void orchestratorPromptCoversEveryOutcome() throws IOException {
        String body = Files.readString(ORCHESTRATOR, StandardCharsets.UTF_8);
        assertThat(body)
            .contains("ASSESSED")
            .contains("DECLINED")
            .contains("NO_REVIEW_OCCASION")
            .contains("INSUFFICIENT_EVIDENCE")
            .doesNotContain("NOT_APPLICABLE")
            .doesNotContain("INCONCLUSIVE");
    }

    private static List<String> names(Enum<?>[] values) {
        return Stream.of(values).map(Enum::name).toList();
    }

    /** Extracts the quoted members of {@code export const <name> = [...]} from the normalizer module. */
    private static Set<String> jsArray(String constantName) throws IOException {
        String body = Files.readString(NORMALIZER, StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile(
            "export const " + Pattern.quote(constantName) + "\\s*=\\s*\\[(.*?)]",
            Pattern.DOTALL
        ).matcher(body);
        assertThat(matcher.find()).as("%s is declared in pi-observation-normalize.mjs", constantName).isTrue();
        Set<String> values = quotedStrings(matcher.group(1));
        assertThat(values).as("%s is not empty", constantName).isNotEmpty();
        return values;
    }

    private static Set<String> quotedStrings(String source) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("\"([A-Z_]+)\"").matcher(source);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private static Path resolveResource(String relativePath) {
        Path candidate = Path.of("src/main/resources").resolve(relativePath);
        return Files.exists(candidate) ? candidate : Path.of("server/src/main/resources").resolve(relativePath);
    }
}
