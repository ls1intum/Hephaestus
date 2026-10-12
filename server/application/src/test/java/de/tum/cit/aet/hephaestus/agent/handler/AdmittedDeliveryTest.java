package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.agent.handler.ReviewResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.ReviewResultParser.ValidatedProblem;
import de.tum.cit.aet.hephaestus.agent.handler.ReviewResultParser.ValidatedResult;
import de.tum.cit.aet.hephaestus.agent.handler.ReviewResultParser.WithheldResult;
import de.tum.cit.aet.hephaestus.agent.handler.composition.ComposedFeedbackUnit;
import de.tum.cit.aet.hephaestus.agent.handler.composition.ComposedReview;
import de.tum.cit.aet.hephaestus.practices.PracticeDeliveryBehavior;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Admission of a complete review: each body goes out whole, exactly as written, or not at all. */
class AdmittedDeliveryTest extends BaseUnitTest {

    private static final long AUTHOR = 7L;
    private static final long OTHER_PERSON = 8L;
    private static final String PATH = "src/Auth.java";
    private static final String SUMMARY = "The sign-in path calls `insecure()` before the token is checked.\n\n"
            + "    insecure();\n\n"
            + "Route it through the checked helper so a bad token cannot reach it.";

    private final JsonMapper mapper = new JsonMapper();
    private final Map<UUID, Long> subjects = new HashMap<>();

    private ValidatedResult practiceResult(String slug, Outcome outcome, String sourceKind, long about) {
        ObjectNode evidence = mapper.createObjectNode();
        // The result's own citation is never verified at a line: only a problem's citation may carry a note.
        ObjectNode own = evidence.putArray("citations").addObject();
        own.put("sourceKind", sourceKind);
        own.put("path", PATH);
        own.put("startLine", 1);
        evidence.putObject("search").putArray("consulted").add("scm.pull-request.diff");
        UUID id = UUID.randomUUID();
        subjects.put(id, about);
        List<ValidatedProblem> problems = outcome == Outcome.NOT_MET
                ? List.of(problemAt(0, citations(diffCitation(sourceKind, PATH, 10))))
                : List.of();
        return new ValidatedResult(
                slug,
                "Summary of " + slug,
                outcome,
                outcome == Outcome.NOT_MET ? Severity.MINOR : null,
                evidence,
                "What the evidence shows.",
                new ResultKeys("occ-" + id, id),
                PracticeDeliveryBehavior.DEFAULT,
                problems,
                null);
    }

    private ObjectNode diffCitation(String sourceKind, String path, int line) {
        ObjectNode citation = mapper.createObjectNode();
        citation.put("sourceKind", sourceKind);
        citation.put("path", path);
        citation.put("side", "NEW");
        citation.put("startLine", line);
        citation.put("endLine", line);
        citation.putObject("verification").put("status", "VERIFIED").put("scope", "EXACT_LOCATION");
        return citation;
    }

    private ArrayNode citations(ObjectNode... citations) {
        ArrayNode array = mapper.createArrayNode();
        for (ObjectNode citation : citations) array.add(citation);
        return array;
    }

    private static ValidatedProblem problemAt(int position, ArrayNode citations) {
        return new ValidatedProblem(
                UUID.randomUUID(),
                position,
                null,
                Severity.MINOR,
                "A problem at position " + position,
                "What this place shows.",
                citations,
                null,
                null,
                null);
    }

    private ValidatedResult problem(String slug) {
        return practiceResult(slug, Outcome.NOT_MET, "scm.pull-request.diff", AUTHOR);
    }

    private static String id(ValidatedResult practiceResult) {
        return String.valueOf(practiceResult.practiceResultId());
    }

    /** The id of the result's first problem; a result without one gets an id no problem has. */
    private static String problemId(ValidatedResult practiceResult) {
        return practiceResult.problems().isEmpty()
                ? "no-problem-of-" + id(practiceResult)
                : String.valueOf(practiceResult.problems().getFirst().observationId());
    }

    private static ComposedReview.InlineNote note(String body, ValidatedResult anchored, ValidatedResult... more) {
        List<String> basedOn = new ArrayList<>(List.of(id(anchored)));
        List<String> observations = new ArrayList<>(List.of(problemId(anchored)));
        for (ValidatedResult also : more) {
            basedOn.add(id(also));
            if (!also.problems().isEmpty()) observations.add(problemId(also));
        }
        return new ComposedReview.InlineNote(
                body,
                basedOn,
                observations,
                new ComposedReview.ResolvedAnchor(id(anchored), problemId(anchored), 0, PATH, "NEW", 10, 10));
    }

    private AdmittedDelivery decide(
            ComposedReview review,
            List<ValidatedResult> recorded,
            List<ValidatedResult> awaitingApproval,
            List<ValidatedResult> automatic) {
        return decide(review, recorded, awaitingApproval, automatic, subjects.keySet());
    }

    private AdmittedDelivery decide(
            ComposedReview review,
            List<ValidatedResult> recorded,
            List<ValidatedResult> awaitingApproval,
            List<ValidatedResult> automatic,
            Set<UUID> publicIds) {
        return AdmittedDelivery.decide(
                review,
                ArtifactKinds.PULL_REQUEST,
                recorded,
                subjects.entrySet().stream()
                        .filter(entry -> publicIds.contains(entry.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
                awaitingApproval,
                automatic);
    }

    @Test
    void shouldPreserveAnIndependentAuthorPartButRefuseTheWholeBodyOfMixedRoleSupport() {
        ValidatedResult author = problem("describe-what-and-why");
        ValidatedResult reviewer = problem("review-communication");
        ComposedReview review = new ComposedReview(
                new ComposedReview.Summary("Explain the purpose of this change.", List.of(id(author))),
                List.of(note("This body concerns both the change and its review.", author, reviewer)),
                List.of());
        DeliveryContent content = automatic(AdmittedDelivery.decide(
                review,
                ArtifactKinds.PULL_REQUEST,
                List.of(author, reviewer),
                Map.of(Objects.requireNonNull(author.practiceResultId()), AUTHOR),
                List.of(),
                List.of(author, reviewer)));
        assertThat(content.mrNote()).isEqualTo("Explain the purpose of this change.");
        assertThat(content.diffNotes()).isEmpty();
    }

    private static DeliveryContent automatic(AdmittedDelivery delivery) {
        assertThat(delivery).isInstanceOf(AdmittedDelivery.Automatic.class);
        return Objects.requireNonNull(((AdmittedDelivery.Automatic) delivery).content());
    }

    @Test
    void shouldKeepSummaryOnlySupportOutOfAnInlineBodyButAllowItsSummary() {
        ValidatedResult original = problem("describe-what-and-why");
        ValidatedResult summaryOnly = new ValidatedResult(
                original.practiceSlug(),
                original.summary(),
                original.outcome(),
                original.severity(),
                original.evidence(),
                original.evidenceRationale(),
                original.keys(),
                new PracticeDeliveryBehavior(true, null, null),
                original.problems(),
                original.example());
        ValidatedResult lineConcern = problem("checks-tokens-first");
        DeliveryContent content = automatic(decide(
                new ComposedReview(
                        new ComposedReview.Summary(SUMMARY, List.of(id(summaryOnly))),
                        List.of(note("This complete note rests on both results.", lineConcern, summaryOnly)),
                        List.of()),
                List.of(summaryOnly, lineConcern),
                List.of(),
                List.of(summaryOnly, lineConcern)));
        assertThat(content.mrNote()).isEqualTo(SUMMARY);
        assertThat(content.diffNotes()).isEmpty();
    }

    @Test
    void shouldPostTheCompleteSummaryVerbatimWithExactlyItsContributorsWhenEverySupportIsAutomatic() {
        ValidatedResult lapse = problem("checks-tokens-first");
        ValidatedResult unrelated = problem("describe-what-and-why");

        DeliveryContent content = automatic(decide(
                new ComposedReview(new ComposedReview.Summary(SUMMARY, List.of(id(lapse))), List.of(), List.of()),
                List.of(lapse, unrelated),
                List.of(),
                List.of(lapse, unrelated)));

        assertThat(content.mrNote()).isEqualTo(SUMMARY);
        assertThat(content.summaryContributors()).containsExactly(lapse.occurrenceKey());
    }

    @Test
    void shouldRefuseTheWholeSummaryAndKeepAnIndependentNoteWhenOneSupportIsHeld() {
        ValidatedResult posted = problem("checks-tokens-first");
        ValidatedResult disputed = problem("describe-what-and-why");

        DeliveryContent content = automatic(decide(
                new ComposedReview(
                        new ComposedReview.Summary(SUMMARY, List.of(id(posted), id(disputed))),
                        List.of(note("Call the checked helper on this line.", posted)),
                        List.of()),
                List.of(posted, disputed),
                List.of(),
                List.of(posted)));

        assertThat(content.mrNote()).isNull();
        assertThat(content.diffNotes()).singleElement().satisfies(diff -> {
            assertThat(diff.body()).isEqualTo("Call the checked helper on this line.");
            assertThat(diff.deliveryKey()).isEqualTo("observation:" + posted.occurrenceKey() + ":0:0");
            assertThat(diff.contributors()).containsExactly(posted.occurrenceKey());
            assertThat(diff.observations()).containsExactly(problemId(posted));
        });
    }

    @Test
    void shouldPostNothingWhenTheReviewRestsOnlyOnAMetResult() {
        ValidatedResult strength = practiceResult("ships-a-preview", Outcome.MET, "scm.pull-request.diff", AUTHOR);
        String body = "The preview you added shows the screen in both appearances.";

        AdmittedDelivery delivery = decide(
                new ComposedReview(new ComposedReview.Summary(body, List.of(id(strength))), List.of(), List.of()),
                List.of(strength),
                List.of(),
                List.of(strength));

        // A MET result is never acknowledged on the work.
        assertThat(delivery).isEqualTo(new AdmittedDelivery.Automatic(null, Set.of()));
    }

    @Test
    void shouldRefuseABodyRestingOnAnUndecidedOrPrivateResultEvenWhenItPassedTheGates() {
        ValidatedResult undecided =
                practiceResult("keeps-tests-honest", Outcome.UNDETERMINED, "scm.pull-request.diff", AUTHOR);
        ValidatedResult fromHistory =
                practiceResult("checks-tokens-first", Outcome.NOT_MET, "hephaestus.feedback-history", AUTHOR);

        AdmittedDelivery delivery = decide(
                new ComposedReview(
                        new ComposedReview.Summary("Earlier reviews said this twice.", List.of(id(fromHistory))),
                        List.of(note("Nothing settled here.", undecided)),
                        List.of()),
                List.of(undecided, fromHistory),
                List.of(),
                List.of(undecided, fromHistory),
                Set.of(Objects.requireNonNull(undecided.practiceResultId())));

        assertThat(delivery).isEqualTo(new AdmittedDelivery.Automatic(null, Set.of()));
    }

    @Test
    void shouldRefuseAStandaloneApprovalLineButKeepTimingInsideASentence() {
        ValidatedResult lapse = problem("checks-tokens-first");
        String timing = "Before this is ready for another look, route the call through the checked helper.";

        DeliveryContent refused = automatic(decide(
                new ComposedReview(
                        new ComposedReview.Summary(SUMMARY + "\n\nLGTM", List.of(id(lapse))),
                        List.of(note(timing, lapse)),
                        List.of()),
                List.of(lapse),
                List.of(),
                List.of(lapse)));

        assertThat(refused.mrNote()).isNull();
        assertThat(refused.diffNotes())
                .singleElement()
                .extracting(ReviewResultParser.DiffNote::body)
                .isEqualTo(timing);
    }

    @Test
    void shouldRefuseANoteWhoseCitationIsNotAVerifiedLineOfThisChangeOrWhoseWorkHasNoLines() {
        List<Consumer<ObjectNode>> unplaceable = List.of(
                // Verified, but not at its exact location: it cannot carry a note onto that line.
                citation -> citation.putObject("verification").put("status", "VERIFIED"),
                citation -> citation.put("side", "OLD"),
                citation -> citation.put("sourceKind", "scm.review-threads"),
                citation -> citation.put("startLine", 11));
        for (Consumer<ObjectNode> change : unplaceable) {
            ValidatedResult lapse = problem("checks-tokens-first");
            change.accept((ObjectNode) lapse.problems().getFirst().citations().get(0));
            ComposedReview review =
                    new ComposedReview(null, List.of(note("Call the checked helper.", lapse)), List.of());

            assertThat(decide(review, List.of(lapse), List.of(), List.of(lapse)))
                    .isEqualTo(new AdmittedDelivery.Automatic(null, Set.of()));
        }

        ValidatedResult onAnIssue = problem("states-acceptance-criteria");
        assertThat(AdmittedDelivery.decide(
                        new ComposedReview(null, List.of(note("Say which criteria.", onAnIssue)), List.of()),
                        ArtifactKinds.ISSUE,
                        List.of(onAnIssue),
                        subjects,
                        List.of(),
                        List.of(onAnIssue)))
                .isEqualTo(new AdmittedDelivery.Automatic(null, Set.of()));
    }

    @Test
    void shouldPlaceTheNoteOnTheNamedCitationOfTheAnchoredProblemWhenTheResultHasSeveralProblems() {
        ValidatedResult base = problem("checks-tokens-first");
        ObjectNode thread = mapper.createObjectNode()
                .put("sourceKind", "scm.review-threads")
                .put("path", "thread/3");
        ValidatedProblem first = base.problems().getFirst();
        ValidatedProblem second = problemAt(1, citations(thread, diffCitation("scm.pull-request.diff", PATH, 20)));
        ValidatedResult lapse = new ValidatedResult(
                base.practiceSlug(),
                base.summary(),
                base.outcome(),
                base.severity(),
                base.evidence(),
                base.evidenceRationale(),
                base.keys(),
                base.deliveryBehavior(),
                List.of(first, second),
                null);
        String firstId = String.valueOf(first.observationId());
        String secondId = String.valueOf(second.observationId());
        ComposedReview.InlineNote note = new ComposedReview.InlineNote(
                "Check the token before this call.",
                List.of(id(lapse)),
                List.of(firstId, secondId),
                new ComposedReview.ResolvedAnchor(id(lapse), secondId, 1, PATH, "NEW", 20, 20));

        DeliveryContent content = automatic(decide(
                new ComposedReview(
                        new ComposedReview.Summary(SUMMARY, List.of(id(lapse)), List.of(firstId)),
                        List.of(note),
                        List.of()),
                List.of(lapse),
                List.of(),
                List.of(lapse)));

        assertThat(content.summaryObservations()).containsExactly(firstId);
        assertThat(content.diffNotes()).singleElement().satisfies(diff -> {
            assertThat(diff.filePath()).isEqualTo(PATH);
            assertThat(diff.startLine()).isEqualTo(20);
            assertThat(diff.deliveryKey()).isEqualTo("observation:" + lapse.occurrenceKey() + ":1:1");
            assertThat(diff.observations()).containsExactly(secondId, firstId);
        });
    }

    @Test
    void shouldRefuseABodyWhenItNamesAProblemOfAResultItDoesNotRestOn() {
        ValidatedResult lapse = problem("checks-tokens-first");
        ValidatedResult other = problem("describe-what-and-why");
        ComposedReview review = new ComposedReview(
                new ComposedReview.Summary(SUMMARY, List.of(id(lapse)), List.of(problemId(other))),
                List.of(
                        // Anchored on the other result's problem while naming this result as the anchor's owner.
                        new ComposedReview.InlineNote(
                                "Call the checked helper.",
                                List.of(id(lapse), id(other)),
                                List.of(problemId(other)),
                                new ComposedReview.ResolvedAnchor(id(lapse), problemId(other), 0, PATH, "NEW", 10, 10)),
                        // Anchored on its own problem, but also naming a problem no result it rests on has.
                        new ComposedReview.InlineNote(
                                "Route the call through the checked helper.",
                                List.of(id(lapse)),
                                List.of(problemId(lapse), problemId(other)),
                                new ComposedReview.ResolvedAnchor(
                                        id(lapse), problemId(lapse), 0, PATH, "NEW", 10, 10))),
                List.of());

        assertThat(decide(review, List.of(lapse, other), List.of(), List.of(lapse, other)))
                .isEqualTo(new AdmittedDelivery.Automatic(null, Set.of()));
    }

    @Test
    void shouldClearEveryPublicBodyButKeepWithholdingWhenBodiesWouldReachDifferentPeople() {
        ValidatedResult authors = problem("checks-tokens-first");
        ValidatedResult reviewers =
                practiceResult("answers-review-threads", Outcome.NOT_MET, "scm.pull-request.diff", OTHER_PERSON);
        ValidatedResult quiet = problem("describe-what-and-why");

        DeliveryContent content = automatic(decide(
                new ComposedReview(
                        new ComposedReview.Summary(SUMMARY, List.of(id(authors))),
                        List.of(note("Answer the open thread.", reviewers)),
                        List.of(new ComposedReview.Withheld(
                                List.of(id(quiet)), ComposedFeedbackUnit.WithholdReason.ALREADY_SAID))),
                List.of(authors, reviewers, quiet),
                List.of(),
                List.of(authors, reviewers, quiet)));

        assertThat(content.mrNote()).isNull();
        assertThat(content.diffNotes()).isEmpty();
        assertThat(content.withheld())
                .containsExactly(new WithheldResult(
                        Objects.requireNonNull(quiet.occurrenceKey()), FeedbackSuppressionReason.COMPOSER_WITHHELD));
    }

    @Test
    void shouldRefuseAWholeWithholdingDecisionThatNamesAStrengthOrAPrivateResult() {
        ValidatedResult lapse = problem("checks-tokens-first");
        ValidatedResult strength = practiceResult("ships-a-preview", Outcome.MET, "scm.pull-request.diff", AUTHOR);
        ValidatedResult fromHistory =
                practiceResult("describe-what-and-why", Outcome.NOT_MET, "hephaestus.feedback-history", AUTHOR);

        AdmittedDelivery delivery = decide(
                new ComposedReview(
                        null,
                        List.of(),
                        List.of(
                                new ComposedReview.Withheld(
                                        List.of(id(lapse), id(strength)),
                                        ComposedFeedbackUnit.WithholdReason.BELOW_BAR),
                                new ComposedReview.Withheld(
                                        List.of(id(lapse), id(fromHistory)),
                                        ComposedFeedbackUnit.WithholdReason.BELOW_BAR))),
                List.of(lapse, strength, fromHistory),
                List.of(),
                List.of(lapse, strength, fromHistory),
                Set.of(
                        Objects.requireNonNull(lapse.practiceResultId()),
                        Objects.requireNonNull(strength.practiceResultId())));

        assertThat(delivery).isEqualTo(new AdmittedDelivery.Automatic(null, Set.of()));
    }

    @Test
    void shouldProposeTheWholeReviewWhenAnyAdmittedBodyRestsOnAResultAwaitingApproval() {
        ValidatedResult automaticLapse = problem("checks-tokens-first");
        ValidatedResult gated = problem("describe-what-and-why");

        AdmittedDelivery delivery = decide(
                new ComposedReview(
                        new ComposedReview.Summary(SUMMARY, List.of(id(gated))),
                        List.of(note("Call the checked helper.", automaticLapse)),
                        List.of()),
                List.of(automaticLapse, gated),
                List.of(gated),
                List.of(automaticLapse));

        assertThat(delivery).isInstanceOf(AdmittedDelivery.Proposed.class);
        DeliveryContent proposed = ((AdmittedDelivery.Proposed) delivery).content();
        assertThat(proposed.mrNote()).isEqualTo(SUMMARY);
        assertThat(proposed.diffNotes()).hasSize(1);
    }

    @Test
    void shouldNotBuildABodyWhoseSupportIsEmptyOrWhoseAnchorItDoesNotRestOn() {
        ValidatedResult lapse = problem("checks-tokens-first");
        ValidatedResult other = problem("describe-what-and-why");
        ComposedReview.ResolvedAnchor anchor =
                new ComposedReview.ResolvedAnchor(id(lapse), problemId(lapse), 0, PATH, "NEW", 10, 10);

        assertThatThrownBy(() -> new ComposedReview.Summary(SUMMARY, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new ComposedReview.InlineNote("A note.", List.of(id(other)), List.of(problemId(lapse)), anchor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new ComposedReview.InlineNote("A note.", List.of(id(lapse)), List.of(problemId(other)), anchor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ComposedReview.Withheld(List.of(), ComposedFeedbackUnit.WithholdReason.BELOW_BAR))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
