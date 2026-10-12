package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.evidence.internal.ClasspathArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeProgramKind;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeProgramPath;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptRunDTO;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptRunStatus;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptStopReason;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

class ReviewOutcomeLookupAdapterTest extends BaseUnitTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ReviewOutcomeLookupAdapter adapter = new ReviewOutcomeLookupAdapter(
            mock(AgentJobRepository.class),
            mock(AgentJobPracticeScriptRunRepository.class),
            new ClasspathArtifactSourceCatalogRegistry(mapper, Clock.systemUTC()));

    @Test
    void shouldNameASourceThatWasNotReadByItsDisplayName() {
        JsonNode decision = mapper.readTree("""
                {"sourceChecks": [
                  {"sourceKind": "scm.pull-request.diff", "meetsRequirements": false,
                   "reasonCodes": ["SOURCE_INCOMPLETE"]},
                  {"sourceKind": "scm.pull-request.core", "meetsRequirements": true, "reasonCodes": []}
                ]}
                """);

        assertThat(adapter.blockers(decision)).containsExactly("The review captured only part of “Code changes”.");
    }

    @Test
    void shouldPhraseEverySourceProblemSoAPluralNameReadsRight() {
        JsonNode decision = mapper.readTree("""
                {"sourceChecks": [
                  {"sourceKind": "scm.pull-request.diff", "meetsRequirements": false,
                   "reasonCodes": ["SOURCE_NOT_AVAILABLE", "SOURCE_EMPTY", "SOMETHING_NEW"]}
                ]}
                """);

        assertThat(adapter.blockers(decision))
                .containsExactly(
                        "The review did not capture “Code changes”.",
                        "The review captured nothing from “Code changes”.",
                        "The review could not read “Code changes”.");
    }

    /**
     * A practice that declares it is not reviewed automatically was stopped by policy: its sentence says so
     * and never lands among the blockers, which would report it as missing evidence.
     */
    @Test
    void shouldSayADeclaredLimitationInItsOwnSentenceAndNeverAsABlocker() {
        JsonNode humanReview = mapper.readTree("""
                {"reasonCodes": ["DECLARED_EVIDENCE_INSUFFICIENT"], "sourceChecks": []}
                """);
        JsonNode guidanceOnly = mapper.readTree("""
                {"reasonCodes": ["NO_AUTOMATED_REVIEW"], "sourceChecks": []}
                """);

        assertThat(ReviewOutcomeLookupAdapter.limitation(humanReview))
                .isEqualTo("This practice needs human review, so no automatic review covers it.");
        assertThat(ReviewOutcomeLookupAdapter.limitation(guidanceOnly))
                .isEqualTo("This practice is guidance only, so no automatic review covers it.");
        assertThat(adapter.blockers(humanReview)).isEmpty();
        assertThat(adapter.blockers(guidanceOnly)).isEmpty();
        assertThat(ReviewOutcomeLookupAdapter.limitation(mapper.readTree("{\"reasonCodes\": []}")))
                .isNull();
    }

    /**
     * One unreadable entry voids the whole record: a partly read record would attribute some practices and leave
     * the rest reading as unreached, from the same run.
     */
    @Test
    void shouldReadTheAnsweringReviewsOnlyFromAWhollyReadableRecord() {
        UUID ready = UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111");
        String valid = "[{\"practiceSlug\":\"tests\",\"revisionId\":7,\"reviewId\":\"" + ready + "\"},"
                + "{\"practiceSlug\":\"naming\",\"revisionId\":8,\"reviewId\":\"" + ready + "\"}]";
        String oneMalformed = "[{\"practiceSlug\":\"tests\",\"revisionId\":7,\"reviewId\":\"" + ready + "\"},"
                + "{\"practiceSlug\":\"naming\",\"revisionId\":8,\"reviewId\":\"not-a-review\"}]";

        assertThat(ReviewOutcomeLookupAdapter.answeredBy(valid)).isEqualTo(Map.of("tests", ready, "naming", ready));
        assertThat(ReviewOutcomeLookupAdapter.answeredBy(oneMalformed)).isEmpty();
        assertThat(ReviewOutcomeLookupAdapter.answeredBy("{not json")).isEmpty();
    }

    /** Only a terminal attempt records runs, so a later attempt's runs replace an earlier one's, never mix. */
    @Test
    void shouldReadOnlyTheLastRecordedAttemptWhenAJobRecordedSeveral() {
        AgentJob retried = new AgentJob();
        retried.setId(UUID.randomUUID());
        AgentJob once = new AgentJob();
        once.setId(UUID.randomUUID());

        var byJob = ReviewOutcomeLookupAdapter.latestAttemptRuns(List.of(
                run(retried, 0, "tests", PracticeScriptRunStatus.FAILED, PracticeScriptStopReason.ERROR),
                run(retried, 0, "naming", PracticeScriptRunStatus.OK, null),
                run(retried, 1, "tests", PracticeScriptRunStatus.SKIPPED, PracticeScriptStopReason.UNAVAILABLE),
                run(once, 0, "tests", PracticeScriptRunStatus.FAILED, PracticeScriptStopReason.DEADLINE)));

        assertThat(byJob.get(retried.getId()))
                .containsOnlyKeys("tests")
                .extractingByKey("tests")
                .extracting(PracticeScriptRunDTO::status)
                .isEqualTo(PracticeScriptRunStatus.SKIPPED);
        assertThat(byJob.get(once.getId()))
                .extractingByKey("tests")
                .isEqualTo(new PracticeScriptRunDTO(
                        PracticeScriptRunStatus.FAILED,
                        PracticeProgramKind.BUILTIN,
                        PracticeProgramPath.CODE,
                        PracticeScriptStopReason.DEADLINE,
                        List.of()));
    }

    private static AgentJobPracticeScriptRun run(
            AgentJob job,
            int attempt,
            String slug,
            PracticeScriptRunStatus status,
            @Nullable PracticeScriptStopReason stopReason) {
        return new AgentJobPracticeScriptRun(
                job,
                attempt,
                slug,
                7L,
                status,
                PracticeProgramKind.BUILTIN,
                null,
                stopReason,
                JsonNodeFactory.instance
                        .objectNode()
                        .put("review", 0)
                        .put("chat", 0)
                        .put("decision", 0)
                        .put("embedding", 0)
                        .put("reranking", 0),
                null,
                JsonNodeFactory.instance.arrayNode(),
                Instant.EPOCH,
                null,
                null);
    }

    @Test
    void shouldFallBackToGenericWordsForASourceNoCatalogueDeclares() {
        JsonNode decision = mapper.readTree("""
                {"reasonCodes": ["DECLARED_EVIDENCE_INSUFFICIENT"],
                 "sourceChecks": [
                  {"sourceKind": "scm.pull-request.retired", "meetsRequirements": false,
                   "reasonCodes": ["SOURCE_NOT_AVAILABLE"]},
                  {"sourceKind": "Not A Kind", "meetsRequirements": false, "reasonCodes": ["SOURCE_EMPTY"]},
                  {"meetsRequirements": false, "reasonCodes": [null]}
                ]}
                """);

        assertThat(adapter.blockers(decision))
                .containsExactly(
                        "The review did not capture a required source.",
                        "The review captured nothing from a required source.",
                        "The review could not read a required source.")
                .allSatisfy(blocker -> assertThat(blocker).doesNotContain("scm.", "Not A Kind", "_"));
    }
}
