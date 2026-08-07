package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.handler.PullRequestReviewSubmissionRequest;
import de.tum.cit.aet.hephaestus.integration.core.events.BotCommandReceivedEvent;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ScmCommentReactionSink;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.review.GateDecision;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewDetectionGate;
import de.tum.cit.aet.hephaestus.practices.review.TriggerMode;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

@Tag("unit")
class BotCommandProcessorTest extends BaseUnitTest {

    private static final long REPO_ID = 100L;
    private static final int MR_NUMBER = 42;
    private static final String AUTHOR = "student1";

    @Mock
    private AgentJobService agentJobService;

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private PracticeReviewDetectionGate practiceReviewDetectionGate;

    private BotCommandProcessor processor;

    private boolean silentModeEngaged;

    @BeforeEach
    void setUp() {
        silentModeEngaged = false;
        processor = new BotCommandProcessor(
            agentJobService,
            pullRequestRepository,
            practiceReviewDetectionGate,
            List.of(),
            () -> silentModeEngaged
        );
    }

    @Nested
    class CommandMatching {

        /**
         * A null trigger event is what makes a manually requested review run the full focus-active
         * practice set rather than one trigger's subset.
         */
        @ParameterizedTest(name = "{0} triggers a review")
        @ValueSource(strings = { "/hephaestus review", "/hephaestus review please", "/Hephaestus Review" })
        void anAcceptedReviewCommand_submitsAReviewForThatMergeRequest(String command) {
            PullRequest pr = createOpenPr();
            mockPrLookup(pr);
            mockGateDetect(pr);
            when(agentJobService.submit(any(), any(), any(), any())).thenReturn(Optional.of(new AgentJob()));

            processor.onBotCommandReceived(event(command));

            var captor = ArgumentCaptor.forClass(PullRequestReviewSubmissionRequest.class);
            // A bot command is not (yet) a ledger-keyed signal, so the submission carries no signal key.
            verify(agentJobService).submit(eq(1L), eq(AgentJobType.PULL_REQUEST_REVIEW), captor.capture(), isNull());
            PullRequestReviewSubmissionRequest request = captor.getValue();
            assertThat(request.pullRequest().number()).isEqualTo(MR_NUMBER);
            assertThat(request.headRefOid()).isEqualTo("abc123");
            assertThat(request.triggerSignal()).isNull();
        }

        @ParameterizedTest(name = "{0} is ignored")
        @ValueSource(strings = { "/hephaestus review-all", "/hephaestus reviewcode", "/hephaestus deploy" })
        void aCommandThatIsNotReview_isSilentlyIgnored(String command) {
            processor.onBotCommandReceived(event(command));

            verify(pullRequestRepository, never()).findByRepositoryIdAndNumber(anyLong(), anyInt());
            verify(agentJobService, never()).submit(any(), any(), any(), any());
        }
    }

    @Nested
    class PrValidation {

        @Test
        void prNotFound_skipsProcessing() {
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_NUMBER)).thenReturn(Optional.empty());

            processor.onBotCommandReceived(event("/hephaestus review"));

            verify(agentJobService, never()).submit(any(), any(), any(), any());
        }

        @Test
        void closedPr_skipsProcessing() {
            PullRequest pr = createPrWithState(PullRequest.State.CLOSED);
            mockPrLookup(pr);

            processor.onBotCommandReceived(event("/hephaestus review"));

            verify(practiceReviewDetectionGate, never()).evaluate(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any(), any());
        }

        @Test
        void mergedPr_skipsProcessing() {
            PullRequest pr = createPrWithState(PullRequest.State.MERGED);
            mockPrLookup(pr);

            processor.onBotCommandReceived(event("/hephaestus review"));

            verify(practiceReviewDetectionGate, never()).evaluate(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any(), any());
        }

        @Test
        void missingBranchInfo_skipsProcessing() {
            PullRequest pr = createOpenPr();
            pr.setHeadRefOid(null);
            mockPrLookup(pr);

            processor.onBotCommandReceived(event("/hephaestus review"));

            verify(practiceReviewDetectionGate, never()).evaluate(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any(), any());
        }
    }

    @Nested
    class GateEvaluation {

        @Test
        void gateSkip_noJobSubmitted() {
            PullRequest pr = createOpenPr();
            mockPrLookup(pr);
            when(practiceReviewDetectionGate.evaluate(eq(pr), any(), any())).thenReturn(
                new GateDecision.Skip("no practices")
            );

            processor.onBotCommandReceived(event("/hephaestus review"));

            verify(agentJobService, never()).submit(any(), any(), any(), any());
        }

        @Test
        void gateReceivesManualTriggerMode() {
            PullRequest pr = createOpenPr();
            mockPrLookup(pr);
            mockGateDetect(pr);
            when(agentJobService.submit(any(), any(), any(), any())).thenReturn(Optional.of(new AgentJob()));

            processor.onBotCommandReceived(event("/hephaestus review"));

            verify(practiceReviewDetectionGate).evaluate(eq(pr), any(), eq(TriggerMode.MANUAL));
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void exceptionDuringProcessing_doesNotPropagate() {
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_NUMBER)).thenThrow(
                new RuntimeException("DB connection failed")
            );

            processor.onBotCommandReceived(event("/hephaestus review"));

            verify(agentJobService, never()).submit(any(), any(), any(), any());
        }
    }

    @Nested
    class EyesReaction {

        private final ScmCommentReactionSink sink = mock(ScmCommentReactionSink.class);

        private BotCommandProcessor processorWithSink() {
            when(sink.kind()).thenReturn(IntegrationKind.GITLAB);
            return new BotCommandProcessor(
                agentJobService,
                pullRequestRepository,
                practiceReviewDetectionGate,
                List.of(sink),
                () -> silentModeEngaged
            );
        }

        @Test
        void acknowledgesCommandWhenNotSilenced() {
            processorWithSink().onBotCommandReceived(eventWithReactionTarget("/hephaestus deploy"));
            verify(sink).react(9L, 7L, "eyes");
        }

        @Test
        void silentModeSuppressesTheAcknowledgementReaction() {
            silentModeEngaged = true;
            processorWithSink().onBotCommandReceived(eventWithReactionTarget("/hephaestus deploy"));
            verify(sink, never()).react(anyLong(), anyLong(), any());
        }

        private BotCommandReceivedEvent eventWithReactionTarget(String noteBody) {
            // commentId = 7 (6th arg), scopeId = 9 (7th arg); react() takes (scopeId, commentNativeId, name).
            return new BotCommandReceivedEvent(IntegrationKind.GITLAB, REPO_ID, MR_NUMBER, noteBody, AUTHOR, 7L, 9L);
        }
    }

    // Test helpers

    private BotCommandReceivedEvent event(String noteBody) {
        return new BotCommandReceivedEvent(IntegrationKind.GITLAB, REPO_ID, MR_NUMBER, noteBody, AUTHOR, null, null);
    }

    private PullRequest createOpenPr() {
        return createPrWithState(PullRequest.State.OPEN);
    }

    private PullRequest createPrWithState(PullRequest.State state) {
        Repository repo = new Repository();
        repo.setId(REPO_ID);
        repo.setNameWithOwner("hephaestustest/demo-repository");
        repo.setHtmlUrl("https://gitlab.example.com/hephaestustest/demo-repository");

        PullRequest pr = new PullRequest();
        pr.setId(500L);
        pr.setNumber(MR_NUMBER);
        pr.setState(state);
        pr.setTitle("Test MR");
        pr.setHtmlUrl("https://gitlab.example.com/hephaestustest/demo-repository/-/merge_requests/" + MR_NUMBER);
        pr.setHeadRefOid("abc123");
        pr.setHeadRefName("feature/branch");
        pr.setBaseRefName("main");
        pr.setRepository(repo);
        return pr;
    }

    private void mockPrLookup(PullRequest pr) {
        when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_NUMBER)).thenReturn(Optional.of(pr));
        when(pullRequestRepository.findByIdWithAllForGate(pr.getId())).thenReturn(Optional.of(pr));
    }

    private void mockGateDetect(PullRequest pr) {
        Workspace workspace = new Workspace();
        workspace.setId(1L);
        when(practiceReviewDetectionGate.evaluate(eq(pr), any(), any())).thenReturn(
            new GateDecision.Detect(workspace, List.of(new Practice()))
        );
    }
}
