package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.spi.ExistingDeliveryLookup;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatch;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchDestination;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchState;
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
            JsonMapper.builder().build()
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
        when(repository.findByDestinationKeyAndWorkspaceId("summary:" + job.getId(), 7L)).thenReturn(
            Optional.of(dispatch)
        );
        lenient().when(repository.claim(any(), any(), anyString(), any(), any(Integer.class))).thenReturn(1);
        lenient().when(repository.beginWrite(any(), any(), anyString())).thenReturn(1);
        lenient().when(repository.finish(any(), any(), anyString(), anyString(), any(), any(), any())).thenReturn(1);
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
            any(),
            any(),
            anyString(),
            org.mockito.ArgumentMatchers.eq("SENT"),
            org.mockito.ArgumentMatchers.eq("provider-42"),
            org.mockito.ArgumentMatchers.isNull(),
            any()
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
            any(),
            any(),
            anyString(),
            org.mockito.ArgumentMatchers.eq("UNCERTAIN"),
            org.mockito.ArgumentMatchers.isNull(),
            anyString(),
            any()
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
        dispatch = dispatch(FeedbackDispatchState.UNCERTAIN, true);
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

    private FeedbackDispatch dispatch(FeedbackDispatchState state) {
        return dispatch(state, false);
    }

    private FeedbackDispatch dispatch(FeedbackDispatchState state, boolean writeStarted) {
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
            0,
            null,
            Instant.now(),
            Instant.now()
        );
    }
}
