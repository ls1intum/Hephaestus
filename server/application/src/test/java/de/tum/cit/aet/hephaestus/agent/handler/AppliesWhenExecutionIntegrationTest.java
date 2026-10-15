package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.AppliesWhenEvaluator;
import de.tum.cit.aet.hephaestus.agent.context.InsufficientEvidenceException;
import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.context.JobFolderIndex;
import de.tum.cit.aet.hephaestus.agent.context.PreparedEvidence;
import de.tum.cit.aet.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.cit.aet.hephaestus.agent.context.providers.ReviewHistoryContentSource;
import de.tum.cit.aet.hephaestus.agent.handler.spi.PreparedJobInputs;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobAssignments;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.AdmissionRequestDTO;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.evidence.CapturedSource;
import de.tum.cit.aet.hephaestus.evidence.ClasspathSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceAbsenceReason;
import de.tum.cit.aet.hephaestus.evidence.SourceCapture;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureFacts;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContentState;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.core.framework.ItemDescriptorRegistry;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSource;
import de.tum.cit.aet.hephaestus.practices.AppliesWhen;
import de.tum.cit.aet.hephaestus.practices.AppliesWhenClause;
import de.tum.cit.aet.hephaestus.practices.DefinitionTestFixtures;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeReadSource;
import de.tum.cit.aet.hephaestus.practices.PracticeReads;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.review.AbstractResultAdmissionIntegrationTest;
import de.tum.cit.aet.hephaestus.practices.review.ReviewAdmissionData;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData;
import de.tum.cit.aet.hephaestus.practices.review.ReviewPracticeRepository;
import de.tum.cit.aet.hephaestus.practices.spi.AdmittedResultQuery;
import de.tum.cit.aet.hephaestus.practices.spi.HistoryCustody;
import de.tum.cit.aet.hephaestus.practices.spi.ProcessingStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReadNeed;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ResultStore;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentStore;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRuntimeSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

class AppliesWhenExecutionIntegrationTest extends AbstractResultAdmissionIntegrationTest {
    private static final String OTHERWISE = "The change touches no Swift file.";
    private static final SourceKind DIFF = new SourceKind("scm.pull-request.diff");

    @Autowired
    ReviewIntegrationData data;

    @Autowired
    ReviewAdmissionData admissions;

    @Autowired
    ReviewJobRepository jobs;

    @Autowired
    ReviewJobAssignments assignments;

    @Autowired
    ReviewAssignmentStore members;

    @Autowired
    AdmittedResultQuery proof;

    @Autowired
    ResultStore store;

    @Autowired
    ResultRepository results;

    @Autowired
    ReviewPracticeRepository practices;

    @Autowired
    ItemDescriptorRegistry descriptors;

    @Autowired
    JsonMapper mapper;

    @Autowired
    ClasspathSourceCatalogRegistry catalogs;

    private final JobEvidenceFiles files = mock(JobEvidenceFiles.class);
    private final WorkspaceContextBuilder context = mock(WorkspaceContextBuilder.class);
    private final ItemSource source = mock(ItemSource.class);

    @BeforeEach
    void setup() {
        databaseTestUtils.cleanDatabase();
        when(custody.find(anyLong(), anyList()))
                .thenAnswer(call -> new HistoryCustody(true, List.of(), List.of(), call.getArgument(1)));
    }

    private ReviewAdmissionData.Pending pending() {
        var d = DefinitionTestFixtures.definition();
        var definition = new PracticeDefinition(
                d.name(),
                d.reviewOn(),
                new AppliesWhen(List.of(new AppliesWhenClause(List.of("**/*.swift"), null, null)), OTHERWISE),
                d.about(),
                new PracticeReads("2.0.0", List.of(new PracticeReadSource(DIFF, ReadNeed.REQUIRED))),
                d.reviewedBy(),
                d.criteria(),
                d.script(),
                d.scan(),
                d.lineNotes(),
                d.guidance());
        var scenario = data.scenario(definition);
        var pending =
                admissions.pending(scenario, Instant.now(), scenario.account().getId());
        admissions.running(pending, 1);
        when(runtime.runtime(scenario.workspace().getId(), pending.review().getId()))
                .thenReturn(Optional.of(new ReviewRuntimeSnapshot(List.of(new ReviewJobSnapshot(
                        pending.jobId(),
                        ReviewJobStatus.RUNNING,
                        1,
                        ProcessingStatus.PENDING,
                        Instant.now(),
                        Instant.now(),
                        null,
                        null,
                        null,
                        "test-trace")))));
        return pending;
    }

    private DefaultReviewExecutionHandler handler(ReviewJob job, boolean available, Set<String> paths) {
        var assignment = assignments.load(job).getFirst();
        var snapshot = mock(ItemSnapshot.class);
        when(source.supports(assignment.item())).thenReturn(true);
        when(source.pin(assignment.item(), assignment.version())).thenReturn(snapshot);
        SourceCaptureState state = available
                ? new SourceCaptureState.Available(
                        SourceContentState.NON_EMPTY,
                        SourceCompleteness.COMPLETE,
                        new SourceCaptureFacts(Instant.now(), null, null, "base:head"))
                : new SourceCaptureState.Unavailable(SourceAbsenceReason.NO_PROVIDER);
        var manifest = new JobFolderIndex(
                new SourceContractVersion("2.0.0"),
                "a".repeat(64),
                assignment.item().kind().value(),
                Instant.now(),
                List.of(new SourceCapture(
                        DIFF,
                        state,
                        EvidenceSnapshotFixtures.citation(DIFF),
                        available
                                ? List.of(new CapturedSource(
                                        "context/change.json", "application/json", "a".repeat(64), 1))
                                : List.of())),
                List.of(),
                List.of(),
                List.of());
        var captured = available
                ? Map.of(
                        "context/change.json",
                        mapper.writeValueAsBytes(mapper.createObjectNode()
                                .put("base_sha", "a".repeat(40))
                                .put("head_sha", "b".repeat(40))
                                .put("diff", "+changed();\n")
                                .set("changed_paths", mapper.valueToTree(paths))))
                : Map.<String, byte[]>of();
        when(context.prepare(eq(snapshot), any())).thenReturn(new PreparedEvidence(captured, manifest));
        when(files.prepare(eq(job), any(), any())).thenAnswer(call -> {
            PreparedEvidence evidence = call.getArgument(1);
            var capture = mapper.createObjectNode();
            capture.set("manifest", mapper.valueToTree(manifest));
            capture.set("readiness", mapper.valueToTree(call.getArgument(2)));
            capture.set("index", mapper.readTree(evidence.files().get(SandboxLayout.PRACTICES_PREFIX + "index.json")));
            job.setCapture(capture);
            return new PreparedJobInputs(Map.of(), List.of(), List.of(), manifest, call.getArgument(2));
        });
        if (available && paths.stream().anyMatch(path -> path.endsWith(".swift")))
            when(source.record(eq(snapshot), any()))
                    .thenReturn(mapper.createObjectNode().put("title", "Captured change"));
        return new DefaultReviewExecutionHandler(
                assignments,
                List.of(source),
                context,
                files,
                new PracticeCatalogInjector(mapper),
                new PracticeScriptInputs(mapper),
                mock(ReviewHistoryContentSource.class),
                members,
                descriptors,
                List.of(),
                mapper,
                Clock.systemUTC(),
                new AppliesWhenEvaluator(mapper),
                mock(ResultAdmissionService.class));
    }

    @Test
    void shouldAdmitDoesNotApplyWithTheAuthoredReasonWhenChangedPathsDoNotMatch() {
        var pending = pending();
        var job = jobs.findById(pending.jobId()).orElseThrow();
        var handler = handler(job, true, Set.of("src/Main.java"));
        try (var prepared = handler.prepareInputs(job)) {
            assertThat(prepared.folderIndex()).isNotNull();
            assertThat(assignments.admissible(job)).isEmpty();
            assertThat(job.getCapture()).isNotNull();
            assertThat(Objects.requireNonNull(job.getCapture()).path("index").isEmpty())
                    .isTrue();
            var output = new ReviewOutputService(
                    assignments, mock(CandidateEvidenceVerifier.class), store, mapper, files, catalogs, members);
            output.prepare(job, new AdmissionRequestDTO(3, job.getReviewId(), job.getId(), 1, List.of()))
                    .record(job);
        }
        var resultId = Objects.requireNonNull(job.getCapture())
                .path("admission")
                .path("resultIds")
                .get(0)
                .asString();
        var recorded = results.findById(UUID.fromString(resultId)).orElseThrow();
        assertThat(recorded.getOutcome()).isEqualTo(ResultOutcome.DOES_NOT_APPLY);
        assertThat(recorded.getExplanation()).isEqualTo(OTHERWISE);
        assertThat(recorded.getPracticeVersionId())
                .isEqualTo(pending.scenario().practice().getCurrentVersion().getId());
    }

    @Test
    void shouldStageThePracticeForReviewWhenChangedPathsMatch() {
        var pending = pending();
        var job = jobs.findById(pending.jobId()).orElseThrow();
        try (var prepared = handler(job, true, Set.of("src/App.swift")).prepareInputs(job)) {
            assertThat(prepared.folderIndex()).isNotNull();
            assertThat(assignments.admissible(job)).hasSize(1);
            assertThat(job.getCapture()).isNotNull();
            assertThat(Objects.requireNonNull(job.getCapture()).path("index").size())
                    .isEqualTo(1);
        }
    }

    @Test
    void shouldRecordTheCaptureFailureWithoutAnInapplicabilityResultWhenTheSourceIsUnavailable() {
        var pending = pending();
        var job = jobs.findById(pending.jobId()).orElseThrow();
        var handler = handler(job, false, Set.of());
        assertThatThrownBy(() -> handler.prepareInputs(job)).isInstanceOf(InsufficientEvidenceException.class);
        var practice = practices
                .findByWorkspaceIdAndReviewIdAndPracticeId(
                        job.getWorkspaceId(),
                        job.getReviewId(),
                        pending.scenario().practice().getId())
                .orElseThrow();
        assertThat(practice.getReason()).isEqualTo("readiness_refused");
        assertThat(practice.getCaptureDecision()).isNotNull();
        assertThat(Objects.requireNonNull(practice.getCaptureDecision())
                        .path("applicability")
                        .isMissingNode())
                .isTrue();
        assertThat(Objects.requireNonNull(practice.getCaptureDecision())
                        .path("reasonCodes")
                        .toString())
                .contains("DECLARED_EVIDENCE_INSUFFICIENT");
        assertThat(job.getCapture()).isNotNull();
        assertThat(Objects.requireNonNull(job.getCapture())
                        .path("readiness")
                        .path("applicability")
                        .isEmpty())
                .isTrue();
        assertThat(Objects.requireNonNull(job.getCapture())
                        .path("manifest")
                        .path("sources")
                        .get(0)
                        .path("state")
                        .path("reasonCode")
                        .asString())
                .isEqualTo("NO_PROVIDER");
        assertThat(assignments.admissible(job)).isEmpty();
        assertThat(proof.forAttempt(job.getWorkspaceId(), job.getReviewId(), job.getId(), job.getAttempt()))
                .isEmpty();
    }
}
