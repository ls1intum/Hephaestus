package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.handler.PullRequestReviewSubmissionRequest;
import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import de.tum.cit.aet.hephaestus.integration.core.signal.RevisionScheme;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalKey;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalStateReason;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeSignalOptions;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.review.GateDecision;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewDetectionGate;
import de.tum.cit.aet.hephaestus.practices.review.TriggerMode;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The single path every hand-requested review takes. Three of these tests exist because the bot command
 * got each of them wrong in the same direction: it named an occasion that never happened, it filed the
 * measurement into the live population, and it left no ledger row at all.
 */
@Tag("unit")
@DisplayName("A review somebody asked for")
class ManualReviewRequestsTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 1L;
    private static final long PR_ID = 500L;

    @Mock
    private ReviewRequestAuthority authority;

    @Mock
    private PracticeReviewDetectionGate gate;

    @Mock
    private PracticeSignalOptions signalOptions;

    @Mock
    private de.tum.cit.aet.hephaestus.integration.core.signal.SignalRecorder signalRecorder;

    @Mock
    private AgentJobService agentJobService;

    @Mock
    private TransactionTemplate transactionTemplate;

    private ManualReviewRequests requests;
    private Workspace workspace;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        requests = new ManualReviewRequests(
            authority,
            gate,
            signalOptions,
            signalRecorder,
            agentJobService,
            transactionTemplate
        );
        workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        lenient()
            .doAnswer(invocation -> {
                ((Consumer<TransactionStatus>) invocation.getArgument(0)).accept(mock(TransactionStatus.class));
                return null;
            })
            .when(transactionTemplate)
            .executeWithoutResult(any());
        lenient().when(authority.mayRequest(anyLong(), any(), any())).thenReturn(true);
        lenient()
            .when(signalOptions.manualRequestSignalFor(ScmSignals.PULL_REQUEST))
            .thenReturn(Optional.of(ScmSignals.PULL_REQUEST_REVIEW_REQUESTED));
    }

    @Test
    void anAskFromSomebodyWithNoStandingSpendsNothingAndRecordsNothing() {
        when(authority.mayRequest(anyLong(), any(), any())).thenReturn(false);

        ManualReviewOutcome outcome = requests.requestPullRequestReview(workspace, pullRequest(), requester());

        assertThat(outcome.status()).isEqualTo(ManualReviewOutcome.Status.FORBIDDEN);
        verifyNoInteractions(signalRecorder, gate, agentJobService);
    }

    /**
     * The gate is asked about the occasion that actually happened. Naming a lifecycle event instead —
     * the bot command used to name {@code scm.pull_request.opened} — puts an untruth in the artifact
     * trace, which renders the signal as the reason the review ran.
     */
    @Test
    void theGateIsAskedAboutTheRequest_notAboutALifecycleEventThatDidNotHappen() {
        givenGateDetects();
        givenSubmissionSucceeds();

        requests.requestPullRequestReview(workspace, pullRequest(), requester());

        verify(gate).evaluate(any(), eq(ScmSignals.PULL_REQUEST_REVIEW_REQUESTED), eq(TriggerMode.MANUAL));
        verify(gate, never()).evaluate(any(), eq(ScmSignals.PULL_REQUEST_OPENED), any());
    }

    /**
     * Two facts that have to hold together. The metadata signal is null, which is what makes the run
     * load every active practice of the kind rather than the none that bind a request signal; and the
     * origin is stated rather than derived, so that null cannot later be read as LIVE.
     */
    @Test
    void theRunCarriesNoOccasionInItsMetadataAndIsFiledAsASelfSelectedSample() {
        givenGateDetects();
        givenSubmissionSucceeds();

        requests.requestPullRequestReview(workspace, pullRequest(), requester());

        var captor = ArgumentCaptor.forClass(PullRequestReviewSubmissionRequest.class);
        verify(agentJobService).submitWithOutcome(
            eq(WORKSPACE_ID),
            eq(AgentJobType.PULL_REQUEST_REVIEW),
            captor.capture(),
            any()
        );
        assertThat(captor.getValue().triggerSignal()).isNull();
        assertThat(captor.getValue().observationOrigin()).isEqualTo(ObservationOrigin.MANUAL);
    }

    @Test
    void theAskIsRecordedInTheLedgerUnderItsOwnRunId() {
        givenGateDetects();
        givenSubmissionSucceeds();

        requests.requestPullRequestReview(workspace, pullRequest(), requester());

        var captor = ArgumentCaptor.forClass(SignalKey.class);
        verify(signalRecorder).record(captor.capture(), any(), eq(DiscoveredVia.MANUAL));
        SignalKey key = captor.getValue();
        assertThat(key.workspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(key.artifactId()).isEqualTo(PR_ID);
        assertThat(key.signalName()).isEqualTo(ScmSignals.PULL_REQUEST_REVIEW_REQUESTED);
        assertThat(key.revision().scheme()).contains(RevisionScheme.RUN_ID);
    }

    /**
     * Two people asking is two occasions. If the second ask reused the first one's key the ledger's
     * unique constraint would swallow it, and the second person would be told nothing happened.
     */
    @Test
    void twoAsksAboutTheSameUnchangedWorkAreTwoOccasions() {
        givenGateDetects();
        givenSubmissionSucceeds();

        requests.requestPullRequestReview(workspace, pullRequest(), requester());
        requests.requestPullRequestReview(workspace, pullRequest(), requester());

        var captor = ArgumentCaptor.forClass(SignalKey.class);
        verify(signalRecorder, org.mockito.Mockito.times(2)).record(captor.capture(), any(), eq(DiscoveredVia.MANUAL));
        assertThat(captor.getAllValues().get(0)).isNotEqualTo(captor.getAllValues().get(1));
    }

    /** A refusal is settled against the ledger row and handed back as a sentence, not as a failure. */
    @Test
    void aGateRefusalIsRecordedAndExplained() {
        when(gate.evaluate(any(), any(), any())).thenReturn(
            new GateDecision.Skip("every practice bound to this signal is off", SignalStateReason.PRACTICE_TIER_OFF)
        );

        ManualReviewOutcome outcome = requests.requestPullRequestReview(workspace, pullRequest(), requester());

        assertThat(outcome.status()).isEqualTo(ManualReviewOutcome.Status.REFUSED);
        assertThat(outcome.reason()).isEqualTo(SignalStateReason.PRACTICE_TIER_OFF);
        assertThat(outcome.describeReason()).isEqualTo(SignalStateReason.PRACTICE_TIER_OFF.describe());
        verify(signalRecorder).markRefused(any(), eq(SignalStateReason.PRACTICE_TIER_OFF));
        verify(agentJobService, never()).submitWithOutcome(any(), any(), any(), any());
    }

    /** A submission refusal — an exhausted budget, a cooldown — travels out with its own reason too. */
    @Test
    void aSubmissionRefusalKeepsTheReasonTheSubmissionStoppedOn() {
        givenGateDetects();
        when(agentJobService.submitWithOutcome(anyLong(), any(), any(), any())).thenReturn(
            SubmissionOutcome.refused(SignalStateReason.BUDGET_EXHAUSTED)
        );

        ManualReviewOutcome outcome = requests.requestPullRequestReview(workspace, pullRequest(), requester());

        assertThat(outcome.status()).isEqualTo(ManualReviewOutcome.Status.REFUSED);
        assertThat(outcome.reason()).isEqualTo(SignalStateReason.BUDGET_EXHAUSTED);
    }

    /** A kind that never declared a request signal has no occasion to record this under. */
    @Test
    void aKindThatDeclaresNoRequestSignalRefusesRatherThanInventingOne() {
        when(signalOptions.manualRequestSignalFor(ScmSignals.PULL_REQUEST)).thenReturn(Optional.empty());

        ManualReviewOutcome outcome = requests.requestPullRequestReview(workspace, pullRequest(), requester());

        assertThat(outcome.status()).isEqualTo(ManualReviewOutcome.Status.REFUSED);
        verifyNoInteractions(signalRecorder, agentJobService);
    }

    /** A mirror that has not caught up with the branch cannot be cloned or diffed. */
    @Test
    void aPullRequestWithNoHeadCommitIsRefusedBeforeAnythingIsRecorded() {
        PullRequest pr = pullRequest();
        pr.setHeadRefOid(null);

        ManualReviewOutcome outcome = requests.requestPullRequestReview(workspace, pr, requester());

        assertThat(outcome.status()).isEqualTo(ManualReviewOutcome.Status.REFUSED);
        assertThat(outcome.reason()).isEqualTo(SignalStateReason.ARTIFACT_GONE);
        verifyNoInteractions(signalRecorder, agentJobService);
    }

    // Fixtures

    private void givenGateDetects() {
        when(gate.evaluate(any(), any(), any())).thenReturn(
            new GateDecision.Detect(workspace, List.of(new Practice()))
        );
    }

    private void givenSubmissionSucceeds() {
        AgentJob job = new AgentJob();
        job.setId(UUID.randomUUID());
        when(agentJobService.submitWithOutcome(anyLong(), any(), any(), any())).thenReturn(SubmissionOutcome.of(job));
    }

    private PullRequest pullRequest() {
        Repository repo = new Repository();
        repo.setId(100L);
        repo.setNameWithOwner("hephaestustest/demo-repository");

        PullRequest pr = new PullRequest();
        pr.setId(PR_ID);
        pr.setNumber(42);
        pr.setTitle("Test MR");
        pr.setState(PullRequest.State.OPEN);
        pr.setHeadRefOid("abc123");
        pr.setHeadRefName("feature/branch");
        pr.setBaseRefName("main");
        pr.setRepository(repo);
        pr.setAssignees(Set.of());
        return pr;
    }

    private User requester() {
        User user = new User();
        user.setId(4242L);
        user.setLogin("student1");
        return user;
    }
}
