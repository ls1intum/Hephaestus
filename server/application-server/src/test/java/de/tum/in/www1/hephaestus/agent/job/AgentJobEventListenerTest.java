package de.tum.in.www1.hephaestus.agent.job;

import static de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent.TriggerEventNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.handler.PullRequestReviewSubmissionRequest;
import de.tum.in.www1.hephaestus.gitprovider.common.DataSource;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.events.RepositoryRef;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.practices.PracticeRepository;
import de.tum.in.www1.hephaestus.practices.model.Practice;
import de.tum.in.www1.hephaestus.practices.review.GateDecision;
import de.tum.in.www1.hephaestus.practices.review.PracticeReviewDetectionGate;
import de.tum.in.www1.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.in.www1.hephaestus.practices.review.TriggerMode;
import de.tum.in.www1.hephaestus.practices.spi.AgentConfigChecker;
import de.tum.in.www1.hephaestus.practices.spi.UserRoleChecker;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceResolver;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

@DisplayName("AgentJobEventListener")
class AgentJobEventListenerTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final RepositoryRef REPO_REF = new RepositoryRef(100L, "owner/repo", "main");
    private static final Long PR_ID = 456L;
    private static final int PR_NUMBER = 42;
    private static final Long WORKSPACE_ID = 1L;

    @Mock
    private AgentJobService agentJobService;

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private PracticeReviewDetectionGate practiceReviewDetectionGate;

    private AgentJobEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AgentJobEventListener(agentJobService, pullRequestRepository, practiceReviewDetectionGate);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private EventPayload.PullRequestData createPrData(Issue.State state, boolean isDraft, boolean isMerged) {
        return new EventPayload.PullRequestData(
            PR_ID,
            PR_NUMBER,
            "Test PR",
            "body",
            state,
            isDraft,
            isMerged,
            0,
            0,
            0,
            "https://github.com/owner/repo/pull/42",
            REPO_REF,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    private EventContext webhookContext(Long scopeId) {
        return new EventContext(
            UUID.randomUUID(),
            Instant.now(),
            scopeId,
            REPO_REF,
            DataSource.WEBHOOK,
            "opened",
            UUID.randomUUID().toString(),
            null
        );
    }

    private EventContext syncContext() {
        return EventContext.forSync(1L, REPO_REF);
    }

    /**
     * Creates a mock PullRequest with lenient stubs — not all fields are accessed
     * depending on the code path (e.g., closed PR skips branch info checks).
     */
    private PullRequest mockPullRequest(String headRefOid, String headRefName, String baseRefName) {
        PullRequest pr = mock(PullRequest.class);
        lenient().when(pr.getId()).thenReturn(PR_ID);
        lenient().when(pr.getHeadRefOid()).thenReturn(headRefOid);
        lenient().when(pr.getHeadRefName()).thenReturn(headRefName);
        lenient().when(pr.getBaseRefName()).thenReturn(baseRefName);
        lenient().when(pr.getState()).thenReturn(Issue.State.OPEN);
        lenient().when(pr.isMerged()).thenReturn(false);
        return pr;
    }

    private EventPayload.ReviewData createReviewData() {
        return new EventPayload.ReviewData(
            100L,
            "LGTM",
            PullRequestReview.State.APPROVED,
            false,
            "https://github.com/owner/repo/pull/42#pullrequestreview-100",
            200L,
            PR_ID,
            Instant.now(),
            100L
        );
    }

    /** Sets up a valid PR mock and gate Detect decision for happy-path tests. */
    private PullRequest setupHappyPath() {
        PullRequest pr = mockPullRequest("abc123", "feature/test", "main");
        when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));

        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        var detect = new GateDecision.Detect(workspace, List.of());
        when(practiceReviewDetectionGate.evaluate(eq(pr), any(), any())).thenReturn(detect);
        when(agentJobService.submit(any(), any(), any())).thenReturn(Optional.empty());

        return pr;
    }

    // ── Test Groups ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Filtering")
    class FilteringTests {

        @Test
        @DisplayName("should skip sync events")
        void shouldSkipSyncEvents() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new DomainEvent.PullRequestCreated(prData, syncContext());

            listener.onPullRequestCreated(event);

            verify(pullRequestRepository, never()).findByIdWithAllForGate(any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("should skip closed PRs")
        void shouldSkipClosedPRs() {
            var prData = createPrData(Issue.State.CLOSED, false, false);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(1L));

            listener.onPullRequestCreated(event);

            verify(pullRequestRepository, never()).findByIdWithAllForGate(any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("should skip merged PRs")
        void shouldSkipMergedPRs() {
            var prData = createPrData(Issue.State.MERGED, false, true);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(1L));

            listener.onPullRequestCreated(event);

            verify(pullRequestRepository, never()).findByIdWithAllForGate(any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("should skip PRs with isMerged=true even when state is OPEN")
        void shouldSkipWhenIsMergedTrueButStateIsOpen() {
            // Race condition: merge flag set before state update in webhook
            var prData = createPrData(Issue.State.OPEN, false, true);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(1L));

            listener.onPullRequestCreated(event);

            verify(pullRequestRepository, never()).findByIdWithAllForGate(any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("should skip when PR entity not found")
        void shouldSkipWhenPRNotFound() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(1L));
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.empty());

            listener.onPullRequestCreated(event);

            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("should skip when headRefOid is null (GitLab)")
        void shouldSkipWhenHeadRefOidIsNull() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(1L));

            PullRequest pr = mockPullRequest(null, "feature/test", "main");
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));

            listener.onPullRequestCreated(event);

            verify(practiceReviewDetectionGate, never()).evaluate(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("should skip when headRefName is null")
        void shouldSkipWhenHeadRefNameIsNull() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(1L));

            PullRequest pr = mockPullRequest("abc123", null, "main");
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));

            listener.onPullRequestCreated(event);

            verify(practiceReviewDetectionGate, never()).evaluate(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("should skip when baseRefName is null")
        void shouldSkipWhenBaseRefNameIsNull() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(1L));

            PullRequest pr = mockPullRequest("abc123", "feature/test", null);
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));

            listener.onPullRequestCreated(event);

            verify(practiceReviewDetectionGate, never()).evaluate(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Gate Integration")
    class GateIntegrationTests {

        @Test
        @DisplayName("should skip when gate returns Skip decision")
        void shouldSkipWhenGateReturnsSkip() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(1L));

            PullRequest pr = mockPullRequest("abc123", "feature/test", "main");
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));
            when(
                practiceReviewDetectionGate.evaluate(pr, TriggerEventNames.PULL_REQUEST_CREATED, TriggerMode.AUTO)
            ).thenReturn(new GateDecision.Skip("no matching practices"));

            listener.onPullRequestCreated(event);

            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("should submit when gate returns Detect decision")
        void shouldSubmitWhenGateReturnsDetect() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(99L));

            PullRequest pr = setupHappyPath();

            listener.onPullRequestCreated(event);

            verify(agentJobService).submit(
                eq(WORKSPACE_ID),
                eq(AgentJobType.PULL_REQUEST_REVIEW),
                any(PullRequestReviewSubmissionRequest.class)
            );
        }

        @Test
        @DisplayName("should use workspace ID from gate decision, not from context scopeId")
        void shouldUseWorkspaceIdFromGateNotContext() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            // Context has scopeId=99, but gate resolves workspace with id=42
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(99L));

            PullRequest pr = mockPullRequest("abc123", "feature/test", "main");
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));

            Workspace workspace = new Workspace();
            workspace.setId(42L);
            var detect = new GateDecision.Detect(workspace, List.of());
            when(
                practiceReviewDetectionGate.evaluate(pr, TriggerEventNames.PULL_REQUEST_CREATED, TriggerMode.AUTO)
            ).thenReturn(detect);
            when(agentJobService.submit(any(), any(), any())).thenReturn(Optional.empty());

            listener.onPullRequestCreated(event);

            verify(agentJobService).submit(eq(42L), eq(AgentJobType.PULL_REQUEST_REVIEW), any());
        }

        @Test
        @DisplayName("should build correct submission request with branch info from entity")
        void shouldBuildCorrectSubmissionRequest() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(1L));

            PullRequest pr = mockPullRequest("sha256abc", "feature/my-branch", "develop");
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));

            Workspace workspace = new Workspace();
            workspace.setId(WORKSPACE_ID);
            var detect = new GateDecision.Detect(workspace, List.of());
            when(practiceReviewDetectionGate.evaluate(eq(pr), any(), any())).thenReturn(detect);
            when(agentJobService.submit(any(), any(), any())).thenReturn(Optional.empty());

            listener.onPullRequestCreated(event);

            var captor = ArgumentCaptor.forClass(PullRequestReviewSubmissionRequest.class);
            verify(agentJobService).submit(eq(WORKSPACE_ID), eq(AgentJobType.PULL_REQUEST_REVIEW), captor.capture());

            PullRequestReviewSubmissionRequest request = captor.getValue();
            assertThat(request.pullRequest()).isSameAs(prData);
            assertThat(request.headRefOid()).isEqualTo("sha256abc");
            assertThat(request.headRefName()).isEqualTo("feature/my-branch");
            assertThat(request.baseRefName()).isEqualTo("develop");
        }

        @Test
        @DisplayName("should delegate draft filtering to gate (not filter in listener)")
        void shouldDelegateDraftFilteringToGate() {
            var prData = createPrData(Issue.State.OPEN, true, false);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(1L));

            PullRequest pr = mockPullRequest("abc123", "feature/test", "main");
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));
            when(
                practiceReviewDetectionGate.evaluate(pr, TriggerEventNames.PULL_REQUEST_CREATED, TriggerMode.AUTO)
            ).thenReturn(new GateDecision.Skip("draft PR"));

            listener.onPullRequestCreated(event);

            // Gate was called (listener did not short-circuit on draft)
            verify(practiceReviewDetectionGate).evaluate(pr, TriggerEventNames.PULL_REQUEST_CREATED, TriggerMode.AUTO);
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("should pass PullRequestCreated trigger event name")
        void shouldPassPullRequestCreatedTriggerEventName() {
            PullRequest pr = setupHappyPath();
            var prData = createPrData(Issue.State.OPEN, false, false);

            listener.onPullRequestCreated(new DomainEvent.PullRequestCreated(prData, webhookContext(1L)));

            verify(practiceReviewDetectionGate).evaluate(pr, TriggerEventNames.PULL_REQUEST_CREATED, TriggerMode.AUTO);
        }

        @Test
        @DisplayName("should pass PullRequestReady trigger event name")
        void shouldPassPullRequestReadyTriggerEventName() {
            PullRequest pr = setupHappyPath();
            var prData = createPrData(Issue.State.OPEN, false, false);

            listener.onPullRequestReady(new DomainEvent.PullRequestReady(prData, webhookContext(1L)));

            verify(practiceReviewDetectionGate).evaluate(pr, TriggerEventNames.PULL_REQUEST_READY, TriggerMode.AUTO);
        }

        @Test
        @DisplayName("should pass PullRequestSynchronized trigger event name")
        void shouldPassPullRequestSynchronizedTriggerEventName() {
            PullRequest pr = setupHappyPath();
            var prData = createPrData(Issue.State.OPEN, false, false);

            listener.onPullRequestSynchronized(new DomainEvent.PullRequestSynchronized(prData, webhookContext(1L)));

            verify(practiceReviewDetectionGate).evaluate(
                pr,
                TriggerEventNames.PULL_REQUEST_SYNCHRONIZED,
                TriggerMode.AUTO
            );
        }

        @Test
        @DisplayName("should not propagate exceptions from submit")
        void shouldNotPropagateExceptionsFromSubmit() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(1L));

            PullRequest pr = mockPullRequest("abc123", "feature/test", "main");
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));

            Workspace workspace = new Workspace();
            workspace.setId(WORKSPACE_ID);
            when(
                practiceReviewDetectionGate.evaluate(pr, TriggerEventNames.PULL_REQUEST_CREATED, TriggerMode.AUTO)
            ).thenReturn(new GateDecision.Detect(workspace, List.of()));
            when(agentJobService.submit(any(), any(), any())).thenThrow(new RuntimeException("DB error"));

            // Should not throw
            listener.onPullRequestCreated(event);
        }

        @Test
        @DisplayName("should not propagate exceptions from gate evaluation")
        void shouldNotPropagateExceptionsFromGate() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(1L));

            PullRequest pr = mockPullRequest("abc123", "feature/test", "main");
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));
            when(
                practiceReviewDetectionGate.evaluate(pr, TriggerEventNames.PULL_REQUEST_CREATED, TriggerMode.AUTO)
            ).thenThrow(new RuntimeException("DB connectivity error"));

            // Should not throw — outer catch handles gate exceptions
            listener.onPullRequestCreated(event);

            verify(agentJobService, never()).submit(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("PullRequestSynchronized")
    class PullRequestSynchronizedTests {

        @Test
        @DisplayName("should submit job when gate passes")
        void shouldSubmitWhenGatePasses() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new DomainEvent.PullRequestSynchronized(prData, webhookContext(1L));

            setupHappyPath();

            listener.onPullRequestSynchronized(event);

            verify(agentJobService).submit(
                eq(WORKSPACE_ID),
                eq(AgentJobType.PULL_REQUEST_REVIEW),
                any(PullRequestReviewSubmissionRequest.class)
            );
        }

        @Test
        @DisplayName("should skip sync events")
        void shouldSkipSyncEvents() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new DomainEvent.PullRequestSynchronized(prData, syncContext());

            listener.onPullRequestSynchronized(event);

            verify(pullRequestRepository, never()).findByIdWithAllForGate(any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("ReviewSubmitted")
    class ReviewSubmittedTests {

        @Test
        @DisplayName("should submit job when gate passes")
        void shouldSubmitWhenGatePasses() {
            var reviewData = createReviewData();
            var event = new DomainEvent.ReviewSubmitted(reviewData, webhookContext(1L));

            PullRequest pr = mockPullRequest("abc123", "feature/test", "main");
            // ReviewSubmitted path calls PullRequestData.from(pr), which needs repository + author
            Repository repo = new Repository();
            repo.setId(100L);
            repo.setNameWithOwner("owner/repo");
            repo.setDefaultBranch("main");
            when(pr.getRepository()).thenReturn(repo);
            when(pr.getNumber()).thenReturn(PR_NUMBER);
            when(pr.getTitle()).thenReturn("Test PR");
            when(pr.getHtmlUrl()).thenReturn("https://github.com/owner/repo/pull/42");
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));

            Workspace workspace = new Workspace();
            workspace.setId(WORKSPACE_ID);
            var detect = new GateDecision.Detect(workspace, List.of());
            when(
                practiceReviewDetectionGate.evaluate(pr, TriggerEventNames.REVIEW_SUBMITTED, TriggerMode.AUTO)
            ).thenReturn(detect);
            when(agentJobService.submit(any(), any(), any())).thenReturn(Optional.empty());

            listener.onReviewSubmitted(event);

            verify(agentJobService).submit(
                eq(WORKSPACE_ID),
                eq(AgentJobType.PULL_REQUEST_REVIEW),
                any(PullRequestReviewSubmissionRequest.class)
            );
        }

        @Test
        @DisplayName("should skip sync events")
        void shouldSkipSyncEvents() {
            var reviewData = createReviewData();
            var event = new DomainEvent.ReviewSubmitted(reviewData, syncContext());

            listener.onReviewSubmitted(event);

            verify(pullRequestRepository, never()).findByIdWithAllForGate(any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("should skip when PR not found")
        void shouldSkipWhenPRNotFound() {
            var reviewData = createReviewData();
            var event = new DomainEvent.ReviewSubmitted(reviewData, webhookContext(1L));
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.empty());

            listener.onReviewSubmitted(event);

            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("should skip when PR is closed")
        void shouldSkipWhenPRIsClosed() {
            var reviewData = createReviewData();
            var event = new DomainEvent.ReviewSubmitted(reviewData, webhookContext(1L));

            PullRequest pr = mock(PullRequest.class);
            when(pr.getState()).thenReturn(Issue.State.CLOSED);
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));

            listener.onReviewSubmitted(event);

            verify(practiceReviewDetectionGate, never()).evaluate(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("should skip when PR is merged")
        void shouldSkipWhenPRIsMerged() {
            var reviewData = createReviewData();
            var event = new DomainEvent.ReviewSubmitted(reviewData, webhookContext(1L));

            PullRequest pr = mock(PullRequest.class);
            when(pr.getState()).thenReturn(Issue.State.MERGED);
            // Lenient: isMerged() may not be reached when getState() == MERGED short-circuits the || chain
            lenient().when(pr.isMerged()).thenReturn(true);
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));

            listener.onReviewSubmitted(event);

            verify(practiceReviewDetectionGate, never()).evaluate(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("should skip when PR has isMerged=true even with OPEN state")
        void shouldSkipWhenIsMergedTrueButStateIsOpen() {
            var reviewData = createReviewData();
            var event = new DomainEvent.ReviewSubmitted(reviewData, webhookContext(1L));

            PullRequest pr = mock(PullRequest.class);
            when(pr.getState()).thenReturn(Issue.State.OPEN);
            when(pr.isMerged()).thenReturn(true);
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));

            listener.onReviewSubmitted(event);

            verify(practiceReviewDetectionGate, never()).evaluate(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("should skip when missing branch info")
        void shouldSkipWhenMissingBranchInfo() {
            var reviewData = createReviewData();
            var event = new DomainEvent.ReviewSubmitted(reviewData, webhookContext(1L));

            PullRequest pr = mockPullRequest(null, "feature/test", "main");
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));

            listener.onReviewSubmitted(event);

            verify(practiceReviewDetectionGate, never()).evaluate(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("should skip when gate returns Skip")
        void shouldSkipWhenGateReturnsSkip() {
            var reviewData = createReviewData();
            var event = new DomainEvent.ReviewSubmitted(reviewData, webhookContext(1L));

            PullRequest pr = mockPullRequest("abc123", "feature/test", "main");
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));
            when(
                practiceReviewDetectionGate.evaluate(pr, TriggerEventNames.REVIEW_SUBMITTED, TriggerMode.AUTO)
            ).thenReturn(new GateDecision.Skip("no matching practices"));

            listener.onReviewSubmitted(event);

            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("should not propagate exceptions from submit")
        void shouldNotPropagateExceptionsFromSubmit() {
            var reviewData = createReviewData();
            var event = new DomainEvent.ReviewSubmitted(reviewData, webhookContext(1L));

            PullRequest pr = mockPullRequest("abc123", "feature/test", "main");
            Repository repo = new Repository();
            repo.setId(100L);
            repo.setNameWithOwner("owner/repo");
            repo.setDefaultBranch("main");
            when(pr.getRepository()).thenReturn(repo);
            when(pr.getNumber()).thenReturn(PR_NUMBER);
            when(pr.getTitle()).thenReturn("Test PR");
            when(pr.getHtmlUrl()).thenReturn("https://github.com/owner/repo/pull/42");
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));

            Workspace workspace = new Workspace();
            workspace.setId(WORKSPACE_ID);
            when(
                practiceReviewDetectionGate.evaluate(pr, TriggerEventNames.REVIEW_SUBMITTED, TriggerMode.AUTO)
            ).thenReturn(new GateDecision.Detect(workspace, List.of()));
            when(agentJobService.submit(any(), any(), any())).thenThrow(new RuntimeException("NATS error"));

            // Should not throw — inner submitJob catch handles it
            listener.onReviewSubmitted(event);
        }

        @Test
        @DisplayName("should not propagate exceptions from gate evaluation")
        void shouldNotPropagateExceptionsFromGate() {
            var reviewData = createReviewData();
            var event = new DomainEvent.ReviewSubmitted(reviewData, webhookContext(1L));

            PullRequest pr = mockPullRequest("abc123", "feature/test", "main");
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));
            when(
                practiceReviewDetectionGate.evaluate(pr, TriggerEventNames.REVIEW_SUBMITTED, TriggerMode.AUTO)
            ).thenThrow(new RuntimeException("unexpected gate error"));

            // Should not throw — outer catch handles gate exceptions
            listener.onReviewSubmitted(event);

            verify(agentJobService, never()).submit(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Collaboration (real gate)")
    class CollaborationTests {

        /**
         * Creates a real gate with mocked leaf dependencies and a listener wired to it.
         * Uses explicit mock creation (not @Mock) to avoid unnecessary stubbing from outer class.
         */
        private record CollaborationFixture(
            AgentJobEventListener listener,
            UserRoleChecker userRoleChecker,
            AgentConfigChecker agentConfigChecker,
            PracticeRepository practiceRepository,
            WorkspaceResolver workspaceResolver
        ) {
            static CollaborationFixture create(
                AgentJobService agentJobService,
                PullRequestRepository pullRequestRepository
            ) {
                var userRoleChecker = mock(UserRoleChecker.class);
                var agentConfigChecker = mock(AgentConfigChecker.class);
                var practiceRepository = mock(PracticeRepository.class);
                var workspaceResolver = mock(WorkspaceResolver.class);
                var properties = new PracticeReviewProperties(true, true, false, "", 15); // runForAllUsers=true
                var realGate = new PracticeReviewDetectionGate(
                    properties,
                    userRoleChecker,
                    agentConfigChecker,
                    practiceRepository,
                    workspaceResolver
                );
                var listener = new AgentJobEventListener(agentJobService, pullRequestRepository, realGate);
                return new CollaborationFixture(
                    listener,
                    userRoleChecker,
                    agentConfigChecker,
                    practiceRepository,
                    workspaceResolver
                );
            }
        }

        /**
         * Creates a mock PR for collaboration tests. Uses lenient stubs because the real gate
         * accesses different fields depending on which gate step short-circuits.
         */
        private PullRequest setupCollaborationPR() {
            PullRequest pr = mock(PullRequest.class);
            lenient().when(pr.getId()).thenReturn(PR_ID);
            lenient().when(pr.getHeadRefOid()).thenReturn("abc123");
            lenient().when(pr.getHeadRefName()).thenReturn("feature/test");
            lenient().when(pr.getBaseRefName()).thenReturn("main");
            lenient().when(pr.getState()).thenReturn(Issue.State.OPEN);
            lenient().when(pr.isDraft()).thenReturn(false);

            Repository repo = new Repository();
            repo.setNameWithOwner("owner/repo");
            lenient().when(pr.getRepository()).thenReturn(repo);

            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));
            return pr;
        }

        @Test
        @DisplayName("should submit job when real gate returns Detect")
        void listenerWithRealGateSubmitsOnDetect() {
            var fixture = CollaborationFixture.create(agentJobService, pullRequestRepository);

            Workspace workspace = new Workspace();
            workspace.setId(WORKSPACE_ID);
            workspace.setWorkspaceSlug("test-workspace");
            workspace.getFeatures().setPracticesEnabled(true);
            when(fixture.workspaceResolver().resolveForRepository("owner/repo")).thenReturn(Optional.of(workspace));
            when(fixture.agentConfigChecker().hasEnabledConfig(WORKSPACE_ID)).thenReturn(true);

            Practice practice = new Practice();
            ArrayNode events = MAPPER.createArrayNode();
            events.add(TriggerEventNames.PULL_REQUEST_CREATED);
            practice.setTriggerEvents(events);
            practice.setActive(true);
            when(fixture.practiceRepository().findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(
                List.of(practice)
            );

            setupCollaborationPR();
            when(agentJobService.submit(any(), any(), any())).thenReturn(Optional.empty());

            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(1L));
            fixture.listener().onPullRequestCreated(event);

            verify(agentJobService).submit(
                eq(WORKSPACE_ID),
                eq(AgentJobType.PULL_REQUEST_REVIEW),
                any(PullRequestReviewSubmissionRequest.class)
            );
        }

        @Test
        @DisplayName("real gate should skip when no matching practices")
        void realGateSkipsWhenNoMatchingPractices() {
            var fixture = CollaborationFixture.create(agentJobService, pullRequestRepository);

            Workspace workspace = new Workspace();
            workspace.setId(WORKSPACE_ID);
            workspace.getFeatures().setPracticesEnabled(true);
            when(fixture.workspaceResolver().resolveForRepository("owner/repo")).thenReturn(Optional.of(workspace));
            when(fixture.agentConfigChecker().hasEnabledConfig(WORKSPACE_ID)).thenReturn(true);

            // Practice only matches ReviewSubmitted, not PullRequestCreated
            Practice practice = new Practice();
            ArrayNode events = MAPPER.createArrayNode();
            events.add(TriggerEventNames.REVIEW_SUBMITTED);
            practice.setTriggerEvents(events);
            practice.setActive(true);
            when(fixture.practiceRepository().findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(
                List.of(practice)
            );

            setupCollaborationPR();

            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(1L));
            fixture.listener().onPullRequestCreated(event);

            verify(agentJobService, never()).submit(any(), any(), any());
        }
    }
}
