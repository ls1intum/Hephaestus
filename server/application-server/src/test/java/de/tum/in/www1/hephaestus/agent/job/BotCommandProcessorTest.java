package de.tum.in.www1.hephaestus.agent.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.gitprovider.common.events.BotCommandReceivedEvent;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.practices.model.Practice;
import de.tum.in.www1.hephaestus.practices.review.GateDecision;
import de.tum.in.www1.hephaestus.practices.review.PracticeReviewDetectionGate;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@Tag("unit")
@DisplayName("BotCommandProcessor")
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
        processor = new BotCommandProcessor(agentJobService, pullRequestRepository, practiceReviewDetectionGate);
    }

    @Nested
    @DisplayName("Command matching")
    class CommandMatching {

        @Test
        @DisplayName("exact '/hephaestus review' triggers review")
        void exactReviewCommand_triggersReview() {
            PullRequest pr = createOpenPr();
            mockPrLookup(pr);
            mockGateDetect(pr);
            when(agentJobService.submit(any(), any(), any())).thenReturn(Optional.of(new AgentJob()));

            processor.onBotCommandReceived(event("/hephaestus review"));

            verify(agentJobService).submit(eq(1L), eq(AgentJobType.PULL_REQUEST_REVIEW), any());
        }

        @Test
        @DisplayName("'/hephaestus review' with trailing whitespace triggers review")
        void reviewCommandWithTrailingSpace_triggersReview() {
            PullRequest pr = createOpenPr();
            mockPrLookup(pr);
            mockGateDetect(pr);
            when(agentJobService.submit(any(), any(), any())).thenReturn(Optional.of(new AgentJob()));

            processor.onBotCommandReceived(event("/hephaestus review   "));

            verify(agentJobService).submit(eq(1L), eq(AgentJobType.PULL_REQUEST_REVIEW), any());
        }

        @Test
        @DisplayName("case-insensitive matching works")
        void caseInsensitive_triggersReview() {
            PullRequest pr = createOpenPr();
            mockPrLookup(pr);
            mockGateDetect(pr);
            when(agentJobService.submit(any(), any(), any())).thenReturn(Optional.of(new AgentJob()));

            processor.onBotCommandReceived(event("/Hephaestus Review"));

            verify(agentJobService).submit(eq(1L), eq(AgentJobType.PULL_REQUEST_REVIEW), any());
        }

        @Test
        @DisplayName("'/hephaestus review-all' does NOT trigger (word boundary)")
        void reviewAllCommand_doesNotTrigger() {
            processor.onBotCommandReceived(event("/hephaestus review-all"));

            verify(pullRequestRepository, never()).findByRepositoryIdAndNumber(anyLong(), anyInt());
        }

        @Test
        @DisplayName("'/hephaestus reviewcode' does NOT trigger (no space boundary)")
        void reviewcodeCommand_doesNotTrigger() {
            processor.onBotCommandReceived(event("/hephaestus reviewcode"));

            verify(pullRequestRepository, never()).findByRepositoryIdAndNumber(anyLong(), anyInt());
        }

        @Test
        @DisplayName("unknown command is silently ignored")
        void unknownCommand_silentlyIgnored() {
            processor.onBotCommandReceived(event("/hephaestus deploy"));

            verify(pullRequestRepository, never()).findByRepositoryIdAndNumber(anyLong(), anyInt());
            verify(agentJobService, never()).submit(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("PR validation")
    class PrValidation {

        @Test
        @DisplayName("PR not found skips processing")
        void prNotFound_skipsProcessing() {
            when(pullRequestRepository.findByRepositoryIdAndNumber(REPO_ID, MR_NUMBER)).thenReturn(Optional.empty());

            processor.onBotCommandReceived(event("/hephaestus review"));

            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("closed PR skips processing")
        void closedPr_skipsProcessing() {
            PullRequest pr = createPrWithState(PullRequest.State.CLOSED);
            mockPrLookup(pr);

            processor.onBotCommandReceived(event("/hephaestus review"));

            verify(practiceReviewDetectionGate, never()).evaluate(any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("merged PR skips processing")
        void mergedPr_skipsProcessing() {
            PullRequest pr = createPrWithState(PullRequest.State.MERGED);
            mockPrLookup(pr);

            processor.onBotCommandReceived(event("/hephaestus review"));

            verify(practiceReviewDetectionGate, never()).evaluate(any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("PR with missing branch info skips processing")
        void missingBranchInfo_skipsProcessing() {
            PullRequest pr = createOpenPr();
            pr.setHeadRefOid(null);
            mockPrLookup(pr);

            processor.onBotCommandReceived(event("/hephaestus review"));

            verify(practiceReviewDetectionGate, never()).evaluate(any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Gate evaluation")
    class GateEvaluation {

        @Test
        @DisplayName("gate Skip decision prevents job submission")
        void gateSkip_noJobSubmitted() {
            PullRequest pr = createOpenPr();
            mockPrLookup(pr);
            when(practiceReviewDetectionGate.evaluate(eq(pr), any())).thenReturn(new GateDecision.Skip("no practices"));

            processor.onBotCommandReceived(event("/hephaestus review"));

            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("gate Detect decision submits job")
        void gateDetect_submitsJob() {
            PullRequest pr = createOpenPr();
            mockPrLookup(pr);
            mockGateDetect(pr);
            when(agentJobService.submit(any(), any(), any())).thenReturn(Optional.of(new AgentJob()));

            processor.onBotCommandReceived(event("/hephaestus review"));

            verify(agentJobService).submit(eq(1L), eq(AgentJobType.PULL_REQUEST_REVIEW), any());
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("exception during processing does not propagate")
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
        return new BotCommandReceivedEvent(REPO_ID, MR_NUMBER, noteBody, AUTHOR);
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
        when(practiceReviewDetectionGate.evaluate(eq(pr), any())).thenReturn(
            new GateDecision.Detect(workspace, List.of(new Practice()))
        );
    }
}
