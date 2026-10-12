package de.tum.cit.aet.hephaestus.agent.handler.composition;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class FeedbackCompositionResultParserTest extends BaseUnitTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final FeedbackCompositionResultParser parser = new FeedbackCompositionResultParser();

    /**
     * The staged {@code results}: two NOT_MET results, each with one problem. The first problem has one anchorable
     * diff citation and one that is not; the second has none. A root citation is staged on the first result too, to
     * prove that only a problem's citation anchors a line note.
     */
    private static final String RESULTS = """
        [
          { "practiceResultId": "res-0", "practiceSlug": "ships-tests-with-the-change", "outcome": "NOT_MET",
            "severity": "MAJOR", "anchorable": true,
            "citations": [
              { "index": 0, "sourceKind": "scm.pull-request.diff", "path": "src/billing/RootOnly.java",
                "side": "NEW", "startLine": 3, "endLine": 3, "anchorable": true }
            ],
            "observations": [
              { "observationId": "prob-0", "anchorable": true,
                "citations": [
                  { "index": 0, "sourceKind": "scm.pull-request.diff", "path": "src/billing/InvoiceTotals.java",
                    "side": "NEW", "startLine": 47, "endLine": 48, "anchorable": true },
                  { "index": 1, "sourceKind": "scm.review-threads", "path": "thread/9",
                    "side": null, "startLine": 1, "endLine": null, "anchorable": false }
                ] }
            ] },
          { "practiceResultId": "res-1", "practiceSlug": "keeps-the-thread-moving", "outcome": "NOT_MET",
            "severity": "MINOR", "anchorable": false,
            "observations": [
              { "observationId": "prob-1", "anchorable": false,
                "citations": [
                  { "index": 0, "sourceKind": "scm.review-threads", "path": "thread/9",
                    "side": null, "startLine": 1, "endLine": null, "anchorable": false }
                ] }
            ] }
        ]
        """;

    private static final String R0 = "{ \"practiceResultId\": \"res-0\" }";
    private static final String P0 = "{ \"practiceResultId\": \"res-0\", \"observationId\": \"prob-0\" }";
    private static final String R1 = "{ \"practiceResultId\": \"res-1\" }";
    private static final String P1 = "{ \"practiceResultId\": \"res-1\", \"observationId\": \"prob-1\" }";

    @Test
    void compositionStatusDistinguishesLegacyQuietFailureAndMalformedMetadata() {
        assertThat(FeedbackCompositionResultParser.compositionStatus(null))
                .isEqualTo(FeedbackCompositionResultParser.CompositionStatus.LEGACY_UNKNOWN);
        assertThat(FeedbackCompositionResultParser.compositionStatus(objectMapper.readTree("{\"feedback\":{}}")))
                .isEqualTo(FeedbackCompositionResultParser.CompositionStatus.LEGACY_UNKNOWN);
        assertThat(FeedbackCompositionResultParser.compositionStatus(
                        objectMapper.readTree("{\"feedback\":{\"compositionFailures\":[]}}")))
                .isEqualTo(FeedbackCompositionResultParser.CompositionStatus.NO_RECORDED_FAILURE);
        for (String reason : List.of(
                "MODEL_ERROR",
                "RUNTIME_ERROR",
                "BUDGET",
                "STALL",
                "SAFETY",
                "LOOP",
                "NO_DECISION",
                "PRIVATE_NOT_RECOVERED")) {
            var status = FeedbackCompositionResultParser.compositionStatus(objectMapper.readTree(
                    "{\"feedback\":{\"compositionFailures\":[{\"phase\":\"PRIVATE_FEEDBACK\",\"reason\":\"%s\"}]}}"
                            .formatted(reason)));
            assertThat(status).isEqualTo(FeedbackCompositionResultParser.CompositionStatus.INCOMPLETE);
            assertThat(status.failed()).isTrue();
            assertThat(status.message()).isEqualTo("Some feedback could not be composed.");
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "null",
                "{}",
                "[null]",
                "[{\"phase\":\"PUBLIC_REVIEW\",\"reason\":\"PRIVATE_NOT_RECOVERED\"}]",
                "[1]",
                "[{\"phase\":\"PRIVATE_FEEDBACK\",\"reason\":\"provider secret\"}]",
                "[{\"phase\":\"MEASUREMENT\",\"reason\":\"MODEL_ERROR\"}]",
                "[{\"phase\":\"PUBLIC_REVIEW\",\"reason\":\"LOOP\",\"error\":\"private text\"}]",
                "[{\"phase\":\"PUBLIC_REVIEW\",\"reason\":\"LOOP\"},{\"phase\":\"PUBLIC_REVIEW\",\"reason\":\"SAFETY\"}]"
            })
    void malformedCompositionStatusDoesNotCertifySilence(String failures) {
        var status = FeedbackCompositionResultParser.compositionStatus(
                objectMapper.readTree("{\"feedback\":{\"compositionFailures\":%s}}".formatted(failures)));
        assertThat(status).isEqualTo(FeedbackCompositionResultParser.CompositionStatus.INVALID);
        assertThat(status.message()).isEqualTo("Feedback composition status could not be read.");
    }

    @Test
    void shouldIgnoreAnEarlierRunnersFragmentUnitForTheWorkAndKeepItsNextStepAsHistory() {
        // Written before results had problems: a flat staged list keyed "observations", and basedOn as plain ids.
        JsonNode legacy = raw("""
            { "observations": [
                { "id": "obs-0", "practiceSlug": "ships-tests-with-the-change", "outcome": "NOT_MET" } ],
              "units": [
                { "channel": "IN_CONTEXT", "practiceSlug": "ships-tests-with-the-change",
                  "basedOn": ["obs-0", "obs-never-staged"], "action": "NEW",
                  "title": "This branch is untested",
                  "nextStep": "Add a case that calls total with a tax-exempt customer.",
                  "placement": { "kind": "DIFF", "observationId": "obs-0", "citationIndex": 0 } } ] }
            """);

        assertThat(parser.parse(legacy)).isEmpty();
        assertThat(parser.writtenWhole(legacy)).isFalse();
        assertThat(parser.historicalNextSteps(legacy))
                .containsExactly(Map.entry("obs-0", "Add a case that calls total with a tax-exempt customer."));
    }

    @Test
    void shouldReadUnitWhenGroundedInPrimaryAndRelatedPracticeEvidence() {
        List<ComposedFeedbackUnit> units = parser.parse(output("""
                { "channel": "IN_APP", "practiceSlug": "ships-tests-with-the-change",
                  "basedOn": [%s, %s], "action": "NEW",
                  "title": "Review readiness breaks across the workflow",
                  "body": "The change enters review without tests and leaves follow-up work unresolved.",
                  "nextStep": "Add the test and resolve the linked review thread before requesting review." }
                """.formatted(P0, R1), "[]"));

        assertThat(units).singleElement().satisfies(unit -> {
            assertThat(unit.basedOn()).containsExactly("res-0", "res-1");
            assertThat(unit.observations()).containsExactly("prob-0");
        });
    }

    @Test
    void shouldReadNotesWhenConversationUnitIsValid() {
        List<ComposedFeedbackUnit> units = parser.parse(output("""
                { "channel": "IN_CHAT", "practiceSlug": "ships-tests-with-the-change",
                  "basedOn": [%s], "action": "NEW",
                  "title": "Tests arrive after review",
                  "notes": {
                    "situation": "On !18, !20 and !22 the test landed a push after the review comment.",
                    "capability": "Writing the test last is what leaves the review to find the gap.",
                    "evidenceSummary": "On the last three changes the test arrived a push later.",
                    "inConversationSignal": "They name a check they could run before pushing."
                  } }
                """.formatted(R0), "[]"));

        assertThat(units).singleElement().satisfies(unit -> {
            assertThat(unit.body()).isNull();
            assertThat(unit.observations()).isEmpty();
            assertThat(unit.notes()).isNotNull();
            assertThat(unit.notes().situation())
                    .isEqualTo("On !18, !20 and !22 the test landed a push after the review comment.");
            assertThat(unit.notes().inConversationSignal())
                    .isEqualTo("They name a check they could run before pushing.");
        });
    }

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
            })
    void shouldRejectConversationUnitWhenRequiredNoteIsMissing(String notes) {
        assertThat(parser.parse(output("""
                    { "channel": "IN_CHAT", "practiceSlug": "ships-tests-with-the-change",
                      "basedOn": [%s], "action": "NEW",
                      "title": "Tests arrive after review",
                      "notes": { %s } }
                    """.formatted(R0, notes), "[]"))).isEmpty();
    }

    @Test
    void refusesAnOverlongNoteInsteadOfPersistingAnArbitrarilyTruncatedPlan() {
        String oversized = "x".repeat(ComposedFeedbackUnit.MAX_AIM_LENGTH + 1);
        assertThat(parser.parse(output("""
                    { "channel": "IN_CHAT", "practiceSlug": "ships-tests-with-the-change",
                      "basedOn": [%s], "action": "NEW",
                      "title": "Tests arrive after review",
                      "notes": {
                        "situation": "Tests repeatedly arrived after review.",
                        "capability": "%s",
                        "evidenceSummary": "Three merge requests show the sequence.",
                        "inConversationSignal": "The developer can name when the test belongs."
                      } }
                    """.formatted(R0, oversized), "[]"))).isEmpty();
    }

    @Test
    void refusesAUnitNamingAThreadKeyThatWasNeverStaged() {
        String unit = """
            { "channel": "IN_APP", "practiceSlug": "ships-tests-with-the-change",
              "basedOn": [%s], "action": "SUPERSEDE",
              "supersedesThreadKey": "invented-key",
              "title": "A way of working", "body": "A body", "nextStep": "A next step" }
            """.formatted(R0);

        assertThat(parser.parse(output(unit, "[]"))).isEmpty();
        assertThat(parser.parse(output(unit, "[\"invented-key\"]"))).hasSize(1);
    }

    @Test
    void refusesAnAnchorOnALongitudinalLane() {
        assertThat(parser.parse(output("""
                    { "channel": "IN_APP", "practiceSlug": "ships-tests-with-the-change",
                      "basedOn": [%s], "action": "NEW",
                      "title": "t", "body": "b", "nextStep": "n",
                      "placement": { "kind": "DIFF", "observationId": "prob-0", "citationIndex": 0 } }
                    """.formatted(P0), "[]"))).isEmpty();
    }

    @Test
    void shouldKeepOnlyWithholdWhenReasonIsPresent() {
        assertThat(parser.parse(output("""
                    { "channel": "IN_APP", "practiceSlug": "ships-tests-with-the-change",
                      "basedOn": [%s], "action": "WITHHOLD", "withholdReason": "ALREADY_SAID" }
                    """.formatted(P0), "[]")))
                .singleElement()
                .satisfies(unit -> {
                    assertThat(unit.action()).isEqualTo(ComposedFeedbackUnit.Action.WITHHOLD);
                    assertThat(unit.withholdReason()).isEqualTo(ComposedFeedbackUnit.WithholdReason.ALREADY_SAID);
                    assertThat(unit.basedOn()).containsExactly("res-0");
                    assertThat(unit.observations()).containsExactly("prob-0");
                });

        assertThat(parser.parse(output("""
                    { "channel": "IN_APP", "practiceSlug": "ships-tests-with-the-change",
                      "basedOn": [%s], "action": "WITHHOLD" }
                    """.formatted(R0), "[]"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                R1,
                "{ \"practiceResultId\": \"res-missing\" }",
                "{ \"practiceResultId\": \"res-0\", \"observationId\": \"prob-1\" }",
                "{ \"practiceResultId\": \"res-0\", \"observationId\": \"prob-missing\" }",
                "{ \"practiceResultId\": \"res-0\", \"observationId\": 7 }",
                "{ \"practiceResultId\": \"res-0\", \"id\": \"res-0\" }",
                "{ \"observationId\": \"prob-0\" }",
                "\"res-0\"",
                "\"prior:ships-tests-with-the-change\"",
            })
    void shouldRejectAUnitWhenItsEvidenceIsFromAnotherPracticeUnknownOrNotARef(String reference) {
        assertThat(parser.parse(output("""
                    { "channel": "IN_APP", "practiceSlug": "ships-tests-with-the-change",
                      "basedOn": [%s], "action": "NEW",
                      "title": "t", "body": "b", "nextStep": "n" }
                    """.formatted(reference), "[]"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"channel", "practiceSlug", "basedOn", "action", "title", "body", "nextStep"})
    void dropsAnInAppUnitMissingAnyLoadBearingPart(String omitted) {
        var unit = objectMapper.createObjectNode();
        unit.put("channel", "IN_APP");
        unit.put("practiceSlug", "ships-tests-with-the-change");
        unit.putArray("basedOn").addObject().put("practiceResultId", "res-0");
        unit.put("action", "NEW");
        unit.put("title", "A title");
        unit.put("body", "A body");
        unit.put("nextStep", "A next step");
        unit.remove(omitted);

        assertThat(parser.parse(outputOf(objectMapper.createArrayNode().add(unit), "[]")))
                .isEmpty();
    }

    @Test
    void keepsOnlyTheFirstUnitForAPracticeOnOneChannel() {
        List<ComposedFeedbackUnit> units = parser.parse(outputOf(objectMapper.readTree("""
                    [
                      { "channel": "IN_APP", "practiceSlug": "ships-tests-with-the-change", "basedOn": [%s], "action": "NEW",
                        "title": "First", "body": "b", "nextStep": "n" },
                      { "channel": "IN_APP", "practiceSlug": "ships-tests-with-the-change", "basedOn": [%s], "action": "NEW",
                        "title": "Second", "body": "b", "nextStep": "n" }
                    ]
                    """.formatted(R0, R0)), "[]"));

        assertThat(units)
                .singleElement()
                .extracting(ComposedFeedbackUnit::title)
                .isEqualTo("First");
    }

    @Test
    void filtersByChannelWithoutCollapsingOtherChannels() {
        JsonNode jobOutput = outputOf(objectMapper.readTree("""
                [
                  { "channel": "IN_CONTEXT", "practiceSlug": "ships-tests-with-the-change",
                    "basedOn": [%s], "action": "NEW", "title": "on the work", "nextStep": "n" },
                  { "channel": "IN_APP", "practiceSlug": "ships-tests-with-the-change",
                    "basedOn": [%s], "action": "NEW", "title": "on the page", "body": "b", "nextStep": "n" }
                ]
                """.formatted(P0, R0)), "[]");

        assertThat(parser.parse(jobOutput))
                .extracting(ComposedFeedbackUnit::channel)
                .containsExactly(FeedbackChannel.IN_APP);
        assertThat(parser.parse(jobOutput, FeedbackChannel.IN_APP))
                .singleElement()
                .extracting(ComposedFeedbackUnit::title)
                .isEqualTo("on the page");
    }

    @Test
    void shouldKeepAllUnitsWhenEveryLaneUsesItsFullCapacity() {
        var practiceResults = objectMapper.createArrayNode();
        var units = objectMapper.createArrayNode();
        for (int practice = 0; practice < 15; practice++) {
            String practiceSlug = "practice-" + practice;
            String practiceResultId = "res-" + practice;
            practiceResults
                    .addObject()
                    .put("practiceResultId", practiceResultId)
                    .put("practiceSlug", practiceSlug)
                    .put("outcome", "NOT_MET");
            for (FeedbackChannel channel : List.of(FeedbackChannel.IN_APP, FeedbackChannel.IN_CHAT)) {
                var unit = units.addObject().put("channel", channel.name()).put("practiceSlug", practiceSlug);
                unit.putArray("basedOn").addObject().put("practiceResultId", practiceResultId);
                unit.put("action", "WITHHOLD").put("withholdReason", "ALREADY_SAID");
            }
        }
        var payload = objectMapper.createObjectNode();
        payload.set("results", practiceResults);
        payload.set("preparedTargets", objectMapper.createArrayNode());
        payload.set("units", units);
        var output = objectMapper.createObjectNode();
        output.set("feedback", payload);

        assertThat(parser.parse(output)).hasSize(30);
    }

    @Test
    void normalisesPracticeSlug() {
        assertThat(parser.parse(output("""
                    { "channel": "IN_APP", "practiceSlug": "Ships_Tests_With_The_Change", "basedOn": [%s],
                      "action": "NEW", "title": "t", "body": "b", "nextStep": "n" }
                    """.formatted(R0), "[]")))
                .singleElement()
                .extracting(ComposedFeedbackUnit::practiceSlug)
                .isEqualTo("ships-tests-with-the-change");
    }

    @Test
    void treatsAnyMalformedOrAbsentPayloadAsNothingComposed() {
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse(objectMapper.createObjectNode())).isEmpty();
        assertThat(parser.parse(raw("{}"))).isEmpty();
        assertThat(parser.parse(raw("{ \"units\": \"not an array\" }"))).isEmpty();
        assertThat(parser.parse(raw("{ \"units\": [ 3, null, \"x\" ] }"))).isEmpty();
    }

    // --- The review on the work ---------------------------------------------------------------------------

    private static final String SUMMARY = "  The new branch in `total` has no test.\\n\\n"
            + "    total(taxExempt)\\n\\nAdd a case that calls it with a tax-exempt customer.\\n";

    private static final String ANCHOR_0 = "{ \"observationId\": \"prob-0\", \"citationIndex\": 0 }";

    private JsonNode review(String reviewJson) {
        return raw("""
            { "contractVersion": 2, "results": %s, "units": [], "review": %s }
            """.formatted(RESULTS, reviewJson));
    }

    @Test
    void shouldReadACompleteReviewWithEveryBodyExactlyAsWrittenAndTheAnchorFromTheCitation() {
        JsonNode output = review("""
            { "summary": { "body": "%s", "basedOn": [%s, %s] },
              "inline": [ { "body": "No test reaches this line.", "basedOn": [%s], "anchor": %s } ],
              "withheld": [] }
            """.formatted(SUMMARY, P0, R1, P0, ANCHOR_0));

        assertThat(parser.writtenWhole(output)).isTrue();
        ComposedReview read = parser.review(output);
        assertThat(read).isNotNull();
        ComposedReview.Summary summary = read.summary();
        assertThat(summary).isNotNull();
        assertThat(summary.body())
                .isEqualTo("  The new branch in `total` has no test.\n\n"
                        + "    total(taxExempt)\n\nAdd a case that calls it with a tax-exempt customer.\n");
        assertThat(summary.basedOn()).containsExactly("res-0", "res-1");
        assertThat(summary.observations()).containsExactly("prob-0");
        assertThat(read.inline()).singleElement().satisfies(note -> {
            assertThat(note.basedOn()).containsExactly("res-0");
            assertThat(note.observations()).containsExactly("prob-0");
            assertThat(note.anchor())
                    .isEqualTo(new ComposedReview.ResolvedAnchor(
                            "res-0", "prob-0", 0, "src/billing/InvoiceTotals.java", "NEW", 47, 48));
        });
        assertThat(read.decidedResultIds()).containsExactlyInAnyOrder("res-0", "res-1");
    }

    @Test
    void shouldReadAValidEmptyReviewAsSilenceAndAMissingOneAsNoReview() {
        assertThat(parser.review(review("{}"))).isEqualTo(ComposedReview.empty());
        assertThat(parser.review(review("null"))).isNull();
        assertThat(parser.review(raw("{ \"contractVersion\": 2, \"units\": [] }")))
                .isNull();
        assertThat(parser.review(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                // Support that is not a non-empty list of refs to this run's results and their own problems.
                "{ \"summary\": { \"body\": \"Add the test.\", \"basedOn\": [" + R0 + ", 42] } }",
                "{ \"summary\": { \"body\": \"Add the test.\", \"basedOn\": " + R0 + " } }",
                "{ \"summary\": { \"body\": \"Add the test.\", \"basedOn\": [] } }",
                "{ \"summary\": { \"body\": \"Add the test.\", \"basedOn\": [{ \"practiceResultId\": \"res-missing\" }] } }",
                "{ \"summary\": { \"body\": \"Add the test.\", \"basedOn\": [\"res-0\"] } }",
                "{ \"summary\": { \"body\": \"Add the test.\","
                        + " \"basedOn\": [{ \"practiceResultId\": \"res-0\", \"observationId\": \"prob-1\" }] } }",
                // Bodies the server would have to change before it could publish them.
                "{ \"summary\": { \"body\": \"   \", \"basedOn\": [" + R0 + "] } }",
                "{ \"summary\": { \"body\": \"Fine. <!-- marker -->\", \"basedOn\": [" + R0 + "] } }",
                "{ \"summary\": { \"body\": \"Add the reason.}\\\"\", \"basedOn\": [" + R0 + "] } }",
                // A line note whose support does not name the problem it is anchored on.
                "{ \"inline\": [ { \"body\": \"x\", \"basedOn\": [" + P1 + "], \"anchor\": " + ANCHOR_0 + " } ] }",
                "{ \"inline\": [ { \"body\": \"x\", \"basedOn\": [" + R0 + "], \"anchor\": " + ANCHOR_0 + " } ] }",
                // A line note on a problem, or a citation of it, that is not anchorable.
                "{ \"inline\": [ { \"body\": \"x\", \"basedOn\": [" + P1 + "],"
                        + " \"anchor\": { \"observationId\": \"prob-1\", \"citationIndex\": 0 } } ] }",
                "{ \"inline\": [ { \"body\": \"x\", \"basedOn\": [" + P0 + "],"
                        + " \"anchor\": { \"observationId\": \"prob-0\", \"citationIndex\": 1 } } ] }",
                "{ \"inline\": [ { \"body\": \"x\", \"basedOn\": [" + P0 + "],"
                        + " \"anchor\": { \"observationId\": \"prob-0\", \"citationIndex\": 4 } } ] }",
                "{ \"inline\": [ { \"body\": \"x\", \"basedOn\": [" + P0 + "],"
                        + " \"anchor\": { \"observationId\": \"prob-0\", \"citationIndex\": \"0\" } } ] }",
                // A root citation never anchors, in either the new or the earlier anchor shape.
                "{ \"inline\": [ { \"body\": \"x\", \"basedOn\": [" + P0 + "],"
                        + " \"anchor\": { \"observationId\": \"res-0\", \"citationIndex\": 0 } } ] }",
                "{ \"inline\": [ { \"body\": \"x\", \"basedOn\": [" + P0 + "],"
                        + " \"anchor\": { \"practiceResultId\": \"res-0\", \"citationIndex\": 0 } } ] }",
                "{ \"inline\": { \"body\": \"x\" } }",
                "{ \"withheld\": [ { \"basedOn\": [" + R0 + "], \"reason\": \"BORED\" } ] }",
                "{ \"summary\": { \"body\": \"Add the test.\", \"basedOn\": [" + R0 + "] },"
                        + " \"withheld\": [ { \"basedOn\": [" + P0 + "], \"reason\": \"BELOW_BAR\" } ] }",
                "{ \"lead\": \"A fragment from an earlier contract.\" }",
            })
    void shouldReadAReviewThatBreaksItsContractAsNoReviewRatherThanAsSilence(String reviewJson) {
        assertThat(parser.review(review(reviewJson))).isNull();
    }

    @Test
    void shouldReadAWithholdingWhenItNamesANotMetResultAndOneOfItsProblems() {
        ComposedReview read = parser.review(review("""
            { "withheld": [ { "basedOn": [%s, %s], "reason": "ALREADY_SAID" } ] }
            """.formatted(P0, R1)));

        assertThat(read).isNotNull();
        assertThat(read.withheld()).singleElement().satisfies(decision -> {
            assertThat(decision.basedOn()).containsExactly("res-0", "res-1");
            assertThat(decision.reason()).isEqualTo(ComposedFeedbackUnit.WithholdReason.ALREADY_SAID);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"MET", "UNDETERMINED", "NOT_APPLICABLE"})
    void shouldRefuseWithholdingWhenTheNamedResultIsNotNotMet(String outcome) {
        String evidence = RESULTS.replace("NOT_MET", outcome);
        JsonNode output = raw("""
            { "contractVersion": 2, "results": %s,
              "review": { "withheld": [{ "basedOn": [%s], "reason": "ALREADY_SAID" }] } }
            """.formatted(evidence, R0));
        assertThat(parser.review(output)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNDETERMINED", "NOT_APPLICABLE"})
    void shouldRefuseASummaryWhenItRestsOnAResultThatDecidesNothing(String outcome) {
        String evidence = RESULTS.replace("NOT_MET", outcome);
        JsonNode output = raw("""
            { "contractVersion": 2, "results": %s,
              "review": { "summary": { "body": "Add the test.", "basedOn": [%s] } } }
            """.formatted(evidence, R0));
        assertThat(parser.review(output)).isNull();
    }

    @Test
    void shouldRefuseAWholeReviewOverTheLineNoteBoundOrWithTwoNotesOnOneLine() {
        String note = "{ \"body\": \"x\", \"basedOn\": [" + P0 + "], \"anchor\": " + ANCHOR_0 + " }";

        assertThat(parser.review(review("{ \"inline\": [" + note + "] }"))).isNotNull();
        assertThat(parser.review(review("{ \"inline\": [" + note + ", " + note + "] }")))
                .isNull();
        assertThat(parser.review(review("{ \"inline\": [" + String.join(", ", Collections.nCopies(31, note)) + "] }")))
                .isNull();
    }

    private JsonNode output(String unitJson, String preparedThreadKeysJson) {
        return outputOf(objectMapper.createArrayNode().add(objectMapper.readTree(unitJson)), preparedThreadKeysJson);
    }

    private JsonNode outputOf(JsonNode units, String preparedThreadKeysJson) {
        var payload = objectMapper.createObjectNode();
        payload.set("results", objectMapper.readTree(RESULTS));
        var preparedTargets = objectMapper.createArrayNode();
        for (JsonNode key : objectMapper.readTree(preparedThreadKeysJson)) {
            preparedTargets
                    .addObject()
                    .put("threadKey", key.asString())
                    .put("channel", "IN_APP")
                    .put("practiceSlug", "ships-tests-with-the-change");
        }
        payload.set("preparedTargets", preparedTargets);
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
