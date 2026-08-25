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
import org.junit.jupiter.api.Test;

class AgentVocabularySyncTest extends BaseUnitTest {

    private static final Path NORMALIZER = resolveResource("agent/pi-observation-normalize.ts");
    private static final Path RUNNER = resolveResource("agent/pi-runner.ts");
    private static final Path ORCHESTRATOR = resolveResource("agent/pi-orchestrator.md");

    @Test
    void presenceVocabularyMatches() throws IOException {
        assertThat(jsArray("PRESENCE_VALUES"))
            .as("PRESENCE_VALUES in pi-observation-normalize.ts vs Presence.values()")
            .containsExactlyInAnyOrderElementsOf(names(Presence.values()));
    }

    @Test
    void assessmentVocabularyMatches() throws IOException {
        assertThat(jsArray("ASSESSMENT_VALUES"))
            .as("ASSESSMENT_VALUES in pi-observation-normalize.ts vs Assessment.values()")
            .containsExactlyInAnyOrderElementsOf(names(Assessment.values()));
    }

    @Test
    void severityVocabularyMatches() throws IOException {
        assertThat(jsArray("SEVERITY_VALUES"))
            .as("SEVERITY_VALUES in pi-observation-normalize.ts vs Severity.values()")
            .containsExactlyInAnyOrderElementsOf(names(Severity.values()));
    }

    @Test
    void carriesValenceAgrees() throws IOException {
        String body = Files.readString(NORMALIZER, StandardCharsets.UTF_8);
        Matcher fn = Pattern.compile(
            // Tolerates the parameter and return annotations the TypeScript runtime carries; the body,
            // which is what this test reads the valenced presences out of, is captured either way.
            "export function carriesValence\\([^)]*\\)[^{]*\\{(.*?)\\n\\}",
            Pattern.DOTALL
        ).matcher(body);
        assertThat(fn.find()).as("carriesValence() is declared in pi-observation-normalize.ts").isTrue();

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
    void runnerOffersTheFusedOutcomeVocabulary() throws IOException {
        String body = Files.readString(RUNNER, StandardCharsets.UTF_8);

        assertThat(body)
            .as("pi-runner.ts offers the fused outcome vocabulary the parser resolves against")
            .contains("\"BEHAVIOR_PRESENT_GOOD\"")
            .contains("\"BEHAVIOR_ABSENT_BAD_MAJOR\"")
            .contains("\"NO_REVIEW_OCCASION\"")
            .contains("\"INSUFFICIENT_EVIDENCE\"");
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
            .filter(
                component -> component.getAnnotatedType().getAnnotation(org.jspecify.annotations.Nullable.class) == null
            )
            .map(component -> component.getName())
            .toList();
        String required =
            "required: [" +
            requiredFields
                .stream()
                .map(field -> "\"" + field + "\"")
                .collect(java.util.stream.Collectors.joining(", ")) +
            "]";

        String runner = Files.readString(RUNNER, StandardCharsets.UTF_8);
        assertThat(runner).as("pi-runner.ts conversation-note schema vs ConversationBrief").contains(required);
        assertThat(javaFields)
            .as("every note field the record carries is declared in the runner schema")
            .allSatisfy(field -> assertThat(runner).contains(field + ": {"));
        assertThat(Files.readString(resolveResource("agent/feedback-composer.md"), StandardCharsets.UTF_8))
            .as("feedback-composer.md conversation-note shape vs ConversationBrief")
            .contains("notes: { " + String.join(", ", javaFields) + " }");
    }

    @Test
    void toolPhasesAreGated() throws IOException {
        String body = Files.readString(RUNNER, StandardCharsets.UTF_8);

        assertThat(body)
            .contains("if (measurementClosed)")
            .contains("measurementClosed = true")
            .contains("if (!compositionAdmitted)")
            .contains("compositionAdmitted = true");
    }

    @Test
    void orchestratorPromptCoversEveryOutcome() throws IOException {
        String body = Files.readString(ORCHESTRATOR, StandardCharsets.UTF_8);
        assertThat(body)
            .contains("BEHAVIOR_PRESENT_")
            .contains("BEHAVIOR_ABSENT_")
            .contains("NO_REVIEW_OCCASION")
            .contains("INSUFFICIENT_EVIDENCE")
            .doesNotContain("NOT_APPLICABLE")
            .doesNotContain("INCONCLUSIVE");
    }

    private static List<String> names(Enum<?>[] values) {
        return Stream.of(values).map(Enum::name).toList();
    }

    private static Set<String> jsArray(String constantName) throws IOException {
        String body = Files.readString(NORMALIZER, StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile(
            "export const " + Pattern.quote(constantName) + "\\s*=\\s*\\[(.*?)]",
            Pattern.DOTALL
        ).matcher(body);
        assertThat(matcher.find()).as("%s is declared in pi-observation-normalize.ts", constantName).isTrue();
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
