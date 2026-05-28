package de.tum.cit.aet.hephaestus.agent.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.integration.core.events.BotCommandReceivedEvent;
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

    @BeforeEach
    void setUp() {
        processor = new BotCommandProcessor(
            agentJobService,
            pullRequestRepository,
            practiceReviewDetectionGate,
            List.of()
        );
    }

    @Nested
    class CommandMatching {

        @Test
        void exactReviewCommand_triggersReview() {
            PullRequest pr = createOpenPr();
            mockPrLookup(pr);
            mockGateDetect(pr);
            when(agentJobService.submit(any(), any(), any())).thenReturn(Optional.of(new AgentJob()));

            processor.onBotCommandReceived(event("/hephaestus review"));

            verify(agentJobService).submit(eq(1L), eq(AgentJobType.PULL_REQUEST_REVIEW), any());
        }

        @Test
        void reviewCommandWithTrailingSpace_triggersReview() {
            PullRequest pr = createOpenPr();
            mockPrLookup(pr);
            mockGateDetect(pr);
            when(agentJobService.submit(any(), any(), any())).thenReturn(Optional.of(new AgentJob()));

            processor.onBotCommandReceived(event("/hephaestus review   "));

            verify(agentJobService).submit(eq(1L), eq(AgentJobType.PULL_REQUEST_REVIEW), any());
        }

        @Test
        void caseInsensitive_triggersReview() {
            PullRequest pr = createOpenPr();
            mockPrLookup(pr);
            mockGateDetect(pr);
            when(agentJobService.submit(any(), any(), any())).thenReturn(Optional.of(new AgentJob()));

            processor.onBotCommandReceived(event("/Hephaestus Review"));

            verify(agentJobService).submit(eq(1L), eq(AgentJobType.PULL_REQUEST_REVIEW), any());
        }

        @Test
        void reviewAllCommand_doesNotTrigger() {
            processor.onBotCommandReceived(event("/hephaestus review-all"));

            verify(pullRequestRepository, never()).findByRepositoryIdAndNumber(anyLong(), anyInt());
        }

        @Test
        void reviewcodeCommand_doesNotTrigger() {
            processor.onBotCommandReceived(event("/hephaestus reviewcode"));

            verify(pullRequestRepository, never()).findByRepositoryIdAndNumber(anyLong(), anyInt());
        }

        @Test
        void unknownCommand_silentlyIgnored() {
            processor.onBotCommandReceived(event("/hephaestus deploy"));

            verify(pullRequestRepository, never()).findByRepositoryIdAndNumber(anyLong(), anyInt());
            verify(agentJobService, never()).submit(any(), any(), any());
        }
    }

    @Nested
    class PrValidation {

        @Test
        void prNotFound_skipsProcessing() {
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_NUMBER)).thenReturn(Optional.empty());

            processor.onBotCommandReceived(event("/hephaestus review"));

            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void closedPr_skipsProcessing() {
            PullRequest pr = createPrWithState(PullRequest.State.CLOSED);
            mockPrLookup(pr);

            processor.onBotCommandReceived(event("/hephaestus review"));

            verify(practiceReviewDetectionGate, never()).evaluate(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void mergedPr_skipsProcessing() {
            PullRequest pr = createPrWithState(PullRequest.State.MERGED);
            mockPrLookup(pr);

            processor.onBotCommandReceived(event("/hephaestus review"));

            verify(practiceReviewDetectionGate, never()).evaluate(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void missingBranchInfo_skipsProcessing() {
            PullRequest pr = createOpenPr();
            pr.setHeadRefOid(null);
            mockPrLookup(pr);

            processor.onBotCommandReceived(event("/hephaestus review"));

            verify(practiceReviewDetectionGate, never()).evaluate(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
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

            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void gateDetect_submitsJob() {
            PullRequest pr = createOpenPr();
            mockPrLookup(pr);
            mockGateDetect(pr);
            when(agentJobService.submit(any(), any(), any())).thenReturn(Optional.of(new AgentJob()));

            processor.onBotCommandReceived(event("/hephaestus review"));

            verify(agentJobService).submit(eq(1L), eq(AgentJobType.PULL_REQUEST_REVIEW), any());
        }

        @Test
        void gateReceivesManualTriggerMode() {
            PullRequest pr = createOpenPr();
            mockPrLookup(pr);
            mockGateDetect(pr);
            when(agentJobService.submit(any(), any(), any())).thenReturn(Optional.of(new AgentJob()));

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

            // Should not throw
            processor.onBotCommandReceived(event("/hephaestus review"));

            verify(agentJobService, never()).submit(any(), any(), any());
        }
    }

    // -- Test helpers --

    private BotCommandReceivedEvent event(String noteBody) {
        return new BotCommandReceivedEvent(REPO_ID, MR_NUMBER, noteBody, AUTHOR, null, null);
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
