package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.agent.handler.composition.ComposedReview;
import de.tum.cit.aet.hephaestus.agent.handler.composition.FeedbackCompositionResultParser;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class PublicReviewCompositionContractTest extends BaseUnitTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final FeedbackCompositionResultParser parser = new FeedbackCompositionResultParser();

    private PracticeResult practiceResult(Outcome outcome, String source) {
        return PracticeResult.builder()
                .scopeKey("test-scope")
                .id(UUID.randomUUID())
                .outcome(outcome)
                .evidence(mapper.readTree("{ \"citations\": [{ \"sourceKind\": \"%s\" }] }".formatted(source)))
                .build();
    }

    private AgentJob job(String review) {
        AgentJob job = new AgentJob();
        job.setId(UUID.randomUUID());
        job.setOutput(
                mapper.readTree("{ \"feedback\": { \"contractVersion\": 2, \"review\": %s } }".formatted(review)));
        return job;
    }

    @Test
    void shouldRefuseEmptyPublicCompositionWhenAnEligibleNegativeHasNoDecision() {
        PracticeResult negative = practiceResult(Outcome.NOT_MET, "scm.pull-request.core");
        assertThatThrownBy(() -> PullRequestReviewHandler.reviewToDeliver(
                        parser,
                        job("{}"),
                        List.of(negative),
                        Set.of(negative.getId()),
                        Set.of(FeedbackChannel.IN_CONTEXT)))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("undecided");
    }

    @Test
    void shouldRefuseAPartialPublicCompositionThatLeavesAnotherNegativeUndecided() {
        PracticeResult said = practiceResult(Outcome.NOT_MET, "scm.pull-request.core");
        PracticeResult missing = practiceResult(Outcome.NOT_MET, "scm.pull-request.core");
        AgentJob job = job(
                "{ \"summary\": { \"body\": \"The description needs its motivation.\", \"basedOn\": [{ \"practiceResultId\": \"%s\" }] } }"
                        .formatted(said.getId()));
        stage(job, said, null);
        assertThatThrownBy(() -> PullRequestReviewHandler.reviewToDeliver(
                        parser,
                        job,
                        List.of(said, missing),
                        Set.of(said.getId(), missing.getId()),
                        Set.of(FeedbackChannel.IN_CONTEXT)))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("undecided");
    }

    @Test
    void shouldDeliverTheReviewWhenEveryEligibleNegativeIsSaidThroughAProblemOrWithheld() {
        PracticeResult said = practiceResult(Outcome.NOT_MET, "scm.pull-request.core");
        PracticeResult quiet = practiceResult(Outcome.NOT_MET, "scm.pull-request.core");
        UUID problem = UUID.randomUUID();
        AgentJob job = job("""
                { "summary": { "body": "The description needs its motivation.",
                               "basedOn": [{ "practiceResultId": "%s", "observationId": "%s" }] },
                  "withheld": [{ "basedOn": [{ "practiceResultId": "%s" }], "reason": "BELOW_BAR" }] }
                """.formatted(said.getId(), problem, quiet.getId()));
        stage(job, said, problem);
        stage(job, quiet, null);

        ComposedReview review = PullRequestReviewHandler.reviewToDeliver(
                parser,
                job,
                List.of(said, quiet),
                Set.of(said.getId(), quiet.getId()),
                Set.of(FeedbackChannel.IN_CONTEXT));

        ComposedReview.Summary summary = review.summary();
        assertThat(summary).isNotNull();
        assertThat(summary.basedOn()).containsExactly(said.getId().toString());
        assertThat(summary.observations()).containsExactly(problem.toString());
        assertThat(review.decidedResultIds())
                .containsExactlyInAnyOrder(
                        said.getId().toString(), quiet.getId().toString());
    }

    /** Stages the result in the job's {@code results} list, with one problem when {@code problem} is given. */
    private static void stage(AgentJob job, PracticeResult practiceResult, @Nullable UUID problem) {
        ObjectNode output = (ObjectNode) Objects.requireNonNull(job.getOutput());
        ObjectNode feedback = (ObjectNode) output.path("feedback");
        ArrayNode results =
                feedback.has("results") ? (ArrayNode) feedback.get("results") : feedback.putArray("results");
        ObjectNode staged = results.addObject()
                .put("practiceResultId", practiceResult.getId().toString())
                .put("practiceSlug", "describe-what-and-why")
                .put("outcome", practiceResult.getOutcome().name());
        ArrayNode observations = staged.putArray("observations");
        if (problem != null) {
            observations.addObject().put("observationId", problem.toString()).put("anchorable", false);
        }
    }

    @Test
    void shouldKeepAllMetSilenceAndExcludePrivateAndUndecidedResultsFromCompleteness() {
        List<PracticeResult> practiceResults = List.of(
                practiceResult(Outcome.MET, "scm.pull-request.core"),
                practiceResult(Outcome.NOT_MET, "hephaestus.observation-history"),
                practiceResult(Outcome.UNDETERMINED, "scm.pull-request.core"));
        assertThat(PullRequestReviewHandler.reviewToDeliver(
                        parser,
                        job("{}"),
                        practiceResults,
                        Set.of(practiceResults.getFirst().getId()),
                        Set.of(FeedbackChannel.IN_CONTEXT)))
                .isEqualTo(ComposedReview.empty());
    }

    @Test
    void shouldSkipPublicCompositionWhenItsChannelWasNotStaged() {
        assertThat(PullRequestReviewHandler.reviewToDeliver(
                        parser,
                        job("null"),
                        List.of(practiceResult(Outcome.NOT_MET, "scm.pull-request.core")),
                        Set.of(),
                        Set.of(FeedbackChannel.IN_APP)))
                .isEqualTo(ComposedReview.empty());
    }
}
