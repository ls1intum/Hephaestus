package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.spi.ExistingDeliveryLookup;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackAnchor;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatch;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchCompletion;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchDestination;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.feedback.ProposedPlacement;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class PracticeFeedbackDispatchServiceTest extends BaseUnitTest {

    @Mock
    private FeedbackDispatchRepository repository;

    @Mock
    private PracticeFeedbackDeliveryPolicy policy;

    @Mock
    private PullRequestCommentPoster poster;

    @Mock
    private TransactionTemplate transactions;

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private DiffNotePoster diffNotePoster;

    @Mock
    private FeedbackLedgerRecorder ledgerRecorder;

    private PracticeFeedbackDispatchService service;
    private AgentJob job;
    private FeedbackDispatch dispatch;

    @BeforeEach
    void setUp() {
        service = new PracticeFeedbackDispatchService(
            repository,
            policy,
            poster,
            transactions,
            new SimpleMeterRegistry(),
            JsonMapper.builder().build(),
            feedbackRepository,
            diffNotePoster,
            ledgerRecorder
        );
        lenient()
            .doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Consumer<TransactionStatus> callback = invocation.getArgument(0);
                callback.accept(mock(TransactionStatus.class));
                return null;
            })
            .when(transactions)
            .executeWithoutResult(any());
        lenient()
            .when(transactions.execute(any(TransactionCallback.class)))
            .thenAnswer(invocation -> {
                TransactionCallback<?> callback = invocation.getArgument(0);
                return callback.doInTransaction(mock(TransactionStatus.class));
            });
        Workspace workspace = new Workspace();
        workspace.setId(7L);
        job = new AgentJob();
        job.setId(UUID.randomUUID());
        job.setWorkspace(workspace);
        dispatch = dispatch(FeedbackDispatchState.PENDING);
        lenient()
            .when(repository.findByDestinationKeyAndWorkspaceId("summary:" + job.getId(), 7L))
            .thenReturn(Optional.of(dispatch));
        lenient().when(repository.claim(any(), any(), anyString(), any(), any(Integer.class))).thenReturn(1);
        lenient().when(repository.beginWrite(any(), any(), anyString())).thenReturn(1);
        lenient().when(repository.finish(any())).thenReturn(1);
        lenient()
            .when(policy.evaluatePullRequest(any(), any(), any(), any()))
            .thenReturn(
                PracticeFeedbackDeliveryPolicy.Decision.allowed(
                    new de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest()
                )
            );
    }

    @Test
    void providerSuccessWithLostLocalAcknowledgementIsReconciledWithoutASecondPost() {
        when(poster.findExistingSummaryComment(job)).thenReturn(ExistingDeliveryLookup.found("provider-42"));

        PracticeFeedbackDispatchService.Result result = service.dispatchAutomaticSummary(
            job,
            "body",
            null,
            Set.of("practice")
        );

        assertThat(result.status()).isEqualTo(PracticeFeedbackDispatchService.Result.Status.SENT);
        assertThat(result.externalRef()).isEqualTo("provider-42");
        verify(poster, never()).postFormattedBody(any(), any());
        verify(repository).finish(
            argThat(
                completion ->
                    completion.state().equals("SENT") &&
                    "provider-42".equals(completion.externalRef()) &&
                    completion.error() == null
            )
        );
    }

    @Test
    void unknownProviderLookupBecomesUncertainAndNeverPosts() {
        when(poster.findExistingSummaryComment(job)).thenReturn(ExistingDeliveryLookup.unknown());

        PracticeFeedbackDispatchService.Result result = service.dispatchAutomaticSummary(
            job,
            "body",
            null,
            Set.of("practice")
        );

        assertThat(result.status()).isEqualTo(PracticeFeedbackDispatchService.Result.Status.UNCERTAIN);
        verify(poster, never()).postFormattedBody(any(), any());
        verify(repository).finish(
            argThat(
                completion ->
                    completion.state().equals("UNCERTAIN") &&
                    completion.externalRef() == null &&
                    completion.error() != null
            )
        );
    }

    @Test
    void duplicateWakeupReturnsAlreadySentDispatchWithoutClaiming() {
        dispatch = dispatch(FeedbackDispatchState.SENT);
        when(repository.findByDestinationKeyAndWorkspaceId("summary:" + job.getId(), 7L)).thenReturn(
            Optional.of(dispatch)
        );

        PracticeFeedbackDispatchService.Result result = service.dispatchAutomaticSummary(
            job,
            "body",
            null,
            Set.of("practice")
        );

        assertThat(result.status()).isEqualTo(PracticeFeedbackDispatchService.Result.Status.SENT);
        assertThat(result.externalRef()).isEqualTo("provider-42");
        verify(repository, never()).claim(any(), any(), anyString(), any(), any(Integer.class));
        verify(poster, never()).findExistingSummaryComment(any());
    }

    @Test
    void losingAClaimToAnotherWorkerDefersWithoutProviderIo() {
        when(repository.claim(any(), any(), anyString(), any(), any(Integer.class))).thenReturn(0);

        PracticeFeedbackDispatchService.Result result = service.dispatchAutomaticSummary(
            job,
            "body",
            null,
            Set.of("practice")
        );

        assertThat(result.status()).isEqualTo(PracticeFeedbackDispatchService.Result.Status.IN_PROGRESS);
        verify(poster, never()).findExistingSummaryComment(any());
    }

    @Test
    void claimUsesFullLeaseAndPolicyIsRecheckedImmediatelyBeforePosting() {
        when(poster.findExistingSummaryComment(job)).thenReturn(ExistingDeliveryLookup.absent());
        when(poster.postFormattedBody(job, "body")).thenReturn("provider-42");
        Instant before = Instant.now();

        PracticeFeedbackDispatchService.Result result = service.dispatchAutomaticSummary(
            job,
            "body",
            null,
            Set.of("practice")
        );

        ArgumentCaptor<Instant> leaseUntil = ArgumentCaptor.forClass(Instant.class);
        verify(repository).claim(any(), any(), anyString(), leaseUntil.capture(), any(Integer.class));
        assertThat(leaseUntil.getValue()).isAfterOrEqualTo(before.plus(PracticeFeedbackDispatchService.LEASE));
        verify(policy, times(2)).evaluatePullRequest(any(), any(), any(), any());
        assertThat(result.status()).isEqualTo(PracticeFeedbackDispatchService.Result.Status.SENT);
    }

    @Test
    void recoveredWriteThatIsNotYetVisibleIsNeverPostedAgain() {
        dispatch = dispatch(FeedbackDispatchState.UNCERTAIN, true, PracticeFeedbackDispatchService.MAX_ATTEMPTS);
        when(repository.findByDestinationKeyAndWorkspaceId("summary:" + job.getId(), 7L)).thenReturn(
            Optional.of(dispatch)
        );
        when(poster.findExistingSummaryComment(job)).thenReturn(ExistingDeliveryLookup.absent());

        PracticeFeedbackDispatchService.Result result = service.dispatchAutomaticSummary(
            job,
            "body",
            null,
            Set.of("practice")
        );

        assertThat(result.status()).isEqualTo(PracticeFeedbackDispatchService.Result.Status.UNCERTAIN);
        verify(repository, never()).beginWrite(any(), any(), anyString());
        verify(poster, never()).postFormattedBody(any(), any());
    }

    @Test
    void anAlreadySuppressedDispatchReportsTheReasonItStored() {
        dispatch = dispatch(FeedbackDispatchState.SUPPRESSED, FeedbackSuppressionReason.OUTSIDE_CURRENT_COVERAGE);
        when(repository.findByDestinationKeyAndWorkspaceId("summary:" + job.getId(), 7L)).thenReturn(
            Optional.of(dispatch)
        );

        var result = service.dispatchAutomaticSummary(job, "body", null, Set.of("practice"));

        assertThat(result.status()).isEqualTo(PracticeFeedbackDispatchService.Result.Status.SUPPRESSED);
        assertThat(result.suppressionReason()).isEqualTo(FeedbackSuppressionReason.OUTSIDE_CURRENT_COVERAGE);
        verify(repository, never()).claim(any(), any(), anyString(), any(), any(Integer.class));
    }

    @Test
    void aPauseDropsAutomaticFeedbackTerminally() {
        when(policy.evaluatePullRequest(any(), any(), any(), any())).thenReturn(
            PracticeFeedbackDeliveryPolicy.Decision.suppressed(FeedbackSuppressionReason.WORKSPACE_DELIVERY_PAUSED)
        );

        var result = service.dispatchAutomaticSummary(job, "body", null, Set.of("practice"));

        assertThat(result.status()).isEqualTo(PracticeFeedbackDispatchService.Result.Status.SUPPRESSED);
        assertThat(result.suppressionReason()).isEqualTo(FeedbackSuppressionReason.WORKSPACE_DELIVERY_PAUSED);
        var completion = ArgumentCaptor.forClass(FeedbackDispatchCompletion.class);
        verify(repository).finish(completion.capture());
        assertThat(completion.getValue().state()).isEqualTo(FeedbackDispatchState.SUPPRESSED.name());
        assertThat(completion.getValue().suppressionReason()).isEqualTo("WORKSPACE_DELIVERY_PAUSED");
        assertThat(completion.getValue().error()).isNull();
        verify(poster, never()).postFormattedBody(any(), any());
    }

    @Test
    void aPauseSuppressesAnApprovedProposalTerminally() {
        var feedback = de.tum.cit.aet.hephaestus.practices.feedback.Feedback.builder()
            .id(UUID.randomUUID())
            .body("approved body")
            .build();
        FeedbackDispatch approved = dispatch(
            FeedbackDispatchState.PENDING,
            FeedbackDispatchDestination.APPROVED_REVIEW_PACKAGE,
            feedback.getId()
        );
        when(repository.findByDestinationKeyAndWorkspaceId("approved:" + feedback.getId(), 7L)).thenReturn(
            Optional.of(approved)
        );
        when(policy.evaluatePullRequest(any(), any(), any(), any())).thenReturn(
            PracticeFeedbackDeliveryPolicy.Decision.suppressed(FeedbackSuppressionReason.WORKSPACE_DELIVERY_PAUSED)
        );

        var result = service.dispatchApproved(job, feedback);

        assertThat(result.status()).isEqualTo(PracticeFeedbackDispatchService.Result.Status.SUPPRESSED);
        var completion = ArgumentCaptor.forClass(FeedbackDispatchCompletion.class);
        verify(repository).finish(completion.capture());
        assertThat(completion.getValue().state()).isEqualTo(FeedbackDispatchState.SUPPRESSED.name());
        assertThat(completion.getValue().suppressionReason()).isEqualTo("WORKSPACE_DELIVERY_PAUSED");
        verify(poster, never()).postApprovedProposal(any(), any(), any());
    }

    @Test
    void oneDispatchLeaseOwnsTheExactApprovedPackage() {
        Feedback feedback = Feedback.builder()
            .id(UUID.randomUUID())
            .workspaceId(7L)
            .body("approved body")
            .proposedPlacements(
                new java.util.ArrayList<>(
                    List.of(
                        ProposedPlacement.summary("approved body"),
                        ProposedPlacement.inline("exact inline", "src/Review.java", 12, null, "old-key")
                    )
                )
            )
            .build();
        FeedbackDispatch approved = dispatch(
            FeedbackDispatchState.PENDING,
            FeedbackDispatchDestination.APPROVED_REVIEW_PACKAGE,
            feedback.getId()
        );
        when(repository.findByDestinationKeyAndWorkspaceId("approved:" + feedback.getId(), 7L)).thenReturn(
            Optional.of(approved)
        );
        when(feedbackRepository.findByIdAndWorkspaceId(feedback.getId(), 7L)).thenReturn(Optional.of(feedback));
        when(poster.findApprovedProposal(job, feedback.getId())).thenReturn(ExistingDeliveryLookup.absent());
        when(poster.postApprovedProposal(job, feedback.getId(), "approved body")).thenReturn("summary-ref");
        var signal = new InlineFeedbackChannel.DeliveredSignal(
            "approved:" + feedback.getId() + ":0",
            new FeedbackAnchor.DiffAnchor("src/Review.java", 12, null),
            InlineFeedbackChannel.Disposition.POSTED,
            "inline-ref",
            "thread-ref"
        );
        when(diffNotePoster.reconcileApprovedInlineNotes(any(), any(), any())).thenReturn(
            new DiffNotePoster.DiffNoteResult(1, 0, List.of(signal))
        );

        var result = service.dispatchApproved(job, feedback);

        assertThat(result.status()).isEqualTo(PracticeFeedbackDispatchService.Result.Status.SENT);
        verify(repository).claim(any(), any(), anyString(), any(), any(Integer.class));
        verify(diffNotePoster).reconcileApprovedInlineNotes(
            job,
            feedback.getId(),
            List.of(new PracticeDetectionResultParser.DiffNote("src/Review.java", 12, null, "exact inline", "old-key"))
        );
        verify(ledgerRecorder).recordApprovedPlacements(feedback, "summary-ref", List.of(signal));
    }

    private FeedbackDispatch dispatch(
        FeedbackDispatchState state,
        FeedbackDispatchDestination destination,
        UUID feedbackId
    ) {
        FeedbackDispatch base = dispatch(state, false);
        return new FeedbackDispatch(
            base.getId(),
            "approved:" + feedbackId,
            base.getWorkspaceId(),
            base.getAgentJobId(),
            feedbackId,
            destination,
            state,
            "approved body",
            null,
            base.getPracticeSlugs(),
            false,
            null,
            null,
            null,
            base.getNextAttemptAt(),
            0,
            null,
            null,
            base.getCreatedAt(),
            base.getUpdatedAt()
        );
    }

    private FeedbackDispatch dispatch(FeedbackDispatchState state, FeedbackSuppressionReason reason) {
        FeedbackDispatch base = dispatch(state, false);
        return new FeedbackDispatch(
            base.getId(),
            base.getDestinationKey(),
            base.getWorkspaceId(),
            base.getAgentJobId(),
            base.getFeedbackId(),
            base.getDestination(),
            base.getState(),
            base.getBody(),
            base.getTargetExternalRef(),
            base.getPracticeSlugs(),
            base.getWriteStarted(),
            base.getDeliveredExternalRef(),
            base.getLeaseOwner(),
            base.getLeaseExpiresAt(),
            base.getNextAttemptAt(),
            base.getAttemptCount(),
            reason.name(),
            base.getLastError(),
            base.getCreatedAt(),
            base.getUpdatedAt()
        );
    }

    private FeedbackDispatch dispatch(FeedbackDispatchState state) {
        return dispatch(state, false);
    }

    private FeedbackDispatch dispatch(FeedbackDispatchState state, boolean writeStarted) {
        return dispatch(state, writeStarted, 0);
    }

    private FeedbackDispatch dispatch(FeedbackDispatchState state, boolean writeStarted, int attemptCount) {
        return new FeedbackDispatch(
            UUID.randomUUID(),
            "summary:" + job.getId(),
            7L,
            job.getId(),
            null,
            FeedbackDispatchDestination.ARTIFACT_SUMMARY,
            state,
            "body",
            null,
            JsonMapper.builder().build().valueToTree(List.of("practice")),
            writeStarted,
            state == FeedbackDispatchState.SENT ? "provider-42" : null,
            null,
            null,
            Instant.now(),
            attemptCount,
            null,
            null,
            Instant.now(),
            Instant.now()
        );
    }
}
