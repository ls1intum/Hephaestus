package de.tum.cit.aet.hephaestus.agent.handler.reflection;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The composition stage is additive, so this parser's contract is "never throw, and never let a
 * half-written message through". Both halves are asserted here.
 */
class ReflectionCompositionResultParserTest extends BaseUnitTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final ReflectionCompositionResultParser parser = new ReflectionCompositionResultParser();

    @Test
    void readsAWellFormedMessage() {
        List<ComposedReflectionMessage> messages = parser.parse(
            output(
                """
                { "messages": [ {
                  "practiceSlug": "ships-tests-with-the-change",
                  "title": "Tests are arriving one commit late",
                  "body": "On three of your last five changes the test landed a push later.",
                  "nextStep": "Write the assertion before the branch."
                } ] }
                """
            )
        );

        assertThat(messages)
            .singleElement()
            .satisfies(message -> {
                assertThat(message.practiceSlug()).isEqualTo("ships-tests-with-the-change");
                assertThat(message.title()).isEqualTo("Tests are arriving one commit late");
                assertThat(message.nextStep()).isEqualTo("Write the assertion before the branch.");
            });
    }

    /**
     * Every field is load-bearing: without a body there is nothing to read, and without a next step it
     * is a verdict rather than feedback. A message missing either is dropped rather than delivered half.
     */
    @ParameterizedTest
    @ValueSource(strings = { "practiceSlug", "title", "body", "nextStep" })
    void dropsAMessageMissingAnyOfItsFourParts(String omitted) {
        var message = objectMapper.createObjectNode();
        message.put("practiceSlug", "ships-tests-with-the-change");
        message.put("title", "A title");
        message.put("body", "A body");
        message.put("nextStep", "A next step");
        message.remove(omitted);
        var payload = objectMapper.createObjectNode();
        payload.putArray("messages").add(message);
        var jobOutput = objectMapper.createObjectNode();
        jobOutput.set("reflectionFeedback", payload);

        assertThat(parser.parse(jobOutput)).isEmpty();
    }

    /** Two messages about one habit read as two problems, so the second is dropped. */
    @Test
    void keepsOnlyTheFirstMessageForAPractice() {
        List<ComposedReflectionMessage> messages = parser.parse(
            output(
                """
                { "messages": [
                  { "practiceSlug": "p", "title": "First", "body": "b", "nextStep": "n" },
                  { "practiceSlug": "p", "title": "Second", "body": "b", "nextStep": "n" }
                ] }
                """
            )
        );

        assertThat(messages).singleElement().extracting(ComposedReflectionMessage::title).isEqualTo("First");
    }

    @Test
    void normalisesTheSlugTheSameWayTheFindingParserDoes() {
        List<ComposedReflectionMessage> messages = parser.parse(
            output(
                """
                { "messages": [ { "practiceSlug": "Ships_Tests", "title": "t", "body": "b", "nextStep": "n" } ] }
                """
            )
        );

        assertThat(messages)
            .singleElement()
            .extracting(ComposedReflectionMessage::practiceSlug)
            .isEqualTo("ships-tests");
    }

    /**
     * A review that measured correctly is a successful review whether or not anything was composed from
     * it, so every shape below is an empty list rather than a failure.
     */
    @Test
    void treatsAnyMalformedOrAbsentPayloadAsNothingComposed() {
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse(objectMapper.createObjectNode())).isEmpty();
        assertThat(parser.parse(output("{}"))).isEmpty();
        assertThat(parser.parse(output("{ \"messages\": \"not an array\" }"))).isEmpty();
        assertThat(parser.parse(output("{ \"messages\": [ 3, null, \"x\" ] }"))).isEmpty();
    }

    private JsonNode output(String reflectionFeedbackJson) {
        var jobOutput = objectMapper.createObjectNode();
        jobOutput.set("reflectionFeedback", objectMapper.readTree(reflectionFeedbackJson));
        return jobOutput;
    }
}
