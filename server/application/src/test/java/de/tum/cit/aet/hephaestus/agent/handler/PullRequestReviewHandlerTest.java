package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.EvidencePlan;
import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.context.JobFolderIndex;
import de.tum.cit.aet.hephaestus.agent.context.JobFolderIndexBuilder;
import de.tum.cit.aet.hephaestus.agent.context.PreparedEvidence;
import de.tum.cit.aet.hephaestus.agent.context.PreparedJobInputsFixtures;
import de.tum.cit.aet.hephaestus.agent.context.ReviewCoalescedException;
import de.tum.cit.aet.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.cit.aet.hephaestus.agent.context.providers.PullRequestContentSource;
import de.tum.cit.aet.hephaestus.agent.context.providers.ReviewRepositoryPreparer;
import de.tum.cit.aet.hephaestus.agent.context.providers.ReviewRepositoryPreparer.PreparedReview;
import de.tum.cit.aet.hephaestus.agent.handler.ReviewOutputService.PreparedResults;
import de.tum.cit.aet.hephaestus.agent.handler.composition.FeedbackCompositionResultParser;
import de.tum.cit.aet.hephaestus.agent.handler.spi.AnsweredPractice;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ExistingDeliveryLookup;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmission;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmissionRequest;
import de.tum.cit.aet.hephaestus.agent.handler.spi.PreparedJobInputs;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ResultsRefusedException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.agent.task.TaskEnvelopeWriter;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.AutomatedReviewReadinessDecision;
import de.tum.cit.aet.hephaestus.evidence.AutomatedReviewReadinessReport;
import de.tum.cit.aet.hephaestus.evidence.SourceReadinessCheck;
import de.tum.cit.aet.hephaestus.integration.core.events.RepositoryRef;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmEventPayload;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReview.State;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.RepositoryKey;
import de.tum.cit.aet.hephaestus.practices.PracticePreconditionClause;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionService;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.result.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.reaction.ReactionRepository;
import de.tum.cit.aet.hephaestus.practices.review.GeneratedPathReviewDTO;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class PullRequestReviewHandlerTest extends BaseUnitTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private GitRepositoryManager gitRepositoryManager;

    @Mock
    private WorkspaceContextBuilder workspaceContextBuilder;

    @Mock
    private ReviewOutputService deliveryService;

    @Mock
    private FeedbackDeliveryService feedbackService;

    @Mock
    private PracticeResultRepository practiceResultRepository;

    @Mock
    private AnsweredPractices answeredPractices;

    private static final Long WORKSPACE_ID = 99L;

    private TaskEnvelopeWriter taskEnvelopeWriter;
    private PullRequestReviewHandler handler;

    @Mock
    private PublicReviewEligibility publicEligibility;

    /** The problems each persisted result was recorded with, by result id, as admission stored them. */
    private final Map<UUID, List<Observation>> problems = new HashMap<>();

    @Test
    void shouldPreservePreparationFailureWhenCaptureAbortFails() {
        var catalog = mock(PracticeCatalogInjector.class);
        var files = mock(JobEvidenceFiles.class);
        var job = new AgentJob();
        var failure = new IllegalStateException("source capture failed");
        var cleanupFailure = new IllegalStateException("capture close failed");
        when(catalog.resolveEligiblePractices(job, ArtifactKinds.PULL_REQUEST)).thenThrow(failure);
        doThrow(cleanupFailure).when(files).abortPersonCapture(job);
        var preparation = new PracticeReviewPreparation(
                workspaceContextBuilder,
                catalog,
                taskEnvelopeWriter,
                gitRepositoryManager,
                files,
                mock(PracticeRevisionService.class),
                answeredPractices);

        assertThatThrownBy(() -> preparation.prepare(
                        job,
                        ArtifactKinds.PULL_REQUEST,
                        new ContextRequest.PracticeReviewRequest(job),
                        () -> {
                            throw new AssertionError("No task can be prepared from failed evidence");
                        },
                        staged -> {
                            throw new AssertionError("Failed evidence cannot be staged");
                        }))
                .isSameAs(failure)
                .hasSuppressedException(cleanupFailure);
    }

    @BeforeEach
    void setUp() {
        lenient().when(publicEligibility.publicResultIds(any(), any())).thenAnswer(invocation -> {
            Collection<PracticeResult> rows = invocation.getArgument(1);
            return rows.stream().map(PracticeResult::getId).collect(Collectors.toSet());
        });
        lenient().when(deliveryService.recorded(any(), any())).thenAnswer(invocation -> {
            List<PracticeResult> rows = invocation.getArgument(1);
            return rows.stream()
                    .map(row ->
                            ReviewResultParser.ValidatedResult.of(row, problems.getOrDefault(row.getId(), List.of())))
                    .toList();
        });
        taskEnvelopeWriter = new TaskEnvelopeWriter(objectMapper);
        var practiceCatalogInjector = new PracticeCatalogInjector(
                objectMapper,
                practiceRepository,
                InContextDeliveryGateFixtures.workspaceDefaults(),
                mock(PracticeScriptInputs.class));
        handler = new PullRequestReviewHandler(
                objectMapper,
                new PracticeReviewPreparation(
                        workspaceContextBuilder,
                        practiceCatalogInjector,
                        taskEnvelopeWriter,
                        gitRepositoryManager,
                        PreparedJobInputsFixtures.freezer(),
                        mock(
                                PracticeRevisionService.class,
                                invocation -> ((Practice) invocation.getArgument(0)).getCurrentRevision()),
                        answeredPractices),
                new FeedbackCompositionResultParser(),
                deliveryService,
                feedbackService,
                new FeedbackResponseSuppressionFilter(
                        mock(PracticeResultRepository.class),
                        mock(ObservationRepository.class),
                        mock(ReactionRepository.class),
                        mock(FeedbackLedgerRecorder.class)),
                InContextDeliveryGateFixtures.gate(
                        practiceRepository, mock(PracticeResultRepository.class), mock(FeedbackLedgerRecorder.class)),
                practiceResultRepository,
                publicEligibility);
    }

    @Test
    void shouldFinishWithoutComposingFeedbackWhenResultsWereRefused() {
        var refused = new AgentJob();
        var metadata = objectMapper.createObjectNode();
        metadata.putObject(ResultAdmissionService.REFUSAL_METADATA_KEY)
                .put("reasonCode", ReviewOutputService.NO_ADMISSIBLE_RESULTS);
        refused.setMetadata(metadata);
        assertThatCode(() -> handler.deliver(refused)).doesNotThrowAnyException();
        verifyNoInteractions(feedbackService, practiceResultRepository, deliveryService);
    }

    @Test
    void shouldSettleARecoveredDeliveryFromItsOwnPackageRatherThanAMarker() {
        AgentJob job = jobWithMetadata(sampleJobMetadata());

        assertThat(handler.reconcilesDeliveryState()).isTrue();
        assertThat(handler.findExistingDelivery(job).kind()).isEqualTo(ExistingDeliveryLookup.Kind.UNKNOWN);
    }

    private PullRequestReviewSubmissionRequest sampleRequest() {
        var pullRequestData = new ScmEventPayload.PullRequestData(
                456L,
                42,
                "Fix authentication bug",
                "This PR fixes the login issue",
                Issue.State.OPEN,
                false,
                false,
                10,
                5,
                3,
                "https://github.com/owner/repo/pull/42",
                new RepositoryRef(123L, "owner/repo", "main"),
                789L,
                Instant.now(),
                Instant.now(),
                null,
                null,
                null);
        return new PullRequestReviewSubmissionRequest(
                pullRequestData, "feature/auth-fix", "abc123def456", "main", "a".repeat(40));
    }

    private ObjectNode sampleJobMetadata() {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("repository_id", 123L);
        metadata.put("repository_full_name", "owner/repo");
        metadata.put("pull_request_id", 456L);
        metadata.put("pr_number", 42);
        metadata.put("pr_url", "https://github.com/owner/repo/pull/42");
        metadata.put("commit_sha", "abc123def456");
        metadata.put("source_branch", "feature/auth-fix");
        metadata.put("target_branch", "main");
        return metadata;
    }

    private AgentJob jobWithMetadata(ObjectNode metadata) {
        var job = new AgentJob();
        job.setId(UUID.randomUUID());
        job.setMetadata(metadata);
        job.setEvidenceSnapshot(admittedPracticeSnapshot());
        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        job.setWorkspace(workspace);
        return job;
    }

    private static final String CHANGE_SHA = "b".repeat(64);

    /** Every fixture practice admitted, over a captured change. */
    private ObjectNode admittedPracticeSnapshot() {
        ObjectNode snapshot = EvidenceSnapshotFixtures.snapshot(objectMapper);
        EvidenceSnapshotFixtures.admittedPractice(snapshot, "pr-description-quality", 1);
        EvidenceSnapshotFixtures.admittedPractice(snapshot, "error-handling", 2);
        EvidenceSnapshotFixtures.admittedPractice(snapshot, "avoids-insecure-defaults-and-over-broad-permissions", 3);
        ObjectNode diff = EvidenceSnapshotFixtures.availableSource(
                snapshot, "scm.pull-request.diff", "a".repeat(40) + ":" + "b".repeat(40));
        EvidenceSnapshotFixtures.artifact(snapshot, diff, PullRequestContentSource.CHANGE_FILE, CHANGE_SHA);
        return snapshot;
    }

    private Practice createPractice(String slug, String name, String criteria) {
        Practice p = new Practice();
        p.setId((long) slug.hashCode());
        p.setSlug(slug);
        p.setName(name);
        p.setCriteria(criteria);
        p.setAutonomy(PracticeAutonomy.AUTOMATIC);
        PracticeTestEvidence.configure(p, ArtifactKinds.PULL_REQUEST);
        p.setAutomatedReviewPolicy(PracticeTestEvidence.forArtifact(ArtifactKinds.PULL_REQUEST));
        var revision = new PracticeRevision(p, 1);
        ReflectionTestUtils.setField(revision, "id", Math.abs((long) slug.hashCode()) + 1);
        p.setCurrentRevision(revision);
        return p;
    }

    private List<Practice> samplePractices() {
        return List.of(
                createPractice("pr-description-quality", "PR Description Quality", "criteria"),
                createPractice("error-handling", "Error Handling", "fallback criteria"));
    }

    private void stubDefaults() {
        lenient()
                .when(workspaceContextBuilder.prepare(
                        any(ContextRequest.PracticeReviewRequest.class), any(EvidencePlan.class)))
                .thenReturn(prepared(Map.of("context/metadata.json", "{}".getBytes(StandardCharsets.UTF_8))));
        lenient()
                .when(workspaceContextBuilder.prepareAutomatedReviewReadiness(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> readiness(invocation.getArgument(1)));
        lenient()
                .when(practiceRepository.findByWorkspaceIdAndArtifactKind(WORKSPACE_ID, ArtifactKinds.PULL_REQUEST))
                .thenReturn(samplePractices());
    }

    private PreparedEvidence prepared(Map<String, byte[]> files) {
        return new PreparedEvidence(files, mock(JobFolderIndex.class));
    }

    /** Every practice asked is ready, recorded the way the readiness check records it. */
    private JobFolderIndexBuilder.PreparedAutomatedReviewReadiness readiness(List<Practice> practices) {
        Instant now = Instant.parse("2026-08-03T10:00:00Z");
        return new JobFolderIndexBuilder.PreparedAutomatedReviewReadiness(
                practices,
                new AutomatedReviewReadinessReport(
                        ArtifactSourceCatalogRegistry.CURRENT_VERSION,
                        "0".repeat(64),
                        ArtifactKinds.PULL_REQUEST.value(),
                        now,
                        now,
                        practices.stream()
                                .map(practice -> new AutomatedReviewReadinessDecision(
                                        practice.getSlug(),
                                        now,
                                        true,
                                        List.of(),
                                        List.of(new SourceReadinessCheck(
                                                PracticePreconditionClause.DIFF_SOURCE,
                                                ArtifactSourceCatalogRegistry.CURRENT_VERSION,
                                                now,
                                                now,
                                                true,
                                                List.of()))))
                                .toList()));
    }

    @Nested
    class JobType {

        @Test
        void returnsPullRequestReview() {
            assertThat(handler.jobType()).isEqualTo(AgentJobType.PULL_REQUEST_REVIEW);
        }
    }

    @Nested
    class CreateSubmission {

        @Test
        void extractsMetadata() {
            JobSubmission submission = handler.createSubmission(sampleRequest());
            JsonNode metadata = submission.metadata();

            assertThat(metadata.get("repository_id").asLong()).isEqualTo(123L);
            assertThat(metadata.get("repository_full_name").asString()).isEqualTo("owner/repo");
            assertThat(metadata.get("pr_number").asInt()).isEqualTo(42);
            assertThat(metadata.get("commit_sha").asString()).isEqualTo("abc123def456");
            assertThat(metadata.path("base_ref_oid").asString()).isEqualTo("a".repeat(40));
            assertThat(metadata.path(PullRequestReviewHandler.RETAINED_RANGE_METADATA_KEY)
                            .isBoolean())
                    .isTrue();
            assertThat(metadata.path(PullRequestReviewHandler.RETAINED_RANGE_METADATA_KEY)
                            .asBoolean())
                    .isTrue();
            assertThat(metadata.get("title").asString()).isEqualTo("Fix authentication bug");
            assertThat(metadata.get("body").asString()).isEqualTo("This PR fixes the login issue");
            assertThat(submission.idempotencyKey()).isEqualTo("pr_review:owner/repo:42:manual:abc123def456");
        }

        @Test
        void recordsNoRetainedRangeWhenTheMirrorHadNoBase() {
            var base = sampleRequest();
            var request = new PullRequestReviewSubmissionRequest(
                    base.pullRequest(), base.headRefName(), base.headRefOid(), base.baseRefName(), null);

            JsonNode metadata = handler.createSubmission(request).metadata();

            assertThat(metadata.has("base_ref_oid")).isFalse();
            assertThat(metadata.has(PullRequestReviewHandler.RETAINED_RANGE_METADATA_KEY))
                    .isFalse();
        }

        @ParameterizedTest
        @CsvSource({"OPEN, false, true", "OPEN, true, false", "CLOSED, false, false", "MERGED, true, false"})
        void retainsOnlyTheOriginalOpenUnmergedAdmissionState(Issue.State state, boolean merged, boolean current) {
            var data = new ScmEventPayload.PullRequestData(
                    456L,
                    42,
                    "Title",
                    "Description",
                    state,
                    false,
                    merged,
                    10,
                    5,
                    3,
                    "https://github.com/owner/repo/pull/42",
                    new RepositoryRef(123L, "owner/repo", "main"),
                    789L,
                    Instant.now(),
                    Instant.now(),
                    null,
                    null,
                    null);
            var request =
                    new PullRequestReviewSubmissionRequest(data, "feature", "a".repeat(40), "main", "b".repeat(40));
            JsonNode value =
                    handler.createSubmission(request).metadata().path(PracticeResult.CURRENT_WORK_METADATA_KEY);
            assertThat(value.isBoolean()).isTrue();
            assertThat(value.asBoolean()).isEqualTo(current);
        }

        @Test
        void preservesSubmittedReviewSubjectAndIdentity() {
            var base = sampleRequest();
            var request = new PullRequestReviewSubmissionRequest(
                            base.pullRequest(),
                            base.headRefName(),
                            base.headRefOid(),
                            base.baseRefName(),
                            base.baseRefOid(),
                            ScmSignals.PULL_REQUEST_REVIEWED)
                    .forSubmittedReview(new ScmEventPayload.ReviewData(
                            100L, "Review body", State.COMMENTED, false, null, 200L, true, 456L, null, 123L));

            JobSubmission submission = handler.createSubmission(request);

            assertThat(submission.metadata().path("review_id").asLong()).isEqualTo(100L);
            assertThat(submission.metadata().path("about_user_id").asLong()).isEqualTo(200L);
            assertThat(submission.metadata().path("subject_role").asString()).isEqualTo("REVIEWER");
            assertThat(submission.idempotencyKey()).endsWith(":review-100");
        }

        @Test
        void rejectsWrongRequestType() {
            JobSubmissionRequest wrongType = new JobSubmissionRequest() {};
            assertThatThrownBy(() -> handler.createSubmission(wrongType))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Expected PullRequestReviewSubmissionRequest");
        }
    }

    @Nested
    class PrepareInputs {

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void shouldStageGeneratedPathPolicyWithUnmodifiedEvidenceForMixedAndGeneratedOnlyChanges(
                boolean generatedOnly) {
            stubDefaults();
            var key = new RepositoryKey(WORKSPACE_ID, 123L);
            var preparer = mock(ReviewRepositoryPreparer.class);
            when(preparer.prepare(any())).thenReturn(new PreparedReview(key, "head", "base"));
            byte[] raw = "{\"title\":\"Original evidence\"}".getBytes(StandardCharsets.UTF_8);
            when(workspaceContextBuilder.prepare(
                            any(ContextRequest.PracticeReviewRequest.class), any(EvidencePlan.class)))
                    .thenAnswer(invocation -> {
                        ContextRequest.PracticeReviewRequest request = invocation.getArgument(0);
                        request.preparation().prepare(preparer, request.job());
                        return prepared(Map.of("inputs/context/metadata.json", raw));
                    });
            when(gitRepositoryManager.changedPaths(key, "base", "head"))
                    .thenReturn(
                            generatedOnly
                                    ? Set.of("generated/client.ts")
                                    : Set.of("generated/client.ts", "src/service.ts"));
            var metadata = sampleJobMetadata();
            metadata.putArray("generated_path_patterns").add("generated/**");
            var files = PreparedJobInputsFixtures.files(handler.prepareInputs(jobWithMetadata(metadata)));
            var policy = objectMapper.readTree(files.get(GeneratedPathReviewDTO.INPUT_PATH));
            assertThat(policy.path("patterns").get(0).asString()).isEqualTo("generated/**");
            assertThat(policy.path("paths")).hasSize(1);
            assertThat(policy.path("paths").get(0).asString()).isEqualTo("generated/client.ts");
            assertThat(files.get("inputs/context/metadata.json")).isEqualTo(raw);
            assertThat(new String(
                            files.get(SandboxLayout.PRACTICES_PREFIX + "pr-description-quality.md"),
                            StandardCharsets.UTF_8))
                    .contains(
                            "Repository generated-path policy",
                            "NOT_APPLICABLE",
                            "excludes-generated-and-build-artifacts");
        }

        @Test
        void delegatesToWorkspaceContextBuilder() {
            stubDefaults();
            AgentJob job = jobWithMetadata(sampleJobMetadata());

            handler.prepareInputs(job);

            ArgumentCaptor<ContextRequest> captor = ArgumentCaptor.forClass(ContextRequest.class);
            verify(workspaceContextBuilder).prepare(captor.capture(), any(EvidencePlan.class));
            assertThat(captor.getValue()).isInstanceOf(ContextRequest.PracticeReviewRequest.class);
            assertThat(((ContextRequest.PracticeReviewRequest) captor.getValue()).job())
                    .isSameAs(job);
        }

        /**
         * The composer only runs when this file is staged, and the runner is the only thing that reads it.
         * Asserted over what preparation actually produces, because a test that calls the staging helper
         * directly passes whether or not a review ever calls it.
         */
        @Test
        void stagesTheRequestThatTurnsFeedbackCompositionOn() {
            stubDefaults();

            Map<String, byte[]> files =
                    PreparedJobInputsFixtures.files(handler.prepareInputs(jobWithMetadata(sampleJobMetadata())));

            assertThat(files)
                    .as("a live review composes feedback, so the request must reach the sandbox")
                    .containsKey(SandboxLayout.FEEDBACK_COMPOSITION_PATH);
            JsonNode request = objectMapper.readTree(
                    new String(files.get(SandboxLayout.FEEDBACK_COMPOSITION_PATH), StandardCharsets.UTF_8));
            assertThat(request.path("enabled").asBoolean()).isTrue();
            assertThat(request.path("inContextPlacementKinds"))
                    .as("a pull request has a diff, so a note may be placed on one")
                    .anySatisfy(kind -> assertThat(kind.asString()).isEqualTo("DIFF"));
        }

        @Test
        void mergesProviderFiles() {
            byte[] metadataBytes = "{\"pr_number\":42}".getBytes(StandardCharsets.UTF_8);
            when(workspaceContextBuilder.prepare(
                            any(ContextRequest.PracticeReviewRequest.class), any(EvidencePlan.class)))
                    .thenReturn(prepared(Map.of("context/metadata.json", metadataBytes)));
            when(workspaceContextBuilder.prepareAutomatedReviewReadiness(any(), any(), any(), any(), any()))
                    .thenAnswer(invocation -> readiness(invocation.getArgument(1)));
            when(practiceRepository.findByWorkspaceIdAndArtifactKind(WORKSPACE_ID, ArtifactKinds.PULL_REQUEST))
                    .thenReturn(samplePractices());

            Map<String, byte[]> files =
                    PreparedJobInputsFixtures.files(handler.prepareInputs(jobWithMetadata(sampleJobMetadata())));

            assertThat(files.get("context/metadata.json")).isEqualTo(metadataBytes);
        }

        @Test
        void writesTaskJsonEnvelope() throws Exception {
            stubDefaults();
            Map<String, byte[]> files =
                    PreparedJobInputsFixtures.files(handler.prepareInputs(jobWithMetadata(sampleJobMetadata())));

            assertThat(files).containsKey("task.json");
            JsonNode envelope = objectMapper.readTree(files.get("task.json"));
            assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(5);
            assertThat(envelope.get("workspaceId").asLong()).isEqualTo(WORKSPACE_ID);
            JsonNode task = envelope;
            assertThat(task.has("kind")).isFalse();
            assertThat(task.has("task")).isFalse();
            assertThat(task.has("paths")).isFalse();
            assertThat(task.get("pullRequestNumber").asInt()).isEqualTo(42);
            assertThat(task.get("repositoryFullName").asString()).isEqualTo("owner/repo");
            assertThat(task.get("prompt").asString()).contains("Review merge request #42");
        }

        @Test
        void injectsPracticeCatalog() {
            stubDefaults();
            Map<String, byte[]> files =
                    PreparedJobInputsFixtures.files(handler.prepareInputs(jobWithMetadata(sampleJobMetadata())));

            assertThat(files).containsKey("inputs/practices/index.json");
            assertThat(files).doesNotContainKey("inputs/practices/all-criteria.md");
            assertThat(files).containsKey("inputs/practices/pr-description-quality.md");
            assertThat(files).containsKey("inputs/practices/error-handling.md");
            assertThat(files).containsKey("work/analysis/practices/.gitkeep");
        }

        private static final UUID EARLIER_REVIEW = UUID.fromString("22222222-2222-2222-2222-222222222222");

        /** A practice a completed review already answered on the same code is left out of what the model is asked. */
        @Test
        void shouldStageOnlyThePracticesNoCompletedReviewAnswered() {
            stubDefaults();
            AgentJob job = jobWithMetadata(sampleJobMetadata());
            var answered = new AnsweredPractice("error-handling", 7L, EARLIER_REVIEW);
            when(answeredPractices.answered(eq(job), any(), any(), any())).thenReturn(List.of(answered));

            PreparedJobInputs inputs = handler.prepareInputs(job);
            Map<String, byte[]> files = PreparedJobInputsFixtures.files(inputs);

            assertThat(objectMapper
                            .readTree(files.get(SandboxLayout.PRACTICES_PREFIX + "index.json"))
                            .valueStream()
                            .map(entry -> entry.path("slug").asString())
                            .toList())
                    .containsExactly("pr-description-quality");
            assertThat(files)
                    .containsKey(SandboxLayout.PRACTICES_PREFIX + "pr-description-quality.md")
                    .doesNotContainKey(SandboxLayout.PRACTICES_PREFIX + "error-handling.md");
            assertThat(inputs.answeredPractices()).containsExactly(answered);
            assertThat(Objects.requireNonNull(inputs.automatedReviewReadinessReport())
                            .decisions())
                    .allSatisfy(decision -> assertThat(decision.ready()).isTrue());
        }

        /** Nothing left to ask is not missing evidence: preparation ends with the answers and the captured evidence. */
        @Test
        void shouldEndAsCoalescedWhenACompletedReviewAnsweredEveryReadyPractice() {
            stubDefaults();
            AgentJob job = jobWithMetadata(sampleJobMetadata());
            var answered = List.of(
                    new AnsweredPractice("error-handling", 7L, EARLIER_REVIEW),
                    new AnsweredPractice("pr-description-quality", 8L, EARLIER_REVIEW));
            when(answeredPractices.answered(eq(job), any(), any(), any())).thenReturn(answered);

            assertThatThrownBy(() -> handler.prepareInputs(job))
                    .isInstanceOfSatisfying(ReviewCoalescedException.class, coalesced -> {
                        try (PreparedJobInputs inputs = coalesced.preparedInputs()) {
                            assertThat(inputs.answeredPractices()).containsExactlyElementsOf(answered);
                            assertThat(inputs.automatedReviewReadinessReport()).isNotNull();
                            assertThat(PreparedJobInputsFixtures.files(inputs))
                                    .doesNotContainKey(SandboxLayout.PRACTICES_PREFIX + "index.json");
                        }
                    });
        }

        @Test
        void doesNotWriteLegacyPromptFile() {
            stubDefaults();
            Map<String, byte[]> files =
                    PreparedJobInputsFixtures.files(handler.prepareInputs(jobWithMetadata(sampleJobMetadata())));
            assertThat(files).doesNotContainKey(".prompt");
        }

        @Test
        void rejectsMalformedSlug() {
            when(practiceRepository.findByWorkspaceIdAndArtifactKind(WORKSPACE_ID, ArtifactKinds.PULL_REQUEST))
                    .thenReturn(List.of(createPractice("../etc/passwd", "bad", "c")));

            assertThatThrownBy(() -> handler.prepareInputs(jobWithMetadata(sampleJobMetadata())))
                    .isInstanceOf(JobPreparationException.class)
                    .hasMessageContaining("Practice slug fails ABI pattern");
        }

        @Test
        void throwsWhenNoActivePractices() {
            when(practiceRepository.findByWorkspaceIdAndArtifactKind(WORKSPACE_ID, ArtifactKinds.PULL_REQUEST))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> handler.prepareInputs(jobWithMetadata(sampleJobMetadata())))
                    .isInstanceOf(JobPreparationException.class)
                    .hasMessageContaining("No active scm.pull_request practices");
            verifyNoInteractions(workspaceContextBuilder);
        }

        @Test
        void throwsWhenMetadataMissing() {
            var job = new AgentJob();
            ReflectionTestUtils.setField(job, "metadata", null);
            assertThatThrownBy(() -> handler.prepareInputs(job))
                    .isInstanceOf(JobPreparationException.class)
                    .hasMessageContaining("no metadata");
        }

        @Test
        void preservesProviderOrder() {
            var providerFiles = new LinkedHashMap<String, byte[]>();
            providerFiles.put("context/metadata.json", "{}".getBytes(StandardCharsets.UTF_8));
            providerFiles.put("context/change.json", "{}".getBytes(StandardCharsets.UTF_8));
            providerFiles.put("context/comments.json", "[]".getBytes(StandardCharsets.UTF_8));
            when(workspaceContextBuilder.prepare(any(), any())).thenReturn(prepared(providerFiles));
            when(workspaceContextBuilder.prepareAutomatedReviewReadiness(any(), any(), any(), any(), any()))
                    .thenAnswer(invocation -> readiness(invocation.getArgument(1)));
            when(practiceRepository.findByWorkspaceIdAndArtifactKind(WORKSPACE_ID, ArtifactKinds.PULL_REQUEST))
                    .thenReturn(samplePractices());

            Map<String, byte[]> files =
                    PreparedJobInputsFixtures.files(handler.prepareInputs(jobWithMetadata(sampleJobMetadata())));
            var keys = files.keySet().iterator();
            assertThat(keys.next()).isEqualTo("context/metadata.json");
            assertThat(keys.next()).isEqualTo("context/change.json");
            assertThat(keys.next()).isEqualTo("context/comments.json");
        }
    }

    @Nested
    class Deliver {

        private PracticeResult persisted(AgentJob job, Practice practice, String summary, Severity severity) {
            var practiceResult = mock(PracticeResult.class);
            lenient().when(practiceResult.getPractice()).thenReturn(practice);
            lenient()
                    .when(practiceResult.getEvidence())
                    .thenReturn(AdmittedResultFixtures.evidence(job.getId(), "scm.pull-request.core"));

            lenient().when(practiceResult.getSummary()).thenReturn(summary);
            lenient().when(practiceResult.getOutcome()).thenReturn(Outcome.NOT_MET);
            lenient().when(practiceResult.getSeverity()).thenReturn(severity);
            lenient()
                    .when(practiceResult.getEvidenceRationale())
                    .thenReturn("Reasoning for " + practice.getSlug() + ".");
            lenient().when(practiceResult.getId()).thenReturn(UUID.randomUUID());
            lenient().when(practiceResult.getOccurrenceKey()).thenReturn("occ-" + practice.getSlug());
            return practiceResult;
        }

        /** The handler checks nothing itself: admission reads the candidates, and records them once fenced. */
        @Test
        void shouldHandTheSubmittedResultsToAdmissionUnchangedWhenARunSubmits() {
            AgentJob job = jobWithMetadata(sampleJobMetadata());
            JsonNode results = objectMapper.readTree("""
                [{"practiceSlug": "avoids-insecure-defaults-and-over-broad-permissions",
                  "candidate": {"outcome": "MET", "summary": "Defaults stay closed",
                    "explanation": "The new endpoint keeps the default deny rule.", "citations": []}}]
                """);
            PreparedResults prepared = mock(PreparedResults.class);
            when(deliveryService.prepare(job, results)).thenReturn(prepared);

            handler.prepareResults(job, results).record(job);

            verify(deliveryService).publish(job, prepared);
            verifyNoInteractions(feedbackService, practiceResultRepository);
        }

        @Test
        void shouldRecordNothingWhenAdmissionRefusesTheSubmission() {
            AgentJob job = jobWithMetadata(sampleJobMetadata());
            JsonNode results = objectMapper.createArrayNode();
            var refusal = new ResultsRefusedException(ReviewOutputService.NO_ADMISSIBLE_RESULTS, "No result");
            when(deliveryService.prepare(job, results)).thenThrow(refusal);

            assertThatThrownBy(() -> handler.prepareResults(job, results)).isSameAs(refusal);

            verify(deliveryService, never()).publish(any(), any());
        }

        @Test
        void shouldHoldAutomaticFeedbackInsideTheReviewPackageWhenAnyPracticeNeedsApproval() {
            String lead = "The retry path is covered now, but the description never says why it changed.";
            ObjectNode metadata = sampleJobMetadata();
            metadata.put(ResultAdmissionService.DIGEST_METADATA_KEY, "digest-1");
            AgentJob job = jobWithMetadata(metadata);
            ObjectNode output = objectMapper.createObjectNode();
            ObjectNode feedback = output.putObject("feedback");
            feedback.put("admissionDigest", "digest-1");
            feedback.put("contractVersion", 2);
            job.setOutput(output);

            Practice approvalGated = createPractice("error-handling", "Error Handling", "criteria");
            approvalGated.setAutonomy(PracticeAutonomy.HUMAN_APPROVAL);
            Practice automatic = createPractice("describe-what-and-why", "Describe What And Why", "criteria");
            when(practiceRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(approvalGated, automatic));
            // Built before the stubbing call: persisted() stubs, and Mockito rejects a stub nested in when().
            var gated = persisted(job, approvalGated, "Unhandled error path", Severity.MAJOR);
            var auto = persisted(job, automatic, "No rationale sentence", Severity.MINOR);
            when(practiceResultRepository.findByAgentJobId(
                            job.getId(), job.getWorkspace().getId()))
                    .thenReturn(List.of(gated, auto));

            composed(job, List.of(gated, auto), summary(lead, gated, auto));
            handler.deliver(job);

            var proposal = ArgumentCaptor.forClass(ReviewResultParser.DeliveryContent.class);
            verify(feedbackService).recordProposal(eq(job), proposal.capture());
            verify(feedbackService, never()).deliverFeedback(any(), any(), any());

            assertThat(proposal.getValue().mrNote()).startsWith(lead);
        }

        @Test
        void shouldSendTheAutomaticProblemAloneAndKeepAnApprovalGatedWithholdBesideIt() {
            ObjectNode metadata = sampleJobMetadata();
            metadata.put(ResultAdmissionService.DIGEST_METADATA_KEY, "digest-1");
            AgentJob job = jobWithMetadata(metadata);
            ObjectNode output = objectMapper.createObjectNode();
            output.putObject("feedback").put("admissionDigest", "digest-1");
            job.setOutput(output);

            Practice approvalGated = createPractice("error-handling", "Error Handling", "criteria");
            approvalGated.setAutonomy(PracticeAutonomy.HUMAN_APPROVAL);
            Practice automatic = createPractice("describe-what-and-why", "Describe What And Why", "criteria");
            when(practiceRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(approvalGated, automatic));
            var gated = persisted(job, approvalGated, "Unhandled error path", Severity.MAJOR);
            var auto = persisted(job, automatic, "No rationale sentence", Severity.MINOR);
            when(practiceResultRepository.findByAgentJobId(
                            job.getId(), job.getWorkspace().getId()))
                    .thenReturn(List.of(gated, auto));
            composed(job, List.of(gated, auto), noteCiting(auto), withholding(gated));

            handler.deliver(job);

            verify(feedbackService, never()).recordProposal(any(), any());
            var content = ArgumentCaptor.forClass(ReviewResultParser.DeliveryContent.class);
            verify(feedbackService).deliverFeedback(eq(job), content.capture(), eq(Set.of("describe-what-and-why")));
            assertThat(content.getValue().mrNote())
                    .contains("No rationale sentence")
                    .doesNotContain("Unhandled error path")
                    .doesNotContain("can wait");
            assertThat(content.getValue().contributors()).containsExactly("occ-describe-what-and-why");
            assertThat(content.getValue().withheld())
                    .extracting(ReviewResultParser.WithheldResult::occurrenceKey)
                    .containsExactly("occ-error-handling");
        }

        @Test
        void shouldProposeANoteWhoseUnitCitesAResultOfAPracticeNeedingApproval() {
            AgentJob job = composedJob();
            Practice gatedPractice = createPractice("error-handling", "Error Handling", "criteria");
            gatedPractice.setAutonomy(PracticeAutonomy.HUMAN_APPROVAL);
            Practice automatic = createPractice("describe-what-and-why", "Describe What And Why", "criteria");
            when(practiceRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(gatedPractice, automatic));
            var auto = persisted(job, automatic, "No rationale sentence", Severity.MINOR);
            var gated = persisted(job, gatedPractice, "Errors reach the caller", Severity.MINOR);
            when(practiceResultRepository.findByAgentJobId(
                            job.getId(), job.getWorkspace().getId()))
                    .thenReturn(List.of(auto, gated));
            composed(job, List.of(auto, gated), noteCiting(auto, gated));

            handler.deliver(job);

            verify(feedbackService, never()).deliverFeedback(any(), any(), any());
            var proposal = ArgumentCaptor.forClass(ReviewResultParser.DeliveryContent.class);
            verify(feedbackService).recordProposal(eq(job), proposal.capture());
            assertThat(proposal.getValue().contributors())
                    .containsExactlyInAnyOrder("occ-describe-what-and-why", "occ-error-handling");
        }

        @Test
        void shouldSendAutomaticallyWhenTheOnlyResultNeedingApprovalIsAnUncitedAbstention() {
            AgentJob job = composedJob();
            Practice gatedPractice = createPractice("error-handling", "Error Handling", "criteria");
            gatedPractice.setAutonomy(PracticeAutonomy.HUMAN_APPROVAL);
            Practice automatic = createPractice("describe-what-and-why", "Describe What And Why", "criteria");
            when(practiceRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(gatedPractice, automatic));
            var auto = persisted(job, automatic, "No rationale sentence", Severity.MINOR);
            var abstention = persisted(job, gatedPractice, "No error path changed", Severity.MINOR);
            lenient().when(abstention.getSeverity()).thenReturn(null);
            lenient().when(abstention.getOutcome()).thenReturn(Outcome.NOT_APPLICABLE);

            when(practiceResultRepository.findByAgentJobId(
                            job.getId(), job.getWorkspace().getId()))
                    .thenReturn(List.of(auto, abstention));
            composed(job, List.of(auto, abstention), noteCiting(auto));

            handler.deliver(job);

            verify(feedbackService, never()).recordProposal(any(), any());
            var content = ArgumentCaptor.forClass(ReviewResultParser.DeliveryContent.class);
            verify(feedbackService).deliverFeedback(eq(job), content.capture(), eq(Set.of("describe-what-and-why")));
            assertThat(content.getValue().mrNote()).contains("Say why the change is needed");
            assertThat(content.getValue().contributors()).containsExactly("occ-describe-what-and-why");
        }

        /** A job past admission whose composition output is filled in by {@link #composed}. */
        private AgentJob composedJob() {
            ObjectNode metadata = sampleJobMetadata();
            metadata.put(ResultAdmissionService.DIGEST_METADATA_KEY, "digest-1");
            AgentJob job = jobWithMetadata(metadata);
            ObjectNode output = objectMapper.createObjectNode();
            output.putObject("feedback").put("admissionDigest", "digest-1");
            job.setOutput(output);
            return job;
        }

        /**
         * Stages the admitted results under their persisted ids, each with its problems, as admission hands them to
         * the runner.
         */
        private void composed(AgentJob job, List<PracticeResult> admitted, String... reviewParts) {
            ObjectNode feedback =
                    (ObjectNode) Objects.requireNonNull(job.getOutput()).get("feedback");
            feedback.put("contractVersion", 2);
            var staged = feedback.putArray("results");
            for (var practiceResult : admitted) {
                ObjectNode entry = staged.addObject()
                        .put("practiceResultId", String.valueOf(practiceResult.getId()))
                        .put("practiceSlug", practiceResult.getPractice().getSlug())
                        .put("outcome", practiceResult.getOutcome().name())
                        .put("anchorable", false);
                var children = entry.putArray("observations");
                for (Observation problem : problems.getOrDefault(practiceResult.getId(), List.of())) {
                    ObjectNode child = children.addObject()
                            .put("observationId", problem.getId().toString());
                    var citations = child.putArray("citations");
                    int index = 0;
                    for (JsonNode citation : problem.getCitations()) {
                        citations
                                .addObject()
                                .put("index", index++)
                                .put("path", citation.path("path").asString())
                                .put("side", citation.path("side").asString())
                                .put("startLine", citation.path("startLine").asInt())
                                .put("endLine", citation.path("endLine").asInt())
                                .put("anchorable", true);
                    }
                    child.put("anchorable", true);
                }
            }
            var review = feedback.putObject("review");
            for (String part : reviewParts) {
                objectMapper.readTree(part).properties().forEach(field -> review.set(field.getKey(), field.getValue()));
            }
        }

        private static String withholding(PracticeResult practiceResult) {
            return """
                    {"withheld":[{"basedOn":[{"practiceResultId":"%s"}],"reason":"ALREADY_SAID"}]}
                    """.formatted(practiceResult.getId());
        }

        private String summary(String body, PracticeResult... cited) {
            var review = objectMapper.createObjectNode();
            var summary = review.putObject("summary").put("body", body);
            var basedOn = summary.putArray("basedOn");
            for (PracticeResult practiceResult : cited)
                basedOn.addObject()
                        .put("practiceResultId", practiceResult.getId().toString());
            return objectMapper.writeValueAsString(review);
        }

        /** A line note anchored on one citation of one problem, resting on that problem of its result. */
        private String lineNote(String body, PracticeResult cited, Observation problem, int citationIndex) {
            var review = objectMapper.createObjectNode();
            var note = review.putArray("inline").addObject().put("body", body);
            note.putArray("basedOn")
                    .addObject()
                    .put("practiceResultId", cited.getId().toString())
                    .put("observationId", problem.getId().toString());
            note.putObject("anchor")
                    .put("observationId", problem.getId().toString())
                    .put("citationIndex", citationIndex);
            return objectMapper.writeValueAsString(review);
        }

        /**
         * A problem of {@code result} whose one citation admission verified at its exact line on the new side of the
         * captured change, the only citation a line note may stand on.
         */
        private Observation problemOf(PracticeResult result, int position, String path, int line) {
            var citations = objectMapper.createArrayNode();
            citations
                    .addObject()
                    .put("sourceKind", "scm.pull-request.diff")
                    .put("artifactPath", "context/change.json")
                    .put("path", path)
                    .put("side", "NEW")
                    .put("startLine", line)
                    .put("endLine", line)
                    .put("quote", "        } catch (TimeoutException ignored) {}")
                    .putObject("verification")
                    .put("status", "VERIFIED")
                    .put("scope", "EXACT_LOCATION");
            Observation problem = Observation.builder()
                    .id(UUID.randomUUID())
                    .workspaceId(WORKSPACE_ID)
                    .practiceResultId(result.getId())
                    .position(position)
                    .severity(Severity.MAJOR)
                    .summary("Timeout during export is caught and ignored")
                    .explanation("The added catch block discards the TimeoutException.")
                    .citations(citations)
                    .build();
            problems.computeIfAbsent(result.getId(), id -> new ArrayList<>()).add(problem);
            return problem;
        }

        private String noteCiting(PracticeResult... cited) {
            return summary(
                    "No rationale sentence explains the change. Say why the change is needed, using one sentence of motivation.",
                    cited);
        }

        /** A job past admission, carrying the coverage ledger its run wrote. */
        private AgentJob jobAwaitingDelivery(int eligible, int evaluated) {
            ObjectNode metadata = sampleJobMetadata();
            metadata.put(ResultAdmissionService.DIGEST_METADATA_KEY, "digest-1");
            AgentJob job = jobWithMetadata(metadata);
            ObjectNode output = objectMapper.createObjectNode();
            output.putObject("feedback").put("admissionDigest", "digest-1");
            ObjectNode coverage = output.putObject("practiceCoverage");
            coverage.put("eligible", eligible);
            coverage.put("evaluated", evaluated);
            job.setOutput(output);
            return job;
        }

        private PracticeResult observed(AgentJob job, Practice practice, Outcome outcome) {
            when(practiceRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(practice));
            var practiceResult = mock(PracticeResult.class);
            lenient().when(practiceResult.getPractice()).thenReturn(practice);
            lenient()
                    .when(practiceResult.getEvidence())
                    .thenReturn(AdmittedResultFixtures.evidence(job.getId(), "scm.pull-request.core"));

            lenient().when(practiceResult.getSummary()).thenReturn("What the review saw");
            lenient().when(practiceResult.getOutcome()).thenReturn(outcome);
            lenient().when(practiceResult.getSeverity()).thenReturn(outcome == Outcome.NOT_MET ? Severity.MAJOR : null);
            lenient().when(practiceResult.getEvidenceRationale()).thenReturn("The evidence warrants it.");
            lenient().when(practiceResult.getId()).thenReturn(UUID.randomUUID());
            lenient().when(practiceResult.getOccurrenceKey()).thenReturn("occ-" + practice.getSlug());
            when(practiceResultRepository.findByAgentJobId(
                            job.getId(), job.getWorkspace().getId()))
                    .thenReturn(List.of(practiceResult));
            return practiceResult;
        }

        @Test
        void shouldKeepAnIntentionalAllMetEmptyReviewQuietWithPartialCoverage() {
            AgentJob job = jobAwaitingDelivery(2, 1);
            var strength = observed(
                    job, createPractice("pr-description-quality", "PR Description Quality", "criteria"), Outcome.MET);
            composed(job, List.of(strength));
            handler.deliver(job);
            verify(feedbackService).deliverFeedback(eq(job), isNull(), eq(Set.of()));
            verify(feedbackService, never()).recordProposal(any(), any());
        }

        @Test
        void shouldStillReportWhatAPartialReviewFound() {
            AgentJob job = jobAwaitingDelivery(2, 1);
            var problem =
                    observed(job, createPractice("error-handling", "Error Handling", "criteria"), Outcome.NOT_MET);
            composed(
                    job,
                    List.of(problem),
                    summary("Return the error to the caller rather than swallowing it.", problem));
            handler.deliver(job);
            verify(feedbackService).deliverFeedback(eq(job), any(), any());
        }

        @Test
        void shouldPlaceALineNoteOnTheVerifiedCitationWhenItAnchorsAProblemOfItsResult() {
            AgentJob job = composedJob();
            Practice practice = createPractice("error-handling", "Error Handling", "criteria");
            when(practiceRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(practice));
            var result = persisted(job, practice, "Two added catch blocks drop export failures", Severity.MAJOR);
            when(practiceResultRepository.findByAgentJobId(job.getId(), WORKSPACE_ID))
                    .thenReturn(List.of(result));
            Observation problem = problemOf(result, 1, "src/InvoiceExporter.java", 88);
            composed(
                    job,
                    List.of(result),
                    lineNote("Log the timeout with the invoice id and rethrow it.", result, problem, 0));

            handler.deliver(job);

            var content = ArgumentCaptor.forClass(ReviewResultParser.DeliveryContent.class);
            verify(feedbackService).deliverFeedback(eq(job), content.capture(), eq(Set.of("error-handling")));
            assertThat(content.getValue().diffNotes()).singleElement().satisfies(note -> {
                assertThat(note.filePath()).isEqualTo("src/InvoiceExporter.java");
                assertThat(note.startLine()).isEqualTo(88);
                assertThat(note.deliveryKey()).isEqualTo("observation:occ-error-handling:1:0");
                assertThat(note.contributors()).containsExactly("occ-error-handling");
                assertThat(note.observations()).containsExactly(problem.getId().toString());
            });
        }

        @Test
        void shouldPostNothingOnTheWorkWhenTheOnlyDecidedResultIsMet() {
            AgentJob job = jobAwaitingDelivery(2, 2);
            var strength = observed(
                    job, createPractice("pr-description-quality", "PR Description Quality", "criteria"), Outcome.MET);
            composed(job, List.of(strength), summary("The description names the reason for the change.", strength));

            handler.deliver(job);

            // A MET result is never acknowledged on the work, so a run of MET results owes no review there.
            var content = ArgumentCaptor.forClass(ReviewResultParser.DeliveryContent.class);
            verify(feedbackService).deliverFeedback(eq(job), content.capture(), any());
            assertThat(content.getValue()).as("nothing goes out on the work").isNull();
        }

        @Test
        void shouldRequestNoPrivatePreparationForOutputComposedFromAnotherAdmission() {
            AgentJob job = jobAwaitingDelivery(1, 1);
            ((ObjectNode) Objects.requireNonNull(job.getOutput()).get("feedback")).put("admissionDigest", "digest-2");

            assertThatThrownBy(() -> handler.deliver(job)).isInstanceOf(RuntimeException.class);

            verifyNoInteractions(feedbackService);
        }
    }
}
