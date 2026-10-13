package de.tum.cit.aet.hephaestus.agent.job;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.catalog.ModelKind;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.config.FrozenModel;
import de.tum.cit.aet.hephaestus.agent.runtime.PiResultParser;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeProgramKind;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeProgramPath;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptRunDTO;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptRunStatus;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptStopReason;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOutcomeLookup;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.testconfig.WithUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import de.tum.cit.aet.hephaestus.workspace.spi.DataHandlingTier;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The program runs of a review, from the runner's report to the trace and the practice needs: one row per admitted
 * practice under the job's workspace, built-in or custom, and needs that come from the newest run of today's script
 * and from today's routing.
 */
class PracticeScriptIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final String NEEDS_URI = "/workspaces/{slug}/practices/practice-script-needs";
    private static final String REVIEW_URI = "/workspaces/{slug}/agents/jobs/{jobId}/practice-scripts";
    private static final Instant MONDAY = Instant.parse("2026-10-05T09:00:00Z");
    private static final String DIGEST = "0123456789abcdef".repeat(4);

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AgentJobRepository jobRepository;

    @Autowired
    private AgentJobPracticeScriptRunRepository runRepository;

    @Autowired
    private PiResultParser parser;

    @Autowired
    private ReviewOutcomeLookup reviewOutcomes;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private PracticeRevisionRepository revisionRepository;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private Workspace workspace;
    private Workspace otherWorkspace;
    private PracticeScriptRunRecorder recorder;

    @BeforeEach
    void setUpWorkspaces() {
        // The recorder runs in the worker role, which the integration context leaves off.
        recorder = new PracticeScriptRunRecorder(parser, runRepository, meterRegistry, objectMapper);
        workspace = createWorkspace(
                "practice-script-needs",
                "Practice script needs",
                "practice-script-needs-org",
                AccountType.ORG,
                persistUser("practice-script-needs-owner"));
        ensureAdminMembership(workspace);
        ensureWorkspaceMembership(workspace, persistUser("testuser"), WorkspaceMembership.WorkspaceRole.MEMBER);
        otherWorkspace = createWorkspace(
                "other-practice-script-needs",
                "Other practice script needs",
                "other-practice-script-needs-org",
                AccountType.ORG,
                persistUser("other-practice-script-needs-owner"));
    }

    @Test
    void shouldRecordEveryAdmittedPracticeOnceWhenTheReportNamesSomeOfThem() {
        Practice commentQuality = persistPractice(workspace, "comment-quality", "Comment quality", "decide()");
        Practice errorHandling = persistPractice(workspace, "error-handling", "Error handling", "scan()");
        Practice naming = persistPractice(workspace, "naming", "Naming", null);
        AgentJob job = persistJob(workspace, commentQuality, errorHandling, naming);
        double skippedBefore = scriptsCounted("skipped");

        record(
                job,
                MONDAY,
                skippedNeedingA("comment-quality", "decision"),
                calls(entry("naming", PracticeProgramKind.BUILTIN, "ok", null), 1, 0, 0)
                        .set(
                                "coverageReached",
                                objectMapper.readTree("{\"reads\":4,\"truncatedReads\":1,\"failedReads\":0}")));

        Map<String, AgentJobPracticeScriptRun> runs = runsOf(workspace, job);
        assertThat(runs).containsOnlyKeys("comment-quality", "error-handling", "naming");
        AgentJobPracticeScriptRun skipped = Objects.requireNonNull(runs.get("comment-quality"));
        assertThat(skipped.getStatus()).isEqualTo(PracticeScriptRunStatus.SKIPPED);
        assertThat(skipped.getProgramKind()).isEqualTo(PracticeProgramKind.CUSTOM);
        assertThat(skipped.getProgramDigest()).isEqualTo(DIGEST);
        assertThat(skipped.getStopReason()).isEqualTo(PracticeScriptStopReason.UNAVAILABLE);
        assertThat(skipped.path()).isNull();
        assertThat(skipped.getPracticeRevisionId())
                .isEqualTo(Objects.requireNonNull(commentQuality.getCurrentRevision())
                        .getId());
        assertThat(skipped.getFinishedAt()).isEqualTo(MONDAY);
        assertThat(skipped.attempt()).isEqualTo(job.getRetryCount());
        assertThat(skipped.getModels()).isEqualTo(objectMapper.readTree("""
            [{"purpose":"PRACTICE_DECISION","need":"REQUIRED","bound":false,"notRated":[]}]\
            """));
        AgentJobPracticeScriptRun builtin = Objects.requireNonNull(runs.get("naming"));
        assertThat(builtin.getStatus()).isEqualTo(PracticeScriptRunStatus.OK);
        assertThat(builtin.getProgramKind()).isEqualTo(PracticeProgramKind.BUILTIN);
        assertThat(builtin.getProgramDigest()).isNull();
        assertThat(builtin.getStopReason()).isNull();
        assertThat(builtin.getCalls()).isEqualTo(objectMapper.readTree("""
            {"review":1,"chat":0,"decision":0,"embedding":0,"reranking":0}\
            """));
        assertThat(builtin.getCoverageReached())
                .isEqualTo(objectMapper.readTree("{\"reads\":4,\"truncatedReads\":1,\"failedReads\":0}"));
        assertThat(builtin.path()).isEqualTo(PracticeProgramPath.REVIEW);
        assertThat(builtin.getModels()).isEqualTo(objectMapper.createArrayNode());
        AgentJobPracticeScriptRun unfinished = Objects.requireNonNull(runs.get("error-handling"));
        assertThat(unfinished.getStatus()).isEqualTo(PracticeScriptRunStatus.NOT_FINISHED);
        assertThat(unfinished.getProgramKind()).isEqualTo(PracticeProgramKind.CUSTOM);
        assertThat(unfinished.getModels()).isNull();
        assertThat(unfinished.getCalls()).isNull();
        assertThat(unfinished.getCoverageReached()).isNull();
        assertThat(runRepository.findByWorkspaceIdAndJobIdIn(otherWorkspace.getId(), List.of(job.getId())))
                .isEmpty();
        assertThat(scriptsCounted("skipped")).isEqualTo(skippedBefore + 1);
    }

    /** The program kind comes from the admitted revision, so a report that names another program records no run. */
    @Test
    void shouldRecordNoRunWhenTheReportNamesAnotherProgramThanTheRevisionHolds() {
        Practice naming = persistPractice(workspace, "naming", "Naming", null);
        AgentJob job = persistJob(workspace, naming);

        record(job, MONDAY, entry("naming", PracticeProgramKind.CUSTOM, "ok", null));

        assertThat(runsOf(workspace, job)).isEmpty();
    }

    @Test
    void shouldRecordNothingWhenTheRunnerWroteNoReport() {
        Practice practice = persistPractice(workspace, "comment-quality", "Comment quality", "decide()");
        AgentJob job = persistJob(workspace, practice);

        transactions.executeWithoutResult(
                status -> recorder.record(jobRepository.findById(job.getId()).orElseThrow(), null, Map.of(), MONDAY));

        assertThat(runsOf(workspace, job)).isEmpty();
    }

    @Test
    void shouldGiveTheTraceTheRunsOfTheReviewWhenAskedFromItsOwnWorkspaceOnly() {
        Practice commentQuality = persistPractice(workspace, "comment-quality", "Comment quality", "decide()");
        Practice errorHandling = persistPractice(workspace, "error-handling", "Error handling", "scan()");
        Practice naming = persistPractice(workspace, "naming", "Naming", null);
        AgentJob job = persistJob(workspace, commentQuality, errorHandling, naming);
        record(
                job,
                MONDAY,
                models(calls(entry("comment-quality", PracticeProgramKind.CUSTOM, "ok", null), 0, 0, 2), """
                    [{"slot":"decision","need":"optional","bound":true,"notRated":{"deadline":5}}]\
                    """),
                calls(entry("naming", PracticeProgramKind.BUILTIN, "ok", null), 1, 0, 0));

        ReviewOutcomeLookup.ReviewOutcome outcome = Objects.requireNonNull(reviewOutcomes
                .findByIds(workspace.getId(), List.of(job.getId()))
                .get(job.getId()));
        PracticeScriptRunDTO run =
                Objects.requireNonNull(outcome.practiceScriptByPracticeSlug().get("comment-quality"));

        assertThat(run.status()).isEqualTo(PracticeScriptRunStatus.OK);
        assertThat(run.programKind()).isEqualTo(PracticeProgramKind.CUSTOM);
        assertThat(run.path()).isEqualTo(PracticeProgramPath.MODELS);
        assertThat(run.stopReason()).isNull();
        assertThat(run.models().getFirst().notRated().getFirst().count()).isEqualTo(5);
        assertThat(outcome.practiceScriptByPracticeSlug().get("naming"))
                .isEqualTo(new PracticeScriptRunDTO(
                        PracticeScriptRunStatus.OK,
                        PracticeProgramKind.BUILTIN,
                        PracticeProgramPath.REVIEW,
                        null,
                        List.of()));
        // The trace lists what became of model calls; a program whose models are not known made none it can list.
        assertThat(outcome.practiceScriptByPracticeSlug().get("error-handling"))
                .isEqualTo(new PracticeScriptRunDTO(
                        PracticeScriptRunStatus.NOT_FINISHED, PracticeProgramKind.CUSTOM, null, null, List.of()));
        assertThat(reviewOutcomes.findByIds(otherWorkspace.getId(), List.of(job.getId())))
                .isEmpty();
    }

    @Test
    @WithAdminUser
    void shouldListTheNeedsOfTheNewestRunWhenNoBindingServesATier() {
        Practice commentQuality = persistPractice(workspace, "comment-quality", "Comment quality", "decide()");
        persistPractice(workspace, "fresh-script", "Fresh script", "scan()");
        persistPractice(workspace, "naming", "Naming", null);
        AgentJob older = persistJob(workspace, commentQuality);
        record(older, MONDAY, skippedNeedingA("comment-quality", "reranking"));
        AgentJob newer = persistJob(workspace, commentQuality);
        record(newer, MONDAY.plus(1, ChronoUnit.DAYS), needingADecisionModel("comment-quality"));
        // A run that did not finish reported no models, so it never stands for the script's needs.
        AgentJob unfinished = persistJob(workspace, commentQuality);
        record(unfinished, MONDAY.plus(2, ChronoUnit.DAYS));

        needs().jsonPath("$.length()")
                .isEqualTo(2)
                .jsonPath("$[0].practiceSlug")
                .isEqualTo("comment-quality")
                .jsonPath("$[0].practiceName")
                .isEqualTo("Comment quality")
                .jsonPath("$[0].scriptChanged")
                .isEqualTo(false)
                .jsonPath("$[0].asOf.jobId")
                .isEqualTo(newer.getId().toString())
                .jsonPath("$[0].needs.length()")
                .isEqualTo(1)
                .jsonPath("$[0].needs[0].purpose")
                .isEqualTo(AgentPurpose.PRACTICE_DECISION.name())
                .jsonPath("$[0].needs[0].need")
                .isEqualTo("REQUIRED")
                .jsonPath("$[0].needs[0].unmetTiers")
                .isEqualTo(List.of("IN_HOUSE", "CLOUD", "UNDECLARED"))
                .jsonPath("$[1].practiceSlug")
                .isEqualTo("fresh-script")
                .jsonPath("$[1].asOf")
                .doesNotExist()
                .jsonPath("$[1].scriptChanged")
                .isEqualTo(false)
                .jsonPath("$[1].needs.length()")
                .isEqualTo(0);
    }

    /** "Uses no model" is a claim, so only a run that reported the models can make it. */
    @Test
    @WithAdminUser
    void shouldNotClaimTheScriptUsesNoModelWhenNoRunReportedItsModels() {
        Practice unfinished = persistPractice(workspace, "comment-quality", "Comment quality", "decide()");
        Practice scanning = persistPractice(workspace, "naming", "Naming", "scan()");
        AgentJob job = persistJob(workspace, unfinished, scanning);
        record(job, MONDAY, entry("naming", PracticeProgramKind.CUSTOM, "ok", null));

        needs().jsonPath("$[0].practiceSlug")
                .isEqualTo("comment-quality")
                .jsonPath("$[0].asOf")
                .doesNotExist()
                .jsonPath("$[0].needs.length()")
                .isEqualTo(0)
                .jsonPath("$[1].practiceSlug")
                .isEqualTo("naming")
                .jsonPath("$[1].asOf.jobId")
                .isEqualTo(job.getId().toString())
                .jsonPath("$[1].needs.length()")
                .isEqualTo(0);
    }

    @Test
    @WithAdminUser
    void shouldShowNoNeedsWhenTheScriptChangedSinceTheNewestRun() {
        Practice practice = persistPractice(workspace, "comment-quality", "Comment quality", "decide()");
        record(persistJob(workspace, practice), MONDAY, needingADecisionModel("comment-quality"));

        practice.setPracticeScript("decide({ reranking: true })");
        appendRevision(practice);

        needs().jsonPath("$[0].practiceSlug")
                .isEqualTo("comment-quality")
                .jsonPath("$[0].scriptChanged")
                .isEqualTo(true)
                .jsonPath("$[0].asOf")
                .doesNotExist()
                .jsonPath("$[0].needs.length()")
                .isEqualTo(0);
    }

    @Test
    @WithAdminUser
    void shouldNeverAnswerFromAnotherWorkspacesRunWhenAPracticeSharesItsSlug() {
        persistPractice(workspace, "comment-quality", "Comment quality", "decide()");
        Practice theirs = persistPractice(otherWorkspace, "comment-quality", "Comment quality", "decide()");
        record(persistJob(otherWorkspace, theirs), MONDAY, needingADecisionModel("comment-quality"));

        needs().jsonPath("$.length()")
                .isEqualTo(1)
                .jsonPath("$[0].scriptChanged")
                .isEqualTo(false)
                .jsonPath("$[0].asOf")
                .doesNotExist()
                .jsonPath("$[0].needs.length()")
                .isEqualTo(0);
    }

    @Test
    @WithUser
    void shouldRefuseTheNeedsWhenAMemberAsks() {
        webTestClient
                .get()
                .uri(NEEDS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody(Void.class);
    }

    /** A workspace admin, not an instance admin: an instance admin may read every workspace. */
    @Test
    @WithMentorUser
    void shouldRefuseTheNeedsWhenTheAdminOfAnotherWorkspaceAsks() {
        ensureWorkspaceMembership(workspace, persistUser("mentor"), WorkspaceMembership.WorkspaceRole.ADMIN);
        webTestClient
                .get()
                .uri(NEEDS_URI, otherWorkspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody(Void.class);
    }

    @Test
    @WithAdminUser
    void shouldNotFindTheNeedsWhenTheWorkspaceDoesNotExist() {
        webTestClient
                .get()
                .uri(NEEDS_URI, "no-such-workspace")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody(Void.class);
    }

    @Test
    void shouldKeepOnlyTheFirstLineWhenAFailedScriptsErrorHasSeveral() {
        Practice practice = persistPractice(workspace, "comment-quality", "Comment quality", "decide()");
        AgentJob job = persistJob(workspace, practice);

        record(
                job,
                MONDAY,
                failed("comment-quality", "TypeError: answer is undefined\n    at decide (comment-quality.ts:4:9)"));

        assertThat(Objects.requireNonNull(runsOf(workspace, job).get("comment-quality"))
                        .getError())
                .isEqualTo("TypeError: answer is undefined");
    }

    @Test
    @WithAdminUser
    void shouldListTheCurrentAttemptsScriptsAndCallsWhenAnEarlierAttemptRecordedRuns() {
        Practice commentQuality = persistPractice(workspace, "comment-quality", "Comment quality", "decide()");
        Practice errorHandling = persistPractice(workspace, "error-handling", "Error handling", "scan()");
        AgentJob job = persistJob(workspace, commentQuality, errorHandling);
        job.setConfigSnapshot(snapshotWithDecisionModel(DataHandlingTier.CLOUD));
        job = jobRepository.save(job);
        record(
                job,
                MONDAY,
                entry("comment-quality", PracticeProgramKind.CUSTOM, "failed", "deadline"),
                entry("error-handling", PracticeProgramKind.CUSTOM, "ok", null));
        countCalls(job, 0, "comment-quality", ModelKind.DECISION, DataHandlingTier.CLOUD, 9, 900, 90);
        // A model that only the earlier attempt called is not a model of the attempt shown.
        countCalls(job, 0, "comment-quality", ModelKind.RERANKING, DataHandlingTier.CLOUD, 3, 30, 0);

        job.setRetryCount(1);
        job = jobRepository.save(job);
        record(
                job,
                MONDAY.plus(1, ChronoUnit.HOURS),
                failed("error-handling", "TypeError: answer is undefined"),
                models(calls(entry("comment-quality", PracticeProgramKind.CUSTOM, "ok", null), 0, 2, 4), """
                    [{"slot":"decision","need":"required","bound":true,"notRated":{"deadline":2}},
                     {"slot":"chat","need":"optional","bound":true,"notRated":{}}]\
                    """)
                        .put("durationMs", 4200));
        countCalls(job, 1, "comment-quality", ModelKind.DECISION, DataHandlingTier.CLOUD, 4, 120, 8);
        // Chat calls of a script are the review model's calls, which the review counts.
        countCalls(job, 1, "comment-quality", ModelKind.CHAT, DataHandlingTier.CLOUD, 2, 50, 5);

        reviewPracticeScript(workspace, job.getId())
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(2)
                .jsonPath("$[0].practiceSlug")
                .isEqualTo("comment-quality")
                .jsonPath("$[0].practiceName")
                .isEqualTo("Comment quality")
                .jsonPath("$[0].run.status")
                .isEqualTo("OK")
                .jsonPath("$[0].run.programKind")
                .isEqualTo("CUSTOM")
                .jsonPath("$[0].run.path")
                .isEqualTo("MODELS")
                .jsonPath("$[0].run.stopReason")
                .doesNotExist()
                .jsonPath("$[0].run.leads")
                .doesNotExist()
                .jsonPath("$[0].run.models[0].notRated[0].count")
                .isEqualTo(2)
                .jsonPath("$[0].models.length()")
                .isEqualTo(1)
                .jsonPath("$[0].models[0].purpose")
                .isEqualTo("PRACTICE_DECISION")
                .jsonPath("$[0].models[0].tier")
                .isEqualTo("CLOUD")
                .jsonPath("$[0].models[0].calls")
                .isEqualTo(4)
                .jsonPath("$[0].models[0].inputTokens")
                .isEqualTo(120)
                .jsonPath("$[0].models[0].outputTokens")
                .isEqualTo(8)
                .jsonPath("$[0].error")
                .doesNotExist()
                .jsonPath("$[0].durationMs")
                .isEqualTo(4200)
                .jsonPath("$[1].durationMs")
                .doesNotExist()
                .jsonPath("$[1].practiceSlug")
                .isEqualTo("error-handling")
                .jsonPath("$[1].run.status")
                .isEqualTo("FAILED")
                .jsonPath("$[1].run.stopReason")
                .isEqualTo("ERROR")
                .jsonPath("$[1].models.length()")
                .isEqualTo(0)
                .jsonPath("$[1].error")
                .isEqualTo("TypeError: answer is undefined");
    }

    @ParameterizedTest
    @CsvSource({"1,0,0,0", "2,1,120,8", "1,1,0,0"})
    @WithAdminUser
    void shouldOmitIncompleteTokenTotalsAndKeepConfirmedZeroWhenListingModelCalls(
            int calls, int reportedCalls, long input, long output) {
        Practice practice = persistPractice(workspace, "comment-quality", "Comment quality", "decide()");
        AgentJob job = persistJob(workspace, practice);
        record(job, MONDAY, entry("comment-quality", PracticeProgramKind.CUSTOM, "ok", null));
        countCalls(job, 0, "comment-quality", ModelKind.DECISION, DataHandlingTier.CLOUD, calls, input, output);
        jdbc.update(
                "UPDATE agent_job_practice_script_usage SET reported_calls = ? WHERE job_id = ?",
                reportedCalls,
                job.getId());

        var response = reviewPracticeScript(workspace, job.getId())
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].models[0].calls")
                .isEqualTo(calls);
        if (reportedCalls == calls) {
            response.jsonPath("$[0].models[0].inputTokens")
                    .isEqualTo(0)
                    .jsonPath("$[0].models[0].outputTokens")
                    .isEqualTo(0);
        } else {
            response.jsonPath("$[0].models[0].inputTokens")
                    .doesNotExist()
                    .jsonPath("$[0].models[0].outputTokens")
                    .doesNotExist();
        }
    }

    /**
     * A running attempt has recorded no runs yet. The runs of the attempt before it did not produce the review's
     * result, so the review shows none until the running attempt ends.
     */
    @Test
    @WithAdminUser
    void shouldListNoScriptsWhenTheCurrentAttemptHasNotEnded() {
        Practice practice = persistPractice(workspace, "comment-quality", "Comment quality", "decide()");
        AgentJob job = persistJob(workspace, practice);
        job.setConfigSnapshot(snapshotWithDecisionModel(DataHandlingTier.CLOUD));
        job = jobRepository.save(job);
        record(job, MONDAY, needingADecisionModel("comment-quality"));
        countCalls(job, 0, "comment-quality", ModelKind.DECISION, DataHandlingTier.CLOUD, 9, 900, 90);

        job.setRetryCount(1);
        job.setStatus(AgentJobStatus.RUNNING);
        job.setConfigSnapshot(snapshotWithDecisionModel(DataHandlingTier.IN_HOUSE));
        job = jobRepository.save(job);

        reviewPracticeScript(workspace, job.getId())
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(0);

        record(job, MONDAY.plus(1, ChronoUnit.HOURS), needingADecisionModel("comment-quality"));

        reviewPracticeScript(workspace, job.getId())
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].models[0].purpose")
                .isEqualTo("PRACTICE_DECISION")
                .jsonPath("$[0].models[0].tier")
                .isEqualTo("IN_HOUSE")
                .jsonPath("$[0].models[0].calls")
                .isEqualTo(0);
    }

    /** A script that did not finish declared no models, so only the proxy's rows know which model served it. */
    @Test
    @WithAdminUser
    void shouldShowTheTierOfTheModelThatServedTheCallsWhenTheScriptDidNotFinish() {
        Practice practice = persistPractice(workspace, "comment-quality", "Comment quality", "decide()");
        AgentJob job = persistJob(workspace, practice);
        job.setConfigSnapshot(snapshotWithDecisionModel(DataHandlingTier.CLOUD));
        job = jobRepository.save(job);
        countCalls(job, 0, "comment-quality", ModelKind.DECISION, DataHandlingTier.CLOUD, 9, 900, 90);
        record(job, MONDAY);

        reviewPracticeScript(workspace, job.getId())
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].run.status")
                .isEqualTo("NOT_FINISHED")
                .jsonPath("$[0].models.length()")
                .isEqualTo(1)
                .jsonPath("$[0].models[0].tier")
                .isEqualTo("CLOUD")
                .jsonPath("$[0].models[0].calls")
                .isEqualTo(9);
    }

    /** The runs are written in the attempt's terminal transaction, which a value PostgreSQL refuses would fail. */
    @Test
    void shouldStoreTheErrorOfAFailedScriptWhenItHoldsANul() {
        Practice practice = persistPractice(workspace, "comment-quality", "Comment quality", "decide()");
        AgentJob job = persistJob(workspace, practice);

        record(job, MONDAY, failed("comment-quality", "a\u0000b"));

        assertThat(Objects.requireNonNull(runsOf(workspace, job).get("comment-quality"))
                        .getError())
                .isEqualTo("a b");
    }

    @Test
    @WithAdminUser
    void shouldListNoScriptsWhenNoneRanInTheReview() {
        AgentJob job = persistJob(workspace);

        reviewPracticeScript(workspace, job.getId())
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(0);
    }

    @Test
    @WithUser
    void shouldRefuseTheScriptsOfAReviewWhenAMemberAsks() {
        Practice practice = persistPractice(workspace, "comment-quality", "Comment quality", "decide()");
        AgentJob job = persistJob(workspace, practice);
        record(job, MONDAY, needingADecisionModel("comment-quality"));

        reviewPracticeScript(workspace, job.getId())
                .expectStatus()
                .isForbidden()
                .expectBody(Void.class);
    }

    @Test
    @WithAdminUser
    void shouldNotFindTheScriptsWhenTheReviewBelongsToAnotherWorkspace() {
        Practice theirs = persistPractice(otherWorkspace, "comment-quality", "Comment quality", "decide()");
        AgentJob job = persistJob(otherWorkspace, theirs);
        record(job, MONDAY, needingADecisionModel("comment-quality"));

        reviewPracticeScript(workspace, job.getId()).expectStatus().isNotFound().expectBody(Void.class);
    }

    private WebTestClient.ResponseSpec reviewPracticeScript(Workspace scope, UUID jobId) {
        return webTestClient
                .get()
                .uri(REVIEW_URI, scope.getWorkspaceSlug(), jobId)
                .headers(TestAuthUtils.withCurrentUser())
                .exchange();
    }

    private JsonNode snapshotWithDecisionModel(DataHandlingTier tier) {
        FrozenModel decision = new FrozenModel(
                "openai-decisions", "https://models.example/v1", "decider", null, null, null, null, tier, null, null);
        return snapshot(tier, Map.of(ModelKind.DECISION, decision));
    }

    private JsonNode snapshot(DataHandlingTier tier, Map<ModelKind, FrozenModel> practiceScript) {
        return new ConfigSnapshot(
                        ConfigSnapshot.SCHEMA_VERSION,
                        "openai-completions",
                        "https://models.example/v1",
                        "reviewer",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        600,
                        false,
                        null,
                        tier,
                        null)
                .withPracticeScript(practiceScript)
                .toJson(objectMapper);
    }

    /** The calls that the proxy counted, with the tier of the model that served them. */
    private void countCalls(
            AgentJob job,
            int attempt,
            String practice,
            ModelKind kind,
            DataHandlingTier tier,
            int calls,
            long input,
            long output) {
        jdbc.update(
                "INSERT INTO agent_job_practice_script_usage (job_id, attempt, model_kind, practice_slug,"
                        + " workspace_id, data_handling_tier, calls, reported_calls, input_tokens, output_tokens) VALUES"
                        + " (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                job.getId(),
                attempt,
                kind.name(),
                practice,
                job.getWorkspace().getId(),
                tier.name(),
                calls,
                calls,
                input,
                output);
    }

    private WebTestClient.BodyContentSpec needs() {
        return webTestClient
                .get()
                .uri(NEEDS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody();
    }

    /**
     * An entry inside the report contract for a practice the job admitted: the program it admitted, no models, no
     * calls and no reads, and no duration.
     */
    private ObjectNode entry(String slug, PracticeProgramKind kind, String status, @Nullable String stopReason) {
        ObjectNode entry = objectMapper.createObjectNode();
        entry.put("practiceSlug", slug);
        ObjectNode program = entry.putObject("program");
        if (kind == PracticeProgramKind.CUSTOM) {
            program.put("kind", "custom").put("digest", DIGEST);
        } else {
            program.put("kind", "builtin").putNull("digest");
        }
        entry.putArray("models");
        entry.put("status", status);
        if (stopReason == null) {
            entry.putNull("stopReason");
        } else {
            entry.put("stopReason", stopReason);
        }
        calls(entry, 0, 0, 0);
        ObjectNode coverage = entry.putObject("coverageReached");
        Stream.of("reads", "truncatedReads", "failedReads").forEach(count -> coverage.put(count, 0));
        return entry;
    }

    private static ObjectNode calls(ObjectNode entry, int review, int chat, int decision) {
        entry.putObject("calls")
                .put("review", review)
                .put("chat", chat)
                .put("decision", decision)
                .put("embedding", 0)
                .put("reranking", 0);
        return entry;
    }

    private ObjectNode models(ObjectNode entry, String models) {
        return entry.set("models", objectMapper.readTree(models));
    }

    private ObjectNode failed(String slug, String error) {
        return entry(slug, PracticeProgramKind.CUSTOM, "failed", "error").put("error", error);
    }

    private ObjectNode needingADecisionModel(String slug) {
        return models(entry(slug, PracticeProgramKind.CUSTOM, "ok", null), """
            [{"slot":"decision","need":"required","bound":true,"notRated":{}}]\
            """);
    }

    private ObjectNode skippedNeedingA(String slug, String slot) {
        return models(entry(slug, PracticeProgramKind.CUSTOM, "skipped", "unavailable"), """
            [{"slot":"%s","need":"required","bound":false,"notRated":{}}]\
            """.formatted(slot));
    }

    private void record(AgentJob job, Instant finishedAt, ObjectNode... entries) {
        byte[] report = ("{\"schemaVersion\":2,\"truncated\":false,\"practices\":["
                        + Stream.of(entries).map(ObjectNode::toString).collect(Collectors.joining(","))
                        + "]}")
                .getBytes(UTF_8);
        transactions.executeWithoutResult(status -> {
            AgentJob current = jobRepository.findById(job.getId()).orElseThrow();
            recorder.record(
                    current,
                    report,
                    ConfigSnapshot.fromJson(current.getConfigSnapshot(), objectMapper)
                            .practiceScriptModels(),
                    finishedAt);
        });
    }

    private Map<String, AgentJobPracticeScriptRun> runsOf(Workspace scope, AgentJob job) {
        return runRepository.findByWorkspaceIdAndJobIdIn(scope.getId(), List.of(job.getId())).stream()
                .sorted(Comparator.comparing(AgentJobPracticeScriptRun::practiceSlug))
                .collect(Collectors.toMap(AgentJobPracticeScriptRun::practiceSlug, run -> run));
    }

    private double scriptsCounted(String status) {
        return meterRegistry
                .counter("agent.review.practice_scripts", "status", status)
                .count();
    }

    private Practice persistPractice(Workspace scope, String slug, String name, @Nullable String script) {
        Practice practice = new Practice();
        practice.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
        practice.setWorkspace(scope);
        practice.setSlug(slug);
        practice.setName(name);
        practice.setCriteria("Review the change");
        PracticeTestEvidence.configure(practice, ScmSignals.PULL_REQUEST_OPENED);
        practice.setAutonomy(PracticeAutonomy.AUTOMATIC);
        practice.setPracticeScript(script);
        return appendRevision(practiceRepository.save(practice));
    }

    private Practice appendRevision(Practice practice) {
        int number = revisionRepository
                .findFirstByPracticeIdOrderByRevisionNumberDesc(practice.getId())
                .map(latest -> latest.getRevisionNumber() + 1)
                .orElse(1);
        PracticeRevision revision = revisionRepository.save(new PracticeRevision(practice, number));
        practice.setCurrentRevision(revision);
        return practiceRepository.save(practice);
    }

    /** A review that admitted these practices at their current revisions, as its evidence snapshot records it. */
    private AgentJob persistJob(Workspace scope, Practice... admitted) {
        AgentJob job = new AgentJob();
        job.setWorkspace(scope);
        job.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setStatus(AgentJobStatus.COMPLETED);
        job.setConfigSnapshot(snapshot(DataHandlingTier.CLOUD, Map.of()));
        var snapshot = objectMapper.createObjectNode();
        var practices = snapshot.putArray("practices");
        for (Practice practice : admitted) {
            practices
                    .addObject()
                    .put("slug", practice.getSlug())
                    .put(
                            "revisionId",
                            Objects.requireNonNull(practice.getCurrentRevision())
                                    .getId());
        }
        job.setEvidenceSnapshot(snapshot);
        return jobRepository.save(job);
    }
}
