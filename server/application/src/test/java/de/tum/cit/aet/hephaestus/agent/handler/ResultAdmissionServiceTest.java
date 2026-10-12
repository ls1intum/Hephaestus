package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.context.CitedSourceAccess;
import de.tum.cit.aet.hephaestus.agent.context.HistoricalGitEvidence;
import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.context.providers.ReviewHistoryContentSource;
import de.tum.cit.aet.hephaestus.agent.conversation.ConversationSourceLiveness;
import de.tum.cit.aet.hephaestus.agent.documentation.DocumentProjection;
import de.tum.cit.aet.hephaestus.agent.handler.conversation.ConversationEligibility;
import de.tum.cit.aet.hephaestus.agent.handler.conversation.ConversationRoutingDecision;
import de.tum.cit.aet.hephaestus.agent.handler.conversation.ConversationalDeliveryListener;
import de.tum.cit.aet.hephaestus.agent.handler.inapp.InAppRoutingDecision;
import de.tum.cit.aet.hephaestus.agent.handler.inapp.InAppSupportContext;
import de.tum.cit.aet.hephaestus.agent.handler.inapp.InAppSupportReader;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.cit.aet.hephaestus.agent.handler.spi.PreparedResults;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ResultsRefusedException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.agent.job.ReviewCompositionBasis;
import de.tum.cit.aet.hephaestus.agent.job.ReviewCompositionBasisRepository;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.integration.scm.ReviewTargetQuery;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeDeliveryBehavior;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeSignalOptions;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackEvidenceRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.PreviousInAppFeedback;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.result.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultVisibilityPolicy;
import de.tum.cit.aet.hephaestus.practices.review.AutomatedReviewFence;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewedWorkChanges;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class ResultAdmissionServiceTest extends BaseUnitTest {

    @Mock
    private AgentJobRepository jobs;

    @Mock
    private PracticeResultRepository practiceResults;

    @Mock
    private ObservationRepository observations;

    @Mock
    private PreparedResults prepared;

    @Mock
    private PlatformTransactionManager transactionManager;

    private final Map<AgentJobType, JobTypeHandler> handlers = new EnumMap<>(AgentJobType.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final JsonNode NO_FAILURES = mapper.createArrayNode();

    @Mock
    private JobEvidenceFiles evidenceFiles;

    @Mock
    private ReviewHistoryContentSource reviewHistory;

    @Mock
    private InAppSupportReader supportReader;

    @Mock
    private ObjectProvider<InAppSupportReader> supportReaders;

    @Mock
    private ConversationalDeliveryListener conversationLane;

    @Mock
    private PullRequestRepository pullRequests;

    @Mock
    private ReviewCompositionBasisRepository basisRepository;

    private ResultAdmissionService service;
    private AgentJob job;
    private ResultAdmissionService.AdmissionIdentity identity;

    @Mock
    private PublicReviewEligibility publicEligibility;

    @BeforeEach
    void setUp() {
        lenient().when(publicEligibility.publicResultIds(any(), any())).thenAnswer(invocation -> {
            Collection<PracticeResult> rows = invocation.getArgument(1);
            return rows.stream().map(PracticeResult::getId).collect(Collectors.toSet());
        });
        lenient()
                .when(transactionManager.getTransaction(any()))
                .thenAnswer(invocation -> new SimpleTransactionStatus());
        // The index of a late read is the catalog's (EvidenceFolderPersonErasureIntegrationTest); here it answers the
        // read.
        lenient()
                .when(evidenceFiles.indexLateRead(any(), any(), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
        for (AgentJobType type : AgentJobType.values()) {
            JobTypeHandler handler = mock(JobTypeHandler.class);
            lenient().when(handler.jobType()).thenReturn(type);
            lenient().when(handler.prepareResults(any(), any())).thenReturn(prepared);
            handlers.put(type, handler);
        }
        service = new ResultAdmissionService(
                jobs,
                practiceResults,
                observations,
                new JobTypeHandlerRegistry(List.copyOf(handlers.values())),
                mapper,
                transactionManager,
                evidenceFiles,
                publicEligibility,
                reviewHistory,
                supportReaders,
                conversationLane,
                pullRequests,
                basisRepository);
        lenient().when(supportReaders.getIfAvailable()).thenReturn(supportReader);
        job = new AgentJob();
        job.setId(UUID.randomUUID());
        job.setStatus(AgentJobStatus.RUNNING);
        job.setWorkerId("worker-1");
        identity = new ResultAdmissionService.AdmissionIdentity(job.getId(), 1L, 0, "worker-1");
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        Workspace workspace = new Workspace();
        workspace.setId(1L);
        job.setWorkspace(workspace);
        job.setMetadata(mapper.createObjectNode());
        when(jobs.findByIdWithWorkspaceForUpdate(job.getId())).thenReturn(Optional.of(job));
        lenient()
                .when(practiceResults.findByAgentJobId(
                        job.getId(), job.getWorkspace().getId()))
                .thenReturn(List.of());
    }

    @Test
    void shouldKeepTheAdmissionAndReportSupportUnavailableWhenTheSupportReadFails() {
        when(supportReader.snapshot(eq(job), any())).thenThrow(new IllegalStateException("read failed"));

        ObjectNode response = service.admit(identity, request());

        assertThat(response.path(ResultAdmissionService.IN_APP_SUPPORT_KEY)
                        .path("state")
                        .asString())
                .isEqualTo("UNAVAILABLE");
        assertThat(Objects.requireNonNull(job.getMetadata())
                        .path(ResultAdmissionService.DIGEST_METADATA_KEY)
                        .asString())
                .isNotBlank()
                .isEqualTo(response.path("admissionDigest").asString());
        verify(prepared, times(1)).record(job);
    }

    @Test
    void shouldReadSupportInAWorkerOnlyContextAndKeepTheAdmissionWhenNoReaderExists() {
        var worker = new ApplicationContextRunner()
                .withPropertyValues("hephaestus.runtime.server.enabled=false", "hephaestus.runtime.worker.enabled=true")
                .withBean(PracticeResultRepository.class, () -> practiceResults)
                .withBean(ResultVisibilityPolicy.class, () -> mock(ResultVisibilityPolicy.class))
                .withBean(PreviousInAppFeedback.class, () -> mock(PreviousInAppFeedback.class))
                .withBean(PracticeFeedbackDeliveryPolicy.class, () -> mock(PracticeFeedbackDeliveryPolicy.class))
                .withBean(FeedbackRepository.class, () -> mock(FeedbackRepository.class))
                .withBean(WorkspaceReviewDefaultsProvider.class, () -> mock(WorkspaceReviewDefaultsProvider.class))
                .withBean(Clock.class, Clock::systemUTC);
        worker.withUserConfiguration(InAppSupportReader.class)
                .run(context -> assertThat(admitWith(context.getBeanProvider(InAppSupportReader.class))
                                .path(ResultAdmissionService.IN_APP_SUPPORT_KEY)
                                .path("state")
                                .asString())
                        .isEqualTo("COMPLETE"));
        worker.run(context -> assertThat(admitWith(context.getBeanProvider(InAppSupportReader.class))
                        .path(ResultAdmissionService.IN_APP_SUPPORT_KEY)
                        .path("state")
                        .asString())
                .isEqualTo("UNAVAILABLE"));
        assertThat(Objects.requireNonNull(job.getMetadata())
                        .path(ResultAdmissionService.DIGEST_METADATA_KEY)
                        .asString())
                .isNotBlank();
    }

    private ObjectNode admitWith(ObjectProvider<InAppSupportReader> readers) {
        return new ResultAdmissionService(
                        jobs,
                        practiceResults,
                        observations,
                        new JobTypeHandlerRegistry(List.copyOf(handlers.values())),
                        mapper,
                        transactionManager,
                        evidenceFiles,
                        publicEligibility,
                        reviewHistory,
                        readers,
                        conversationLane,
                        pullRequests,
                        basisRepository)
                .admit(identity, request());
    }

    @Test
    void shouldCarryTheSupportReadOfTheOwningAttemptForTheAdmittedRows() {
        var support = new InAppSupportContext(
                InAppSupportContext.State.COMPLETE,
                "2026-10-08T10:00:00Z",
                null,
                List.of(new InAppSupportContext.PracticeSupport(
                        "handles-errors", InAppRoutingDecision.UNCORROBORATED, List.of())));
        when(supportReader.snapshot(job, List.of())).thenReturn(support);

        ObjectNode response = service.admit(identity, request());

        assertThat(response.path(ResultAdmissionService.IN_APP_SUPPORT_KEY)).isEqualTo(mapper.valueToTree(support));
        assertThat(response.path(ResultAdmissionService.IN_APP_SUPPORT_KEY)
                        .path("practices")
                        .get(0)
                        .path("decision")
                        .asString())
                .isEqualTo("UNCORROBORATED");
        assertThat(response.path("schemaVersion").asInt()).isEqualTo(ResultAdmissionService.SCHEMA_VERSION);
        assertThat(response.path("results").isArray()).isTrue();
        assertThat(response.path("withheld").isArray()).isTrue();
    }

    /** The conversation lane's routing is read for the owning attempt's admitted rows, by their persisted ids. */
    @Test
    void shouldCarryTheConversationRoutingOfTheAdmittedRowsBesideTheFullAdmission() {
        PracticeResult anchored = negative("inline-practice");
        PracticeResult unanchored = negative("described-practice");
        when(practiceResults.findByAgentJobId(job.getId(), 1L)).thenReturn(List.of(anchored, unanchored));
        var routing = new ConversationEligibility(
                ConversationEligibility.State.COMPLETE,
                "2026-10-08T10:00:00Z",
                List.of(
                        new ConversationEligibility.ResultRoute(
                                anchored.getId(), ConversationRoutingDecision.HAS_INLINE_ANCHOR),
                        new ConversationEligibility.ResultRoute(
                                unanchored.getId(), ConversationRoutingDecision.ADMIT)));
        when(conversationLane.eligibility(job, List.of(anchored, unanchored))).thenReturn(routing);

        ObjectNode response = service.admit(identity, request());

        assertThat(response.path(ResultAdmissionService.IN_CHAT_ELIGIBILITY_KEY))
                .isEqualTo(mapper.valueToTree(routing));
        // A lane decision narrows composition only: every admitted result is still answered.
        assertThat(response.path("results"))
                .extracting(row -> row.path("practiceResultId").asString())
                .containsExactly(anchored.getId().toString(), unanchored.getId().toString());
    }

    @Test
    void shouldKeepTheAdmissionAndReportConversationRoutingUnavailableWhenItsReadFails() {
        when(conversationLane.eligibility(eq(job), any())).thenThrow(new IllegalStateException("read failed"));

        ObjectNode response = service.admit(identity, request());

        assertThat(response.path(ResultAdmissionService.IN_CHAT_ELIGIBILITY_KEY)
                        .path("state")
                        .asString())
                .isEqualTo("UNAVAILABLE");
        assertThat(response.path(ResultAdmissionService.IN_CHAT_ELIGIBILITY_KEY).path("results"))
                .isEmpty();
        assertThat(ResultAdmissionService.isAdmitted(job)).isTrue();
    }

    private PracticeResult negative(String slug) {
        Practice practice = new Practice();
        practice.setSlug(slug);
        return PracticeResult.builder()
                .scopeKey("test-scope")
                .id(UUID.randomUUID())
                .practice(practice)
                .aboutUserId(7L)
                .artifactKind(ArtifactKinds.PULL_REQUEST)
                .artifactId(42L)
                .summary("A problem")
                .outcome(Outcome.NOT_MET)
                .evidence(mapper.createObjectNode())
                .build();
    }

    @Test
    void shouldReportTheLostAttemptInsteadOfSupportWhenOwnershipChangesAfterAdmission() {
        doAnswer(invocation -> {
                    job.setWorkerId("worker-2");
                    return null;
                })
                .when(prepared)
                .record(job);

        assertThatThrownBy(() -> service.admit(identity, request()))
                .isInstanceOf(ResultAdmissionService.StaleAttemptException.class);
        assertThat(Objects.requireNonNull(job.getMetadata())
                        .path(ResultAdmissionService.DIGEST_METADATA_KEY)
                        .asString())
                .isNotBlank();
        verifyNoInteractions(supportReader, conversationLane);
    }

    @Test
    void refreshesCommunicationAfterAdmissionWithoutChangingCanonicalEvidence() {
        service.admit(identity, request());
        JsonNode metadata = Objects.requireNonNull(job.getMetadata()).deepCopy();
        PracticeResult author = PracticeResult.builder()
                .scopeKey("test-scope")
                .id(UUID.randomUUID())
                .aboutUserId(7L)
                .artifactKind(ArtifactKinds.PULL_REQUEST)
                .artifactId(42L)
                .build();
        when(practiceResults.findByAgentJobId(job.getId(), 1L)).thenReturn(List.of(author));
        ObjectNode first = mapper.createObjectNode();
        first.putArray("feedback");
        ObjectNode later = first.deepCopy();
        later.withArray("feedback").addObject().put("body", "Earlier public advice");
        when(reviewHistory.publicSameWorkFeedback(1L, 7L, ArtifactKinds.PULL_REQUEST, 42L))
                .thenReturn(first, later);

        assertThat(service.publicFeedbackHistory(identity).path("history")).isEqualTo(first);
        ObjectNode refreshed = service.publicFeedbackHistory(identity);
        assertThat(refreshed.path("history")).isEqualTo(later);
        assertThat(Instant.parse(refreshed.path("readAt").asString())).isNotNull();
        assertThat(job.getMetadata()).isEqualTo(metadata);
        verify(prepared, times(1)).record(job);
        verify(pullRequests(), times(1)).prepareResults(eq(job), any());
    }

    @Test
    void refusesHistoryBeforeAdmissionAndAfterOwnershipLoss() {
        assertThatThrownBy(() -> service.publicFeedbackHistory(identity))
                .isInstanceOf(ResultAdmissionService.PublicHistoryRefusedException.class);
        verifyNoInteractions(reviewHistory);
        service.admit(identity, request());
        job.setWorkerId("another-worker");
        assertThatThrownBy(() -> service.publicFeedbackHistory(identity))
                .isInstanceOf(ResultAdmissionService.StaleAttemptException.class);
        verifyNoInteractions(reviewHistory);
    }

    @Test
    void refusesIneligiblePublicHistoryAndPreservesAdmissionWhenAHistoryQueryFails() {
        service.admit(identity, request());
        JsonNode metadata = Objects.requireNonNull(job.getMetadata()).deepCopy();
        assertThatThrownBy(() -> service.publicFeedbackHistory(identity))
                .isInstanceOf(ResultAdmissionService.PublicHistoryRefusedException.class);
        PracticeResult author = PracticeResult.builder()
                .scopeKey("test-scope")
                .id(UUID.randomUUID())
                .aboutUserId(7L)
                .artifactKind(ArtifactKinds.PULL_REQUEST)
                .artifactId(42L)
                .build();
        when(practiceResults.findByAgentJobId(job.getId(), 1L)).thenReturn(List.of(author));
        when(reviewHistory.publicSameWorkFeedback(1L, 7L, ArtifactKinds.PULL_REQUEST, 42L))
                .thenThrow(new IllegalStateException("history unavailable"));
        assertThatThrownBy(() -> service.publicFeedbackHistory(identity)).isInstanceOf(IllegalStateException.class);
        assertThat(job.getMetadata()).isEqualTo(metadata);
        verify(prepared, times(1)).record(job);
    }

    /** Submits an admission and returns once its thread is parked on the running one. */
    private static Future<ObjectNode> joiningRetry(ExecutorService pool, Callable<ObjectNode> admit)
            throws InterruptedException {
        var thread = new AtomicReference<Thread>();
        Future<ObjectNode> retry = pool.submit(() -> {
            thread.set(Thread.currentThread());
            return admit.call();
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (thread.get() == null || thread.get().getState() != Thread.State.WAITING) {
            assertThat(System.nanoTime())
                    .as("the retry parks on the running admission")
                    .isLessThan(deadline);
            assertThat(retry.isDone())
                    .as("the retry does not finish on its own")
                    .isFalse();
            Thread.sleep(5);
        }
        return retry;
    }

    private JobTypeHandler pullRequests() {
        return Objects.requireNonNull(handlers.get(AgentJobType.PULL_REQUEST_REVIEW));
    }

    private static String refusalReason(AgentJob job) {
        return Objects.requireNonNull(job.getMetadata())
                .path(ResultAdmissionService.REFUSAL_METADATA_KEY)
                .path("reasonCode")
                .asString();
    }

    @Test
    void shouldRemoveEvidenceOnlyAfterAdmissionCommits() {
        var committed = new AtomicBoolean();
        doAnswer(invocation -> {
                    committed.set(true);
                    return null;
                })
                .when(transactionManager)
                .commit(any());
        doAnswer(invocation -> {
                    assertThat(committed).isTrue();
                    assertThat(ResultAdmissionService.isAdmitted(job)).isTrue();
                    return null;
                })
                .when(evidenceFiles)
                .discardAdmittedAttempt(identity);
        service.admit(identity, request());
        verify(evidenceFiles).discardAdmittedAttempt(identity);
    }

    /**
     * The original public writer inputs are kept with the admission that produced them, in its transaction and
     * before the attempt's folder is retired; a closed public lane or a receipt that cannot vouch keeps nothing.
     */
    @Test
    void shouldKeepTheOriginalPublicInputsWithTheAdmissionOnlyWhenTheyCanServeAPublicWriter() {
        UUID receipt = UUID.randomUUID();
        when(evidenceFiles.completeReceipt(identity)).thenReturn(Optional.of(receipt));
        Map<String, byte[]> files = Map.of(
                SandboxLayout.FEEDBACK_COMPOSITION_PATH,
                "{\"channels\":{\"IN_CONTEXT\":{\"enabled\":true}}}".getBytes(StandardCharsets.UTF_8),
                SandboxLayout.CONTEXT_PREFIX + "description.md",
                "Original description".getBytes(StandardCharsets.UTF_8));
        when(evidenceFiles.originalPublicInputs(identity)).thenReturn(Optional.of(files));
        when(evidenceFiles.holdVouchingReceipt(identity, receipt)).thenReturn(true);

        ObjectNode response = service.admit(identity, request());

        var saved = ArgumentCaptor.forClass(ReviewCompositionBasis.class);
        var order = inOrder(basisRepository, evidenceFiles);
        // The receipt is held and rechecked before the row is written, inside the admitting transaction.
        order.verify(evidenceFiles).holdVouchingReceipt(identity, receipt);
        order.verify(basisRepository).save(saved.capture());
        order.verify(evidenceFiles).discardAdmittedAttempt(identity);
        ReviewCompositionBasis basis = saved.getValue();
        assertThat(basis.getAdmissionAttempt()).isZero();
        assertThat(basis.getAdmissionDigest())
                .isEqualTo(response.path("admissionDigest").asString());
        assertThat(basis.getCopyId()).isEqualTo(receipt);
        assertThat(basis.getBasisDigest()).isEqualTo(ReviewCompositionBasis.digest(files));
        assertThat(basis.getByteSize()).isEqualTo(mapper.writeValueAsBytes(basis.getPayload()).length);
        assertThat(basis.getPayload().path("files").size()).isEqualTo(2);

        // A replayed admission returns the original and keeps the first basis.
        service.admit(identity, request());
        verify(basisRepository, times(1)).save(any());
    }

    @Test
    void shouldKeepNoInputsWhenThePublicLaneWasClosed() {
        when(evidenceFiles.completeReceipt(identity)).thenReturn(Optional.of(UUID.randomUUID()));
        when(evidenceFiles.originalPublicInputs(identity))
                .thenReturn(Optional.of(Map.of(
                        SandboxLayout.FEEDBACK_COMPOSITION_PATH,
                        "{\"channels\":{\"IN_CONTEXT\":{\"enabled\":false}}}".getBytes(StandardCharsets.UTF_8))));

        service.admit(identity, request());

        verify(basisRepository, never()).save(any());
    }

    /** Inputs read before an erasure reached their receipt are not kept; the admission itself still commits. */
    @Test
    void shouldKeepNoInputsWhoseReceiptStoppedVouchingBeforeTheAdmissionCommitted() {
        UUID receipt = UUID.randomUUID();
        when(evidenceFiles.completeReceipt(identity)).thenReturn(Optional.of(receipt));
        when(evidenceFiles.originalPublicInputs(identity))
                .thenReturn(Optional.of(Map.of(
                        SandboxLayout.FEEDBACK_COMPOSITION_PATH,
                        "{\"channels\":{\"IN_CONTEXT\":{\"enabled\":true}}}".getBytes(StandardCharsets.UTF_8))));
        when(evidenceFiles.holdVouchingReceipt(identity, receipt)).thenReturn(false);

        service.admit(identity, request());

        verify(basisRepository, never()).save(any());
        assertThat(ResultAdmissionService.isAdmitted(job)).isTrue();
    }

    /** An attempt that continues an admitted review composes from it: it cannot admit again, not even a replay. */
    @Test
    void shouldRefuseAdmissionFromAnAttemptThatContinuesAnAdmittedReview() {
        job.setMetadata(mapper.createObjectNode()
                .put(ResultAdmissionService.DIGEST_METADATA_KEY, "a".repeat(64))
                .put(ResultAdmissionService.ATTEMPT_METADATA_KEY, 0));
        job.setRetryCount(1);
        var continuing = new ResultAdmissionService.AdmissionIdentity(job.getId(), 1L, 1, "worker-1");

        assertThatThrownBy(() -> service.admit(continuing, request()))
                .isInstanceOf(ResultAdmissionService.StaleAttemptException.class);
        verify(prepared, never()).record(any());
    }

    @Test
    void shouldClearPreviousRefusalWhenResultsAreAdmitted() {
        ObjectNode metadata = mapper.createObjectNode();
        metadata.putObject(ResultAdmissionService.REFUSAL_METADATA_KEY)
                .put("reasonCode", ReviewOutputService.NO_ADMISSIBLE_RESULTS);
        job.setMetadata(metadata);
        assertThat(ResultAdmissionService.practiceResultsWereRefused(job)).isTrue();
        service.admit(identity, request());
        assertThat(ResultAdmissionService.practiceResultsWereRefused(job)).isFalse();
        verify(prepared).record(job);
    }

    @Test
    void samePayloadReplaysWithoutAdmittingTwice() {
        ObjectNode payload = request("one");
        ObjectNode first = service.admit(identity, payload);
        ObjectNode replay = service.admit(identity, payload);

        assertThat(replay.path("admissionDigest").asString())
                .isEqualTo(first.path("admissionDigest").asString());
        verify(pullRequests(), times(1)).prepareResults(job, payload.path("results"));
        verify(prepared, times(1)).record(job);
    }

    @Test
    void changedPayloadConflictsAfterAdmission() {
        service.admit(identity, request("one"));
        assertThatThrownBy(() -> service.admit(identity, request("two")))
                .isInstanceOf(ResultAdmissionService.AdmissionConflictException.class);
        verify(pullRequests(), times(1)).prepareResults(eq(job), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"attempt", "worker", "workspace", "cancelled", "completed", "missing"})
    void shouldRejectAdmissionAndRefusalWhenAuthenticatedOwnershipIsStale(String mismatch) {
        switch (mismatch) {
            case "attempt" -> job.setRetryCount(1);
            case "worker" -> job.setWorkerId("worker-2");
            case "workspace" -> job.getWorkspace().setId(2L);
            case "cancelled" -> job.setStatus(AgentJobStatus.CANCELLED);
            case "completed" -> job.setStatus(AgentJobStatus.COMPLETED);
            case "missing" ->
                when(jobs.findByIdWithWorkspaceForUpdate(job.getId())).thenReturn(Optional.empty());
            default -> throw new AssertionError(mismatch);
        }

        assertThatThrownBy(() -> service.admit(identity, request()))
                .isInstanceOf(ResultAdmissionService.StaleAttemptException.class);
        assertThatThrownBy(() -> service.recordRefusal(
                        identity, ReviewOutputService.NO_ADMISSIBLE_RESULTS, "Refused", NO_FAILURES))
                .isInstanceOf(ResultAdmissionService.StaleAttemptException.class);

        handlers.values().forEach(handler -> verify(handler, never()).prepareResults(any(), any()));
        verify(jobs, never()).save(any());
        assertThat(job.getMetadata()).isEqualTo(mapper.createObjectNode());
    }

    @Test
    void shouldPersistRefusalWhenAttemptStillOwnsRunningJob() {
        service.recordRefusal(identity, ReviewOutputService.NO_ADMISSIBLE_RESULTS, "Refused", NO_FAILURES);
        assertThat(refusalReason(job)).isEqualTo(ReviewOutputService.NO_ADMISSIBLE_RESULTS);
        verify(jobs).save(job);
    }

    @Test
    void shouldRecordTheRefusalOnTheJobWhenTheHandlerRefusesTheSubmission() {
        when(pullRequests().prepareResults(eq(job), any()))
                .thenThrow(new ResultsRefusedException("did_not_read_the_diff", "Refused"));

        assertThatThrownBy(() -> service.admit(identity, request())).isInstanceOf(ResultsRefusedException.class);

        assertThat(refusalReason(job)).isEqualTo("did_not_read_the_diff");
        verify(prepared, never()).record(any());
    }

    @Test
    void shouldRecordAnInadmissibleSubmissionAsARefusalWhenTheHandlerCannotAdmitIt() {
        when(pullRequests().prepareResults(eq(job), any()))
                .thenThrow(new JobDeliveryException("Result cited unavailable evidence"));

        assertThatThrownBy(() -> service.admit(identity, request()))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessage("Result cited unavailable evidence");

        assertThat(refusalReason(job)).isEqualTo(ResultAdmissionService.INADMISSIBLE_REASON_CODE);
        assertThat(Objects.requireNonNull(job.getMetadata())
                        .path(ResultAdmissionService.REFUSAL_METADATA_KEY)
                        .path("reason")
                        .asString())
                .isEqualTo("Result cited unavailable evidence");
    }

    @ParameterizedTest
    @EnumSource(AgentJobType.class)
    void shouldRecordAnInadmissibleSubmissionWhenPublicationRejectsIt(AgentJobType type) {
        job.setJobType(type);
        doThrow(new JobDeliveryException("Reviewed work changed after submission"))
                .when(prepared)
                .record(job);

        assertThatThrownBy(() -> service.admit(identity, request()))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessage("Reviewed work changed after submission");

        assertThat(refusalReason(job)).isEqualTo(ResultAdmissionService.INADMISSIBLE_REASON_CODE);
        assertThat(Objects.requireNonNull(job.getMetadata())
                        .path(ResultAdmissionService.REFUSAL_METADATA_KEY)
                        .path("reason")
                        .asString())
                .isEqualTo("Reviewed work changed after submission");
        assertThat(ResultAdmissionService.isAdmitted(job)).isFalse();
        verify(evidenceFiles, never()).discardAdmittedAttempt(any());
        verify(transactionManager).rollback(any());
    }

    @Test
    void shouldRejectDelayedRefusalWhenAnotherSubmissionWasAdmitted() {
        service.admit(identity, request());
        assertThatThrownBy(() -> service.recordRefusal(
                        identity, ReviewOutputService.NO_ADMISSIBLE_RESULTS, "Refused", NO_FAILURES))
                .isInstanceOf(ResultAdmissionService.AdmissionConflictException.class);
        assertThat(ResultAdmissionService.practiceResultsWereRefused(job)).isFalse();
        verify(jobs, times(1)).save(job);
    }

    @Test
    void shouldRejectPublicationWhenOwnershipChangesDuringVerification() {
        when(pullRequests().prepareResults(eq(job), any())).thenAnswer(invocation -> {
            job.setRetryCount(1);
            return prepared;
        });
        assertThatThrownBy(() -> service.admit(identity, request()))
                .isInstanceOf(ResultAdmissionService.StaleAttemptException.class);
        verifyNoInteractions(prepared);
        verify(jobs, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ISSUE_REVIEW", "CONVERSATION_REVIEW", "DOCUMENT_REVIEW"})
    void shouldUseFencedAdmissionForEveryReviewedWorkKind(String type) {
        job.setJobType(AgentJobType.valueOf(type));
        ObjectNode payload = request("explains-why");
        service.admit(identity, payload);
        verify(handlers.get(job.getJobType())).prepareResults(job, payload.path("results"));
        verify(pullRequests(), never()).prepareResults(any(), any());
        verify(prepared).record(job);
        assertThat(Objects.requireNonNull(job.getMetadata())
                        .path(ResultAdmissionService.DIGEST_METADATA_KEY)
                        .asString())
                .isNotBlank();
    }

    @Test
    void shouldJoinTheRunningAdmissionWhenTheSamePayloadArrivesAgain() throws Exception {
        ObjectNode payload = request("one");
        var verifying = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var preparations = new AtomicInteger();
        when(pullRequests().prepareResults(eq(job), any())).thenAnswer(invocation -> {
            preparations.incrementAndGet();
            verifying.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return prepared;
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ObjectNode> first = pool.submit(() -> service.admit(identity, payload));
            assertThat(verifying.await(10, TimeUnit.SECONDS)).isTrue();
            // The retry has nothing to do but wait: a second preparation would be a second container.
            Future<ObjectNode> retry = joiningRetry(pool, () -> service.admit(identity, payload.deepCopy()));
            release.countDown();

            assertThat(retry.get(10, TimeUnit.SECONDS).path("admissionDigest").asString())
                    .isEqualTo(first.get(10, TimeUnit.SECONDS)
                            .path("admissionDigest")
                            .asString());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
        assertThat(preparations).hasValue(1);
        verify(prepared, times(1)).record(job);
    }

    @Test
    void shouldHandTheRunningAdmissionsRefusalToTheRetryThatJoinedIt() throws Exception {
        ObjectNode payload = request("one");
        var verifying = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(pullRequests().prepareResults(eq(job), any())).thenAnswer(invocation -> {
            verifying.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            throw new ResultsRefusedException(ReviewOutputService.NO_ADMISSIBLE_RESULTS, "Refused");
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ObjectNode> first = pool.submit(() -> service.admit(identity, payload));
            assertThat(verifying.await(10, TimeUnit.SECONDS)).isTrue();
            Future<ObjectNode> retry = joiningRetry(pool, () -> service.admit(identity, payload.deepCopy()));
            release.countDown();

            for (Future<ObjectNode> attempt : List.of(first, retry)) {
                assertThatThrownBy(() -> attempt.get(10, TimeUnit.SECONDS))
                        .cause()
                        .isInstanceOf(ResultsRefusedException.class);
            }
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
        verify(pullRequests(), times(1)).prepareResults(eq(job), any());
        assertThat(refusalReason(job)).isEqualTo(ReviewOutputService.NO_ADMISSIBLE_RESULTS);
    }

    @Test
    void shouldConflictWhenADifferentPayloadArrivesWhileOneIsBeingVerified() throws Exception {
        var verifying = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(pullRequests().prepareResults(eq(job), any())).thenAnswer(invocation -> {
            verifying.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return prepared;
        });
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            Future<ObjectNode> first = pool.submit(() -> service.admit(identity, request("one")));
            assertThat(verifying.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> service.admit(identity, request("two")))
                    .isInstanceOf(ResultAdmissionService.AdmissionConflictException.class);

            release.countDown();
            first.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void shouldAnswerEachResultUnderItsDurableIdWhenItIsAdmitted() {
        job.setJobType(AgentJobType.ISSUE_REVIEW);
        Practice practice = new Practice();
        practice.setSlug("explains-why");
        ObjectNode evidence = mapper.createObjectNode();
        evidence.putArray("citations")
                .addObject()
                .put("sourceKind", "scm.issue.core")
                .put("quote", "why");
        PracticeResult practiceResult = PracticeResult.builder()
                .scopeKey("test-scope")
                .id(UUID.randomUUID())
                .practice(practice)
                .summary("Explains the motivation")
                .outcome(Outcome.MET)
                .evidence(evidence)
                .evidenceRationale("The issue states why.")
                .build();
        when(practiceResults.findByAgentJobId(job.getId(), job.getWorkspace().getId()))
                .thenReturn(List.of(practiceResult));

        when(publicEligibility.publicResultIds(job, List.of(practiceResult))).thenReturn(Set.of());
        ObjectNode response = service.admit(identity, request("explains-why"));

        JsonNode row = response.path("results").get(0);
        assertThat(row.path("practiceResultId").asString())
                .isEqualTo(practiceResult.getId().toString());
        assertThat(row.has("id")).as("ids are always named, never bare").isFalse();
        assertThat(row.path("practiceSlug").asString()).isEqualTo("explains-why");
        assertThat(row.path("outcome").asString()).isEqualTo("MET");
        assertThat(row.path("explanation").asString()).isEqualTo("The issue states why.");
        assertThat(row.path("publicEligible").asBoolean()).isFalse();
        assertThat(row.path("citations")).singleElement().satisfies(citation -> {
            assertThat(citation.path("index").asInt()).isZero();
            assertThat(citation.path("quote").asString()).isEqualTo("why");
            assertThat(citation.path("anchorable").asBoolean()).isFalse();
        });
        assertThat(row.path("evidence").has("citations"))
                .as("the warrants only; the citations are projected beside them")
                .isFalse();
        assertThat(row.path("observations")).isEmpty();
        assertThat(response.path("withheld")).isEmpty();
    }

    /**
     * A negative is answered with its problems: each child under its own id with its verified citations, the result
     * keeping only the citations no problem carries. Only a problem's head-side diff quote can anchor a line note.
     */
    @Test
    void shouldProjectTheProblemsOfANegativeBesideItsOwnCitationsWhenItIsAdmitted() {
        Practice practice = new Practice();
        practice.setSlug("handles-errors");
        ObjectNode own = mapper.createObjectNode()
                .put("sourceKind", "scm.pull-request.core")
                .put("artifactPath", "context/pull_request.json")
                .put("path", "context/pull_request.json")
                .put("startLine", 1)
                .put("endLine", 1)
                .put("quote", "Adds a retry.");
        ObjectNode located = mapper.createObjectNode()
                .put("sourceKind", "scm.pull-request.diff")
                .put("artifactPath", "context/change.json")
                .put("path", "src/Retry.java")
                .put("side", "NEW")
                .put("startLine", 12)
                .put("endLine", 12)
                .put("quote", "catch (Exception ignored) {}");
        located.putObject("verification").put("status", "VERIFIED").put("scope", "EXACT_LOCATION");
        ObjectNode evidence = mapper.createObjectNode();
        evidence.putArray("citations").add(own).add(located);
        evidence.putArray("undecidability")
                .addObject()
                .put("openQuestion", "Is the retry bounded elsewhere?")
                .put("wouldSettleIt", "The caller's configuration.");
        ObjectNode example = mapper.createObjectNode().put("note", "The refresh path logs its failure.");
        example.set("citation", own);
        ObjectNode coverage = mapper.createObjectNode()
                .put("source", "host")
                .put("reads", 2)
                .put("truncatedReads", 0)
                .put("failedReads", 0)
                .put("complete", true);
        PracticeResult negative = PracticeResult.builder()
                .scopeKey("scm.pull_request:42:AUTHOR")
                .id(UUID.randomUUID())
                .practice(practice)
                .summary("Errors are swallowed")
                .outcome(Outcome.NOT_MET)
                .severity(Severity.MAJOR)
                .evidence(evidence)
                .evidenceRationale("One catch block drops the failure.")
                .example(example)
                .coverage(coverage)
                .build();
        ObjectNode basis = mapper.createObjectNode()
                .put("type", "introduced")
                .put("head", 0)
                .put("explanation", "The pull request adds the empty catch block.");
        Observation problem = Observation.builder()
                .id(UUID.randomUUID())
                .workspaceId(1L)
                .practiceResultId(negative.getId())
                .position(0)
                .severity(Severity.MAJOR)
                .summary("Empty catch block")
                .explanation("The retry swallows every exception.")
                .citations(mapper.createArrayNode().add(located))
                .basis(basis)
                .nextStep("Log the failure or rethrow it.")
                .build();
        when(practiceResults.findByAgentJobId(job.getId(), 1L)).thenReturn(List.of(negative));
        when(observations.findByPracticeResultIds(1L, List.of(negative.getId())))
                .thenReturn(List.of(problem));

        ObjectNode response = service.admit(identity, request("handles-errors"));

        JsonNode row = response.path("results").get(0);
        assertThat(row.path("practiceResultId").asString())
                .isEqualTo(negative.getId().toString());
        assertThat(row.path("severity").asString()).isEqualTo("MAJOR");
        assertThat(row.path("citations"))
                .as("the result's own citations: the stored list without its problems'")
                .singleElement()
                .satisfies(citation -> {
                    assertThat(citation.path("quote").asString()).isEqualTo("Adds a retry.");
                    assertThat(citation.path("anchorable").asBoolean()).isFalse();
                });
        assertThat(row.path("evidence").path("undecidability")).hasSize(1);
        assertThat(row.path("example")).isEqualTo(example);
        assertThat(row.path("coverage")).isEqualTo(coverage);
        assertThat(row.path("observations")).singleElement().satisfies(child -> {
            assertThat(child.path("observationId").asString())
                    .isEqualTo(problem.getId().toString());
            assertThat(child.path("position").asInt()).isZero();
            assertThat(child.path("severity").asString()).isEqualTo("MAJOR");
            assertThat(child.path("summary").asString()).isEqualTo("Empty catch block");
            assertThat(child.path("nextStep").asString()).isEqualTo("Log the failure or rethrow it.");
            assertThat(child.path("basis")).isEqualTo(basis);
            assertThat(child.path("citations")).singleElement().satisfies(citation -> {
                assertThat(citation.path("index").asInt()).isZero();
                assertThat(citation.path("anchorable").asBoolean()).isTrue();
            });
            assertThat(child.path("anchorable").asBoolean()).isTrue();
        });
    }

    private ObjectNode reviewedTarget(long pullRequestId) {
        return mapper.createObjectNode()
                .put("pull_request_id", pullRequestId)
                .put("repository_id", 7L)
                .put("repository_full_name", "org/repo")
                .put("pr_number", 12);
    }

    @Test
    void shouldLockThePreparedPullRequestBeforeTheJobAndAdmitAnUnchangedTarget() {
        job.setMetadata(reviewedTarget(42L));

        service.admit(identity, request());

        var order = inOrder(pullRequests, prepared);
        order.verify(pullRequests).lockById(42L);
        order.verify(prepared).record(job);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pull_request_id", "repository_id", "repository_full_name", "pr_number"})
    void shouldRefuseATargetThatChangedWhileThePullRequestLockWasAwaited(String field) {
        job.setMetadata(reviewedTarget(42L));
        when(Objects.requireNonNull(handlers.get(AgentJobType.PULL_REQUEST_REVIEW))
                        .prepareResults(any(), any()))
                .thenAnswer(invocation -> {
                    ObjectNode changed = reviewedTarget(42L);
                    if (field.equals("repository_full_name")) {
                        changed.put(field, "org/other");
                    } else {
                        changed.put(field, 43L);
                    }
                    job.setMetadata(changed);
                    return prepared;
                });

        assertThatThrownBy(() -> service.admit(identity, request()))
                .isInstanceOf(ResultAdmissionService.StaleAttemptException.class);

        verify(pullRequests).lockById(42L);
        verify(prepared, never()).record(any());
    }

    @Test
    void shouldRefuseATargetMovedToAnotherWorkspaceWhileThePullRequestLockWasAwaited() {
        job.setMetadata(reviewedTarget(42L));
        when(Objects.requireNonNull(handlers.get(AgentJobType.PULL_REQUEST_REVIEW))
                        .prepareResults(any(), any()))
                .thenAnswer(invocation -> {
                    Workspace other = new Workspace();
                    other.setId(2L);
                    job.setWorkspace(other);
                    return prepared;
                });

        assertThatThrownBy(() -> service.admit(identity, request()))
                .isInstanceOf(ResultAdmissionService.StaleAttemptException.class);

        verify(prepared, never()).record(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"18446744073709551616", "\"42\"", "0", "-1", "4.2", "null"})
    void shouldLockNothingForAPullRequestIdThatIsNotAPositiveLong(String id) {
        ObjectNode metadata = reviewedTarget(42L);
        metadata.set("pull_request_id", mapper.readTree(id));
        job.setMetadata(metadata);

        service.admit(identity, request());

        verify(pullRequests, never()).lockById(anyLong());
        verify(prepared).record(job);
    }

    @Test
    void shouldLockNothingForAnotherJobType() {
        job.setJobType(AgentJobType.ISSUE_REVIEW);
        job.setMetadata(reviewedTarget(42L));

        service.admit(identity, request());

        verify(pullRequests, never()).lockById(anyLong());
        verify(prepared).record(job);
    }

    /** A version 2 admission body naming these practices, each with a candidate the stubbed handler never reads. */
    private ObjectNode request(String... slugs) {
        List<String> named = slugs.length == 0 ? List.of("explains-why") : List.of(slugs);
        return submission(named.stream()
                .map(slug -> entry(slug, mapper.createObjectNode()))
                .toArray(ObjectNode[]::new));
    }

    /** A version 2 admission body of these entries, in the order given. */
    private ObjectNode submission(ObjectNode... entries) {
        ObjectNode request = mapper.createObjectNode().put("schemaVersion", ResultAdmissionService.SCHEMA_VERSION);
        ArrayNode results = request.putArray("results");
        for (ObjectNode entry : entries) {
            results.add(entry);
        }
        return request;
    }

    private ObjectNode entry(String slug, JsonNode candidate) {
        ObjectNode entry = mapper.createObjectNode().put("practiceSlug", slug);
        entry.set("candidate", candidate);
        return entry;
    }

    // --- Admission through the real checks of the submitted candidates ----------------------------------------------

    private static final String CORE = "scm.pull-request.core";
    private static final String CORE_FILE = "context/pull_request.json";
    private static final String CORE_SHA = "b".repeat(64);

    /** The captured pull request record as the verifier reads it, line by line. */
    private static final List<String> CORE_LINES =
            List.of("Fixes the login timeout.", "Adds a retry to the session refresh.");

    @Mock
    private PracticeRevisionRepository revisions;

    @Mock
    private ReviewTargetQuery reviewTargets;

    @Mock
    private CitedSourceAccess citedSourceAccess;

    @Mock
    private ArtifactSourceCatalogRegistry sourceCatalogs;

    private final Map<Long, Practice> admittedPractices = new HashMap<>();

    /**
     * Routes this job's submissions through the real admission checks of {@link ReviewOutputService}: a pull request
     * whose captured record holds {@link #CORE_LINES}, with {@code explains-why} and {@code small-changes} admitted.
     */
    private void throughTheChecks() {
        lenient().when(citedSourceAccess.permitsReviewResult(job)).thenReturn(true);
        lenient().when(sourceCatalogs.isSourceUsePermitted(any(), any(), any())).thenReturn(true);
        ReviewOutputService output = new ReviewOutputService(
                revisions,
                practiceResults,
                observations,
                new ReviewResultTargetResolver(
                        reviewTargets, mock(ConversationSourceLiveness.class), mock(DocumentProjection.class)),
                mapper,
                new CandidateEvidenceVerifier(
                        mapper, evidenceFiles, mock(HistoricalGitEvidence.class), citedSourceAccess),
                sourceCatalogs,
                new AutomatedReviewFence(Map.of()),
                new LinkedIssueRepairAdmissionService(
                        mock(ReviewedWorkChanges.class),
                        practiceResults,
                        mock(FeedbackEvidenceRepository.class),
                        mock(FeedbackRepository.class)),
                mock(PracticeSignalOptions.class),
                citedSourceAccess);
        job.setMetadata(reviewedTarget(42L));
        lenient()
                .when(reviewTargets.findPullRequest(42L))
                .thenReturn(Optional.of(new ReviewTargetQuery.Target(7L, "org/repo", 12, 789L, false)));
        ObjectNode snapshot = EvidenceSnapshotFixtures.snapshot(mapper);
        EvidenceSnapshotFixtures.artifact(
                snapshot, EvidenceSnapshotFixtures.availableSource(snapshot, CORE, null), CORE_FILE, CORE_SHA);
        admitPractice(snapshot, "explains-why", 10L, 11L);
        admitPractice(snapshot, "small-changes", 20L, 21L);
        job.setEvidenceSnapshot(snapshot);
        lenient()
                .when(evidenceFiles.containsUtf8AtLines(
                        eq(job), eq(CORE_FILE), eq(CORE_SHA), anyString(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    int first = invocation.getArgument(4, Integer.class);
                    int last = invocation.getArgument(5, Integer.class);
                    if (first < 1 || last > CORE_LINES.size() || first > last) return Optional.of(false);
                    return Optional.of(String.join("\n", CORE_LINES.subList(first - 1, last))
                            .equals(invocation.getArgument(3, String.class)));
                });
        lenient()
                .when(practiceResults.insertIfAbsent(
                        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                        any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        lenient().when(practiceResults.findByAgentJobId(job.getId(), 1L)).thenAnswer(invocation -> insertedRows());
        lenient().when(pullRequests().prepareResults(eq(job), any())).thenAnswer(invocation -> {
            ReviewOutputService.PreparedResults checked = output.prepare(job, invocation.getArgument(1));
            return (PreparedResults) admitted -> output.publish(admitted, checked);
        });
    }

    private void admitPractice(ObjectNode snapshot, String slug, long practiceId, long revisionId) {
        Practice practice = new Practice();
        ReflectionTestUtils.setField(practice, "id", practiceId);
        practice.setSlug(slug);
        practice.setWorkspace(job.getWorkspace());
        PracticeTestEvidence.configure(practice, ArtifactKinds.PULL_REQUEST);
        practice.setAutomatedReviewPolicy(PracticeTestEvidence.forArtifact(ArtifactKinds.PULL_REQUEST));
        EvidenceSnapshotFixtures.admittedPractice(snapshot, slug, revisionId);
        PracticeRevision revision = mock(PracticeRevision.class);
        lenient().when(revision.getId()).thenReturn(revisionId);
        lenient().when(revision.getReviewRuleFingerprint()).thenReturn("v5:" + "a".repeat(64));
        lenient().when(revision.getSlug()).thenReturn(slug);
        lenient().when(revision.getPractice()).thenReturn(practice);
        lenient().when(revision.getAutomatedReviewPolicy()).thenReturn(practice.getAutomatedReviewPolicy());
        lenient().when(revision.getSignals()).thenReturn(practice.getSignals());
        lenient().when(revision.getEvidenceRequirements()).thenReturn(practice.getEvidenceRequirements());
        lenient().when(revision.getReviewWhen()).thenReturn(practice.getReviewWhen());
        lenient().when(revision.getSubject()).thenReturn(practice.getSubject());
        lenient().when(revision.getPrecondition()).thenReturn(practice.getPrecondition());
        lenient().when(revision.getDeliveryBehavior()).thenReturn(PracticeDeliveryBehavior.DEFAULT);
        lenient().when(revisions.findByIdAndWorkspaceId(revisionId, 1L)).thenReturn(Optional.of(revision));
        admittedPractices.put(practiceId, practice);
    }

    /** The rows admission asked the repository to insert, as the repository reads them back. */
    private List<PracticeResult> insertedRows() {
        return mockingDetails(practiceResults).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("insertIfAbsent"))
                .map(call -> PracticeResult.builder()
                        .id(call.getArgument(0, UUID.class))
                        .occurrenceKey(call.getArgument(1, String.class))
                        .practice(Objects.requireNonNull(admittedPractices.get(call.getArgument(4, Long.class))))
                        .summary(call.getArgument(9, String.class))
                        .outcome(Outcome.valueOf(call.getArgument(10, String.class)))
                        .evidence(mapper.readTree(call.getArgument(12, String.class)))
                        .evidenceRationale(call.getArgument(13, String.class))
                        .scopeKey(call.getArgument(14, String.class))
                        .coverage(mapper.readTree(call.getArgument(16, String.class)))
                        .build())
                .toList();
    }

    /** The number of problems admission asked the repository to insert. */
    private long insertedProblems() {
        return mockingDetails(observations).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("insert"))
                .count();
    }

    /** The captured record's line, quoted exactly where it stands. */
    private ObjectNode quote(int line) {
        return mapper.createObjectNode()
                .put("sourceKind", CORE)
                .put("artifactPath", CORE_FILE)
                .put("path", CORE_FILE)
                .put("startLine", line)
                .put("endLine", line)
                .put("quote", CORE_LINES.get(line - 1));
    }

    /** A sentence the pull request never wrote, placed at {@code line}. */
    private ObjectNode misquote(int line) {
        return quote(line).put("quote", "Removes the retry altogether.");
    }

    /** Session-shaped ({@code host}: the host counted the reads) or code-shaped ({@code claimed}: the program did). */
    private ObjectNode coverage(String source) {
        ObjectNode coverage = mapper.createObjectNode().put("source", source);
        if (source.equals("host")) {
            coverage.put("reads", 1).put("truncatedReads", 0).put("failedReads", 0);
        } else {
            coverage.put("selected", 1).put("checked", 1).put("total", 1);
        }
        return coverage.put("complete", true);
    }

    private ObjectNode met(ObjectNode citation, String coverage) {
        ObjectNode candidate = mapper.createObjectNode()
                .put("outcome", "MET")
                .put("summary", "The description says why")
                .put("explanation", "The pull request states the problem it fixes.");
        candidate.putArray("citations").add(citation);
        candidate.putObject("evidence");
        candidate.set("coverage", coverage(coverage));
        return candidate;
    }

    private ObjectNode notMet(ObjectNode... problems) {
        ObjectNode candidate = mapper.createObjectNode()
                .put("outcome", "NOT_MET")
                .put("summary", "The description hides a behaviour change")
                .put("explanation", "Two statements of the description disagree with the change.");
        candidate.putArray("citations");
        ArrayNode listed = candidate.putArray("problems");
        for (ObjectNode problem : problems) {
            listed.add(problem);
        }
        candidate.putObject("evidence");
        candidate.set("coverage", coverage("host"));
        return candidate;
    }

    private ObjectNode problem(Severity severity, ObjectNode citation) {
        ObjectNode problem = mapper.createObjectNode()
                .put("severity", severity.name())
                .put("summary", "Misleading description")
                .put("explanation", "The description claims a change the pull request does not make.");
        problem.putArray("citations").add(citation);
        problem.putObject("basis")
                .put("type", "introduced")
                .put("head", 0)
                .put("explanation", "The pull request adds this sentence.");
        return problem;
    }

    /**
     * A program's own candidate and a session's are one contract: each practice is admitted or withheld by the same
     * checks, whoever counted its coverage.
     */
    @ParameterizedTest
    @ValueSource(strings = {"host", "claimed"})
    void shouldHoldCodeAndSessionShapedCandidatesToTheSameChecksWhenTheyAreAdmitted(String source) {
        throughTheChecks();

        ObjectNode response = service.admit(
                identity,
                submission(
                        entry("explains-why", met(quote(1), source)),
                        entry("small-changes", met(misquote(2), source))));

        assertThat(response.path("results")).singleElement().satisfies(row -> {
            assertThat(row.path("practiceSlug").asString()).isEqualTo("explains-why");
            assertThat(row.path("coverage").path("source").asString()).isEqualTo(source);
            assertThat(row.path("citations")
                            .get(0)
                            .path("verification")
                            .path("status")
                            .asString())
                    .isEqualTo("VERIFIED");
        });
        assertThat(response.path("withheld")).singleElement().satisfies(withheld -> {
            assertThat(withheld.path("practiceSlug").asString()).isEqualTo("small-changes");
            assertThat(withheld.path("reasonCode").asString()).isEqualTo(ReviewOutputService.QUOTE_UNVERIFIED);
            assertThat(withheld.path("verificationFailures"))
                    .singleElement()
                    .satisfies(failure ->
                            assertThat(failure.path("citation").asString()).isEqualTo("citations[0]"));
        });
        assertThat(insertedRows())
                .singleElement()
                .satisfies(row -> assertThat(row.getCoverage()).isEqualTo(coverage(source)));
    }

    /** One problem whose quote does not verify withholds its whole practice; the run's other practices stand. */
    @Test
    void shouldWithholdOnlyThePracticeOfAnUnverifiedProblemWhenOtherPracticesProceed() {
        throughTheChecks();
        ObjectNode negative = notMet(problem(Severity.MAJOR, quote(1)), problem(Severity.MINOR, misquote(2)));

        ObjectNode response = service.admit(
                identity, submission(entry("explains-why", negative), entry("small-changes", met(quote(2), "host"))));

        assertThat(response.path("results")).singleElement().satisfies(row -> {
            assertThat(row.path("practiceSlug").asString()).isEqualTo("small-changes");
            assertThat(row.path("outcome").asString()).isEqualTo("MET");
        });
        assertThat(response.path("withheld")).singleElement().satisfies(withheld -> {
            assertThat(withheld.path("practiceSlug").asString()).isEqualTo("explains-why");
            assertThat(withheld.path("reasonCode").asString()).isEqualTo(ReviewOutputService.QUOTE_UNVERIFIED);
            assertThat(withheld.path("reason").asString()).isNotBlank();
            assertThat(withheld.path("verificationFailures")).singleElement().satisfies(failure -> {
                assertThat(failure.path("citation").asString()).isEqualTo("problems[1].citations[0]");
                assertThat(failure.path("status").asString()).isEqualTo("REJECTED");
                assertThat(failure.path("reasonCode").asString()).isEqualTo("QUOTE_LOCATION_MISMATCH");
                assertThat(failure.path("candidateQuoteSha256").asString())
                        .isEqualTo(CitationVerification.quoteDigest("Removes the retry altogether."));
            });
        });
        assertThat(insertedProblems())
                .as("a withheld negative records none of its problems, not even the one that verified")
                .isZero();
        assertThat(ResultAdmissionService.isAdmitted(job)).isTrue();
        assertThat(Objects.requireNonNull(job.getMetadata()).path(ReviewOutputService.WITHHELD_METADATA_KEY))
                .isEqualTo(response.path("withheld"));
    }

    /** The token names the attempt; a body that names another job, attempt or worker changes nothing. */
    @Test
    void shouldAdmitForTheAuthenticatedAttemptWhenTheBodyNamesAnother() {
        UUID otherJob = UUID.randomUUID();
        ObjectNode body = request("explains-why");
        body.put("jobId", otherJob.toString()).put("attempt", 3).put("workerId", "worker-9");

        ObjectNode response = service.admit(identity, body);

        assertThat(ResultAdmissionService.isAdmitted(job)).isTrue();
        assertThat(Objects.requireNonNull(job.getMetadata())
                        .path(ResultAdmissionService.ATTEMPT_METADATA_KEY)
                        .asInt())
                .isEqualTo(identity.attempt());
        assertThat(response.path("admissionDigest").asString())
                .isEqualTo(Objects.requireNonNull(job.getMetadata())
                        .path(ResultAdmissionService.DIGEST_METADATA_KEY)
                        .asString());
        verify(jobs, never()).findByIdWithWorkspaceForUpdate(otherJob);
        verify(pullRequests()).prepareResults(job, body.path("results"));
        verify(prepared).record(job);
    }
}
