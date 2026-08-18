package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.conversation.ChatSignals;
import de.tum.cit.aet.hephaestus.agent.conversation.ConversationCandidateSource;
import de.tum.cit.aet.hephaestus.agent.conversation.ConversationThreadCandidate;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactSignal;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalKey;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalRecorder;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalState;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalStateReason;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** One settled thread, several recipients, one ledger row. */
@Tag("unit")
@DisplayName("A settled conversation thread's review")
class ConversationReviewSubmitterTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 4L;
    private static final long THREAD_ID = 88L;

    @Mock
    private ConversationCandidateSource candidateSource;

    @Mock
    private AgentJobService agentJobService;

    @Mock
    private SignalRecorder signalRecorder;

    @Mock
    private TransactionTemplate transactionTemplate;

    private ConversationReviewSubmitter submitter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        submitter = new ConversationReviewSubmitter(
            candidateSource,
            agentJobService,
            signalRecorder,
            transactionTemplate
        );
        lenient()
            .doAnswer(invocation -> {
                ((Consumer<TransactionStatus>) invocation.getArgument(0)).accept(mock(TransactionStatus.class));
                return null;
            })
            .when(transactionTemplate)
            .executeWithoutResult(any());
    }

    /**
     * The fan-out is a delivery decision, not three occasions. Settling per participant would leave the
     * row pointing at whichever submission happened to be last, and would put the participant count into
     * every "how many occasions did this instance see" answer.
     */
    @Test
    void threeParticipantsAreThreeJobsAndStillOneSettledOccurrence() {
        givenSubmissionSucceeds();

        long started = submitter.submitAndSettle(candidate(11L, 12L, 13L), key());

        assertThat(started).isEqualTo(3);
        verify(agentJobService, times(3)).submitWithOutcome(
            eq(WORKSPACE_ID),
            eq(AgentJobType.CONVERSATION_REVIEW),
            any(),
            eq(null)
        );
        verify(signalRecorder).markTriggered(eq(key()), any());
        verify(signalRecorder, never()).markRefused(any(), any());
    }

    /** A refused occurrence gets its reason on the row, which is what the reaper later reads. */
    @Test
    void anOccurrenceNothingRanForIsRefusedWithTheReasonThatStoppedIt() {
        when(agentJobService.submitWithOutcome(anyLong(), any(), any(), any())).thenReturn(
            SubmissionOutcome.refused(SignalStateReason.BUDGET_EXHAUSTED)
        );

        long started = submitter.submitAndSettle(candidate(11L), key());

        assertThat(started).isZero();
        verify(signalRecorder).markRefused(key(), SignalStateReason.BUDGET_EXHAUSTED);
    }

    /**
     * A partial fan-out counts as triggered. A review did run on this occurrence, and saying otherwise
     * would hand the reaper a row to re-offer — which would re-review the participants who already got
     * one.
     */
    @Test
    void aPartialFanOutStillCountsAsAnOccurrenceThatWasReviewed() {
        AgentJob job = new AgentJob();
        job.setId(UUID.randomUUID());
        when(agentJobService.submitWithOutcome(anyLong(), any(), any(), any())).thenReturn(
            SubmissionOutcome.refused(SignalStateReason.SUBJECT_UNLINKED),
            SubmissionOutcome.of(job)
        );

        assertThat(submitter.submitAndSettle(candidate(11L, 12L), key())).isEqualTo(1);
        verify(signalRecorder).markTriggered(key(), job.getId());
        verify(signalRecorder, never()).markRefused(any(), any());
    }

    /** Nobody to file findings against is retryable: they can link their account afterwards. */
    @Test
    void aThreadWithNoResolvableParticipantIsHeldOpenRatherThanRetired() {
        assertThat(submitter.submitAndSettle(candidate(), key())).isZero();

        verify(signalRecorder).markRefused(key(), SignalStateReason.SUBJECT_UNLINKED);
    }

    /**
     * The reaper's path re-reads the thread instead of rebuilding it from the ledger row, so consent is
     * re-checked. A signal refused for an exhausted budget can be re-offered days later, and by then the
     * channel may have been withdrawn — reviewing it would read a conversation nobody agreed to.
     */
    @Test
    void aReOfferedSignalWhoseChannelLostConsentIsRetiredRatherThanReviewed() {
        when(candidateSource.candidateById(WORKSPACE_ID, THREAD_ID)).thenReturn(Optional.empty());

        submitter.resubmit(pendingSignal());

        verify(signalRecorder).markRefused(key(), SignalStateReason.ARTIFACT_GONE);
        verify(agentJobService, never()).submitWithOutcome(anyLong(), any(), any(), any());
    }

    @Test
    void aReOfferedSignalOnAStillConsentedThreadIsReviewedAgain() {
        when(candidateSource.candidateById(WORKSPACE_ID, THREAD_ID)).thenReturn(Optional.of(candidate(11L)));
        givenSubmissionSucceeds();

        submitter.resubmit(pendingSignal());

        verify(agentJobService).submitWithOutcome(eq(WORKSPACE_ID), any(), any(), eq(null));
        verify(signalRecorder).markTriggered(eq(key()), any());
    }

    @Test
    void theKindItSpeaksForIsTheChatThread() {
        assertThat(submitter.artifactKind()).isEqualTo(ChatSignals.CONVERSATION_THREAD);
    }

    // Fixtures

    private void givenSubmissionSucceeds() {
        when(agentJobService.submitWithOutcome(anyLong(), any(), any(), any())).thenAnswer(invocation -> {
            AgentJob job = new AgentJob();
            job.setId(UUID.randomUUID());
            return SubmissionOutcome.of(job);
        });
    }

    private static SignalKey key() {
        return ChatSignals.threadSettledKey(WORKSPACE_ID, THREAD_ID, "1700000000.000100", "1700000600.000200", 6);
    }

    private static ConversationThreadCandidate candidate(long... participants) {
        return new ConversationThreadCandidate(
            WORKSPACE_ID,
            THREAD_ID,
            "C123",
            "#design",
            "1700000000.000100",
            "1700000600.000200",
            null,
            participants
        );
    }

    private static ArtifactSignal pendingSignal() {
        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        SignalKey key = key();
        ArtifactSignal signal = new ArtifactSignal();
        signal.setId(UUID.randomUUID());
        signal.setWorkspace(workspace);
        signal.setArtifactKind(ChatSignals.CONVERSATION_THREAD.value());
        signal.setArtifactId(THREAD_ID);
        signal.setSignalName(ChatSignals.CONVERSATION_THREAD_SETTLED.value());
        signal.setRevision(key.revision().value());
        signal.setState(SignalState.PENDING);
        return signal;
    }
}
