package de.tum.cit.aet.hephaestus.agent.handler.composition;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The boundary a composed payload has to cross before anybody reads it.
 *
 * <p>The composition stage is additive, so the parser's first contract is "never throw". Its second is
 * the one that matters here: the payload arrives from inside the sandbox, next to the model, so nothing
 * in it is trusted. A unit that names a supersession target nobody staged, or an anchor pointing at a
 * citation that does not exist, or a line that is not in this change, is a unit that would put words on
 * a surface they do not belong on — each is refused, and each refusal is a case below.
 */
class FeedbackCompositionResultParserTest extends BaseUnitTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final FeedbackCompositionResultParser parser = new FeedbackCompositionResultParser();

    private static final String OBSERVATIONS = """
        [
          { "id": "obs-0", "practiceSlug": "ships-tests-with-the-change", "assessment": "BAD",
            "severity": "MAJOR", "anchorable": true,
            "citations": [
              { "index": 0, "sourceKind": "scm.pull-request.diff", "path": "src/billing/InvoiceTotals.java",
                "side": "NEW", "startLine": 47, "endLine": 47, "anchorable": true }
            ] },
          { "id": "obs-1", "practiceSlug": "keeps-the-thread-moving", "assessment": "BAD",
            "severity": "MINOR", "anchorable": false,
            "citations": [
              { "index": 0, "sourceKind": "scm.review-threads", "path": "thread/9",
                "side": null, "startLine": 1, "endLine": null, "anchorable": false }
            ] }
        ]
        """;

    @Test
    void readsAnInContextUnitAndResolvesItsAnchorFromTheCitation() {
        List<ComposedFeedbackUnit> units = parser.parse(
            output(
                """
                { "channel": "IN_CONTEXT", "practiceSlug": "ships-tests-with-the-change",
                  "basedOn": ["obs-0"], "action": "NEW",
                  "title": "This branch is untested",
                  "nextStep": "Add a case that calls total with a tax-exempt customer.",
                  "placement": { "kind": "DIFF", "observationId": "obs-0", "citationIndex": 0 } }
                """,
                "[]"
            )
        );

        assertThat(units)
            .singleElement()
            .satisfies(unit -> {
                assertThat(unit.channel()).isEqualTo(FeedbackChannel.IN_CONTEXT);
                assertThat(unit.basedOn()).containsExactly("obs-0");
                assertThat(unit.placement().diffAnchor()).isNotNull();
                assertThat(unit.placement().diffAnchor().path()).isEqualTo("src/billing/InvoiceTotals.java");
                assertThat(unit.placement().diffAnchor().side()).isEqualTo("NEW");
                assertThat(unit.placement().diffAnchor().startLine()).isEqualTo(47);
            });
    }

    @Test
    void readsAnArtifactPlacedInContextUnitWithoutInventingCoordinates() {
        List<ComposedFeedbackUnit> units = parser.parse(
            output(
                """
                { "channel": "IN_CONTEXT", "practiceSlug": "ships-tests-with-the-change",
                  "basedOn": ["obs-0"], "action": "NEW",
                  "title": "The decision is not yet explained",
                  "nextStep": "Add the constraint that makes this option necessary.",
                  "placement": { "kind": "ARTIFACT" } }
                """,
                "[]"
            )
        );

        assertThat(units)
            .singleElement()
            .satisfies(unit -> {
                assertThat(unit.placement().kind()).isEqualTo(
                    ComposedFeedbackUnit.InContextPlacement.PlacementKind.ARTIFACT
                );
                assertThat(unit.placement().diffAnchor()).isNull();
            });
    }

    @Test
    void rejectsCoordinatesOnAnArtifactPlacement() {
        assertThat(
            parser.parse(
                output(
                    """
                    { "channel": "IN_CONTEXT", "practiceSlug": "ships-tests-with-the-change",
                      "basedOn": ["obs-0"], "action": "NEW",
                      "title": "The decision is not yet explained", "nextStep": "Add the constraint.",
                      "placement": { "kind": "ARTIFACT", "observationId": "obs-0", "citationIndex": 0 } }
                    """,
                    "[]"
                )
            )
        ).isEmpty();
    }

    @Test
    void readsAConversationUnitAsNotesRatherThanAScript() {
        List<ComposedFeedbackUnit> units = parser.parse(
            output(
                """
                { "channel": "IN_CHAT", "practiceSlug": "ships-tests-with-the-change",
                  "basedOn": ["prior:ships-tests-with-the-change"], "action": "NEW",
                  "title": "Tests arrive after review",
                  "notes": {
                    "situation": "On !18, !20 and !22 the test landed a push after the review comment.",
                    "capability": "Writing the test last is what leaves the review to find the gap.",
                    "evidenceSummary": "On the last three changes the test arrived a push later.",
                    "inConversationSignal": "They name a check they could run before pushing."
                  } }
                """,
                "[]"
            )
        );

        assertThat(units)
            .singleElement()
            .satisfies(unit -> {
                assertThat(unit.body()).isNull();
                assertThat(unit.notes()).isNotNull();
                assertThat(unit.notes().situation()).isEqualTo(
                    "On !18, !20 and !22 the test landed a push after the review comment."
                );
                assertThat(unit.notes().inConversationSignal()).isEqualTo(
                    "They name a check they could run before pushing."
                );
            });
    }

    /**
     * Each note refuses a different degradation of the turn, so three of four is not a smaller note - it is a
     * turn missing the part that makes it coaching. The one dropped here is the coaching goal, without which
     * the mentor cannot tell what useful movement the conversation is meant to support.
     *
     * <p>The absent case and the blank case are both here because they are not the same code path: the
     * sanitiser answers {@code ""} for a field that was never present, so a missing note arrives looking
     * exactly like an empty one, and a check for null alone lets both through.
     */
    @ParameterizedTest
    @ValueSource(
        strings = {
            """
            "situation": "On !18 the test landed a push after the review comment.",
            "evidenceSummary": "On the last three changes the test arrived a push later.",
            "inConversationSignal": "They name a check they could run before pushing."
            """,
            """
            "situation": "On !18 the test landed a push after the review comment.",
            "capability": "   ",
            "evidenceSummary": "On the last three changes the test arrived a push later.",
            "inConversationSignal": "They name a check they could run before pushing."
            """,
        }
    )
    void refusesAConversationUnitMissingOneOfItsNotes(String notes) {
        assertThat(
            parser.parse(
                output(
                    """
                    { "channel": "IN_CHAT", "practiceSlug": "ships-tests-with-the-change",
                      "basedOn": ["prior:ships-tests-with-the-change"], "action": "NEW",
                      "title": "Tests arrive after review",
                      "notes": { %s } }
                    """.formatted(notes),
                    "[]"
                )
            )
        ).isEmpty();
    }

    @Test
    void refusesAnOverlongNoteInsteadOfPersistingAnArbitrarilyTruncatedPlan() {
        String oversized = "x".repeat(ComposedFeedbackUnit.MAX_AIM_LENGTH + 1);
        assertThat(
            parser.parse(
                output(
                    """
                    { "channel": "IN_CHAT", "practiceSlug": "ships-tests-with-the-change",
                      "basedOn": ["prior:ships-tests-with-the-change"], "action": "NEW",
                      "title": "Tests arrive after review",
                      "notes": {
                        "situation": "Tests repeatedly arrived after review.",
                        "capability": "%s",
                        "evidenceSummary": "Three merge requests show the sequence.",
                        "inConversationSignal": "The developer can name when the test belongs."
                      } }
                    """.formatted(oversized),
                    "[]"
                )
            )
        ).isEmpty();
    }

    /**
     * A supersession target the composer was never shown is one it invented, and acting on it would let a
     * model retire a message it cannot have read.
     */
    @Test
    void refusesAUnitNamingAThreadKeyThatWasNeverStaged() {
        String unit = """
            { "channel": "IN_APP", "practiceSlug": "ships-tests-with-the-change",
              "basedOn": ["obs-0"], "action": "SUPERSEDE",
              "supersedesThreadKey": "invented-key",
              "title": "A habit", "body": "A body", "nextStep": "A next step" }
            """;

        assertThat(parser.parse(output(unit, "[]"))).isEmpty();
        assertThat(parser.parse(output(unit, "[\"invented-key\"]"))).hasSize(1);
    }

    @Test
    void refusesAnAnchorPointingAtACitationTheObservationDoesNotHave() {
        assertThat(
            parser.parse(
                output(
                    """
                    { "channel": "IN_CONTEXT", "practiceSlug": "ships-tests-with-the-change",
                      "basedOn": ["obs-0"], "action": "NEW",
                      "title": "t", "nextStep": "n",
                      "placement": { "kind": "DIFF", "observationId": "obs-0", "citationIndex": 4 } }
                    """,
                    "[]"
                )
            )
        ).isEmpty();
    }

    /**
     * The observation is true; it is simply not on this change. That routes it to another surface, and it
     * must never put a note on a line the diff does not contain.
     */
    @Test
    void refusesAnInContextUnitAnchoredToAnObservationThatIsNotOnTheDiff() {
        assertThat(
            parser.parse(
                output(
                    """
                    { "channel": "IN_CONTEXT", "practiceSlug": "keeps-the-thread-moving",
                      "basedOn": ["obs-1"], "action": "NEW",
                      "title": "t", "nextStep": "n",
                      "placement": { "kind": "DIFF", "observationId": "obs-1", "citationIndex": 0 } }
                    """,
                    "[]"
                )
            )
        ).isEmpty();
    }

    @Test
    void refusesAnInContextUnitWithNoAnchorAtAll() {
        assertThat(
            parser.parse(
                output(
                    """
                    { "channel": "IN_CONTEXT", "practiceSlug": "ships-tests-with-the-change",
                      "basedOn": ["obs-0"], "action": "NEW",
                      "title": "t", "nextStep": "n" }
                    """,
                    "[]"
                )
            )
        ).isEmpty();
    }

    /** The longitudinal surfaces are not on the diff, so a unit that thinks it is anchored is misaddressed. */
    @Test
    void refusesAnAnchorOnALongitudinalLane() {
        assertThat(
            parser.parse(
                output(
                    """
                    { "channel": "IN_APP", "practiceSlug": "ships-tests-with-the-change",
                      "basedOn": ["obs-0"], "action": "NEW",
                      "title": "t", "body": "b", "nextStep": "n",
                      "placement": { "kind": "DIFF", "observationId": "obs-0", "citationIndex": 0 } }
                    """,
                    "[]"
                )
            )
        ).isEmpty();
    }

    @Test
    void keepsAWithholdWithItsReasonAndDropsOneWithout() {
        assertThat(
            parser.parse(
                output(
                    """
                    { "channel": "IN_APP", "practiceSlug": "ships-tests-with-the-change",
                      "basedOn": ["obs-0"], "action": "WITHHOLD", "withholdReason": "ALREADY_SAID" }
                    """,
                    "[]"
                )
            )
        )
            .singleElement()
            .satisfies(unit -> {
                assertThat(unit.action()).isEqualTo(ComposedFeedbackUnit.Action.WITHHOLD);
                assertThat(unit.withholdReason()).isEqualTo(ComposedFeedbackUnit.WithholdReason.ALREADY_SAID);
            });

        assertThat(
            parser.parse(
                output(
                    """
                    { "channel": "IN_APP", "practiceSlug": "ships-tests-with-the-change",
                      "basedOn": ["obs-0"], "action": "WITHHOLD" }
                    """,
                    "[]"
                )
            )
        ).isEmpty();
    }

    /**
     * Without a body there is nothing to read, and without a next step it is a verdict rather than
     * feedback. A unit missing either is dropped rather than delivered half.
     */
    @ParameterizedTest
    @ValueSource(strings = { "channel", "practiceSlug", "basedOn", "action", "title", "body", "nextStep" })
    void dropsAnInAppUnitMissingAnyLoadBearingPart(String omitted) {
        var unit = objectMapper.createObjectNode();
        unit.put("channel", "IN_APP");
        unit.put("practiceSlug", "ships-tests-with-the-change");
        unit.putArray("basedOn").add("obs-0");
        unit.put("action", "NEW");
        unit.put("title", "A title");
        unit.put("body", "A body");
        unit.put("nextStep", "A next step");
        unit.remove(omitted);

        assertThat(parser.parse(outputOf(objectMapper.createArrayNode().add(unit), "[]"))).isEmpty();
    }

    /** Two units about one habit on one surface read as two problems, so the second is dropped. */
    @Test
    void keepsOnlyTheFirstUnitForAPracticeOnOneChannel() {
        List<ComposedFeedbackUnit> units = parser.parse(
            outputOf(
                objectMapper.readTree(
                    """
                    [
                      { "channel": "IN_APP", "practiceSlug": "p", "basedOn": ["obs-0"], "action": "NEW",
                        "title": "First", "body": "b", "nextStep": "n" },
                      { "channel": "IN_APP", "practiceSlug": "p", "basedOn": ["obs-0"], "action": "NEW",
                        "title": "Second", "body": "b", "nextStep": "n" }
                    ]
                    """
                ),
                "[]"
            )
        );

        assertThat(units).singleElement().extracting(ComposedFeedbackUnit::title).isEqualTo("First");
    }

    /** One practice may earn one message on each surface — that is the point of composing all three at once. */
    @Test
    void keepsOnePracticeOnTwoDifferentChannelsAndHandsEachLaneItsOwn() {
        JsonNode jobOutput = outputOf(
            objectMapper.readTree(
                """
                [
                  { "channel": "IN_CONTEXT", "practiceSlug": "ships-tests-with-the-change",
                    "basedOn": ["obs-0"], "action": "NEW", "title": "on the work", "nextStep": "n",
                    "placement": { "kind": "DIFF", "observationId": "obs-0", "citationIndex": 0 } },
                  { "channel": "IN_APP", "practiceSlug": "ships-tests-with-the-change",
                    "basedOn": ["obs-0"], "action": "NEW", "title": "on the page", "body": "b", "nextStep": "n" }
                ]
                """
            ),
            "[]"
        );

        assertThat(parser.parse(jobOutput))
            .extracting(ComposedFeedbackUnit::channel)
            .containsExactly(FeedbackChannel.IN_CONTEXT, FeedbackChannel.IN_APP);
        assertThat(parser.parse(jobOutput, FeedbackChannel.IN_APP))
            .singleElement()
            .extracting(ComposedFeedbackUnit::title)
            .isEqualTo("on the page");
    }

    @Test
    void normalisesTheSlugTheSameWayTheFindingParserDoes() {
        assertThat(
            parser.parse(
                output(
                    """
                    { "channel": "IN_APP", "practiceSlug": "Ships_Tests", "basedOn": ["obs-0"],
                      "action": "NEW", "title": "t", "body": "b", "nextStep": "n" }
                    """,
                    "[]"
                )
            )
        )
            .singleElement()
            .extracting(ComposedFeedbackUnit::practiceSlug)
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
        assertThat(parser.parse(raw("{}"))).isEmpty();
        assertThat(parser.parse(raw("{ \"units\": \"not an array\" }"))).isEmpty();
        assertThat(parser.parse(raw("{ \"units\": [ 3, null, \"x\" ] }"))).isEmpty();
    }

    private JsonNode output(String unitJson, String preparedThreadKeysJson) {
        return outputOf(objectMapper.createArrayNode().add(objectMapper.readTree(unitJson)), preparedThreadKeysJson);
    }

    private JsonNode outputOf(JsonNode units, String preparedThreadKeysJson) {
        var payload = objectMapper.createObjectNode();
        payload.set("observations", objectMapper.readTree(OBSERVATIONS));
        payload.set("preparedThreadKeys", objectMapper.readTree(preparedThreadKeysJson));
        payload.set("units", units);
        var jobOutput = objectMapper.createObjectNode();
        jobOutput.set("feedback", payload);
        return jobOutput;
    }

    private JsonNode raw(String feedbackJson) {
        var jobOutput = objectMapper.createObjectNode();
        jobOutput.set("feedback", objectMapper.readTree(feedbackJson));
        return jobOutput;
    }
}
