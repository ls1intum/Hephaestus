package de.tum.cit.aet.hephaestus.agent.job;

import static de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent.TriggerEventNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.handler.PullRequestReviewSubmissionRequest;
import de.tum.cit.aet.hephaestus.integration.core.events.EventContext;
import de.tum.cit.aet.hephaestus.integration.core.events.RepositoryRef;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmEventPayload;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.DataSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReview;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.review.GateDecision;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewDetectionGate;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.practices.review.TriggerMode;
import de.tum.cit.aet.hephaestus.practices.spi.AgentConfigChecker;
import de.tum.cit.aet.hephaestus.practices.spi.UserRoleChecker;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceResolver;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

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

    // Helpers

    private ScmEventPayload.PullRequestData createPrData(Issue.State state, boolean isDraft, boolean isMerged) {
        return new ScmEventPayload.PullRequestData(
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
     * Creates a real PullRequest with the fields the listener reads set — not all fields
     * are accessed depending on the code path (e.g., closed PR skips branch info checks).
     */
    private PullRequest mockPullRequest(String headRefOid, String headRefName, String baseRefName) {
        PullRequest pr = new PullRequest();
        pr.setId(PR_ID);
        pr.setHeadRefOid(headRefOid);
        pr.setHeadRefName(headRefName);
        pr.setBaseRefName(baseRefName);
        pr.setState(Issue.State.OPEN);
        pr.setMerged(false);
        return pr;
    }

    private ScmEventPayload.ReviewData createReviewData() {
        return new ScmEventPayload.ReviewData(
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

    // Test Groups

    @Nested
    class FilteringTests {

        @Test
        void shouldSkipSyncEvents() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new ScmDomainEvent.PullRequestCreated(prData, syncContext());

            listener.onPullRequestCreated(event);

            verify(pullRequestRepository, never()).findByIdWithAllForGate(any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void shouldSkipClosedPRs() {
            var prData = createPrData(Issue.State.CLOSED, false, false);
            var event = new ScmDomainEvent.PullRequestCreated(prData, webhookContext(1L));

            listener.onPullRequestCreated(event);

            verify(pullRequestRepository, never()).findByIdWithAllForGate(any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void shouldSkipMergedPRs() {
            var prData = createPrData(Issue.State.MERGED, false, true);
            var event = new ScmDomainEvent.PullRequestCreated(prData, webhookContext(1L));

            listener.onPullRequestCreated(event);

            verify(pullRequestRepository, never()).findByIdWithAllForGate(any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void shouldSkipWhenIsMergedTrueButStateIsOpen() {
            // Race condition: merge flag set before state update in webhook
            var prData = createPrData(Issue.State.OPEN, false, true);
            var event = new ScmDomainEvent.PullRequestCreated(prData, webhookContext(1L));

            listener.onPullRequestCreated(event);

            verify(pullRequestRepository, never()).findByIdWithAllForGate(any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void shouldSkipWhenPRNotFound() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new ScmDomainEvent.PullRequestCreated(prData, webhookContext(1L));
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.empty());

            listener.onPullRequestCreated(event);

            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void shouldSkipWhenHeadRefOidIsNull() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new ScmDomainEvent.PullRequestCreated(prData, webhookContext(1L));

            PullRequest pr = mockPullRequest(null, "feature/test", "main");
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));

            listener.onPullRequestCreated(event);

            verify(practiceReviewDetectionGate, never()).evaluate(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void shouldSkipWhenHeadRefNameIsNull() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new ScmDomainEvent.PullRequestCreated(prData, webhookContext(1L));

            PullRequest pr = mockPullRequest("abc123", null, "main");
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));

            listener.onPullRequestCreated(event);

            verify(practiceReviewDetectionGate, never()).evaluate(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void shouldSkipWhenBaseRefNameIsNull() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new ScmDomainEvent.PullRequestCreated(prData, webhookContext(1L));

            PullRequest pr = mockPullRequest("abc123", "feature/test", null);
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));

            listener.onPullRequestCreated(event);

            verify(practiceReviewDetectionGate, never()).evaluate(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }
    }

    @Nested
    class GateIntegrationTests {

        @Test
        void shouldSkipWhenGateReturnsSkip() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new ScmDomainEvent.PullRequestCreated(prData, webhookContext(1L));

            PullRequest pr = mockPullRequest("abc123", "feature/test", "main");
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));
            when(
                practiceReviewDetectionGate.evaluate(pr, TriggerEventNames.PULL_REQUEST_CREATED, TriggerMode.AUTO)
            ).thenReturn(new GateDecision.Skip("no matching practices"));

            listener.onPullRequestCreated(event);

            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void shouldSubmitWhenGateReturnsDetect() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new ScmDomainEvent.PullRequestCreated(prData, webhookContext(99L));

            PullRequest pr = setupHappyPath();

            listener.onPullRequestCreated(event);

            verify(agentJobService).submit(
                eq(WORKSPACE_ID),
                eq(AgentJobType.PULL_REQUEST_REVIEW),
                any(PullRequestReviewSubmissionRequest.class)
            );
        }

        @Test
        void shouldUseWorkspaceIdFromGateNotContext() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            // Context has scopeId=99, but gate resolves workspace with id=42
            var event = new ScmDomainEvent.PullRequestCreated(prData, webhookContext(99L));

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
        void shouldBuildCorrectSubmissionRequest() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new ScmDomainEvent.PullRequestCreated(prData, webhookContext(1L));

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
        void shouldDelegateDraftFilteringToGate() {
            var prData = createPrData(Issue.State.OPEN, true, false);
            var event = new ScmDomainEvent.PullRequestCreated(prData, webhookContext(1L));

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
        void shouldPassPullRequestCreatedTriggerEventName() {
            PullRequest pr = setupHappyPath();
            var prData = createPrData(Issue.State.OPEN, false, false);

            listener.onPullRequestCreated(new ScmDomainEvent.PullRequestCreated(prData, webhookContext(1L)));

            verify(practiceReviewDetectionGate).evaluate(pr, TriggerEventNames.PULL_REQUEST_CREATED, TriggerMode.AUTO);
        }

        @Test
        void shouldPassPullRequestReadyTriggerEventName() {
            PullRequest pr = setupHappyPath();
            var prData = createPrData(Issue.State.OPEN, false, false);

            listener.onPullRequestReady(new ScmDomainEvent.PullRequestReady(prData, webhookContext(1L)));

            verify(practiceReviewDetectionGate).evaluate(pr, TriggerEventNames.PULL_REQUEST_READY, TriggerMode.AUTO);
        }

        @Test
        void shouldPassPullRequestSynchronizedTriggerEventName() {
            PullRequest pr = setupHappyPath();
            var prData = createPrData(Issue.State.OPEN, false, false);

            listener.onPullRequestSynchronized(new ScmDomainEvent.PullRequestSynchronized(prData, webhookContext(1L)));

            verify(practiceReviewDetectionGate).evaluate(
                pr,
                TriggerEventNames.PULL_REQUEST_SYNCHRONIZED,
                TriggerMode.AUTO
            );
        }

        @Test
        void shouldNotPropagateExceptionsFromSubmit() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new ScmDomainEvent.PullRequestCreated(prData, webhookContext(1L));

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
        void shouldNotPropagateExceptionsFromGate() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new ScmDomainEvent.PullRequestCreated(prData, webhookContext(1L));

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
    class PullRequestSynchronizedTests {

        @Test
        void shouldSubmitWhenGatePasses() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new ScmDomainEvent.PullRequestSynchronized(prData, webhookContext(1L));

            setupHappyPath();

            listener.onPullRequestSynchronized(event);

            verify(agentJobService).submit(
                eq(WORKSPACE_ID),
                eq(AgentJobType.PULL_REQUEST_REVIEW),
                any(PullRequestReviewSubmissionRequest.class)
            );
        }

        @Test
        void shouldSkipSyncEvents() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new ScmDomainEvent.PullRequestSynchronized(prData, syncContext());

            listener.onPullRequestSynchronized(event);

            verify(pullRequestRepository, never()).findByIdWithAllForGate(any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }
    }

    @Nested
    class ReviewSubmittedTests {

        @Test
        void shouldSubmitWhenGatePasses() {
            var reviewData = createReviewData();
            var event = new ScmDomainEvent.ReviewSubmitted(reviewData, webhookContext(1L));

            PullRequest pr = mockPullRequest("abc123", "feature/test", "main");
            // ReviewSubmitted path calls PullRequestData.from(pr), which needs repository + author
            Repository repo = new Repository();
            repo.setId(100L);
            repo.setNameWithOwner("owner/repo");
            repo.setDefaultBranch("main");
            pr.setRepository(repo);
            pr.setNumber(PR_NUMBER);
            pr.setTitle("Test PR");
            pr.setHtmlUrl("https://github.com/owner/repo/pull/42");
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
        void shouldSkipSyncEvents() {
            var reviewData = createReviewData();
            var event = new ScmDomainEvent.ReviewSubmitted(reviewData, syncContext());

            listener.onReviewSubmitted(event);

            verify(pullRequestRepository, never()).findByIdWithAllForGate(any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void shouldSkipWhenPRNotFound() {
            var reviewData = createReviewData();
            var event = new ScmDomainEvent.ReviewSubmitted(reviewData, webhookContext(1L));
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.empty());

            listener.onReviewSubmitted(event);

            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void shouldSkipWhenPRIsClosed() {
            var reviewData = createReviewData();
            var event = new ScmDomainEvent.ReviewSubmitted(reviewData, webhookContext(1L));

            PullRequest pr = new PullRequest();
            pr.setState(Issue.State.CLOSED);
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));

            listener.onReviewSubmitted(event);

            verify(practiceReviewDetectionGate, never()).evaluate(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void shouldSkipWhenPRIsMerged() {
            var reviewData = createReviewData();
            var event = new ScmDomainEvent.ReviewSubmitted(reviewData, webhookContext(1L));

            PullRequest pr = new PullRequest();
            pr.setState(Issue.State.MERGED);
            pr.setMerged(true);
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));

            listener.onReviewSubmitted(event);

            verify(practiceReviewDetectionGate, never()).evaluate(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void shouldSkipWhenIsMergedTrueButStateIsOpen() {
            var reviewData = createReviewData();
            var event = new ScmDomainEvent.ReviewSubmitted(reviewData, webhookContext(1L));

            PullRequest pr = new PullRequest();
            pr.setState(Issue.State.OPEN);
            pr.setMerged(true);
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));

            listener.onReviewSubmitted(event);

            verify(practiceReviewDetectionGate, never()).evaluate(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void shouldSkipWhenMissingBranchInfo() {
            var reviewData = createReviewData();
            var event = new ScmDomainEvent.ReviewSubmitted(reviewData, webhookContext(1L));

            PullRequest pr = mockPullRequest(null, "feature/test", "main");
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));

            listener.onReviewSubmitted(event);

            verify(practiceReviewDetectionGate, never()).evaluate(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void shouldSkipWhenGateReturnsSkip() {
            var reviewData = createReviewData();
            var event = new ScmDomainEvent.ReviewSubmitted(reviewData, webhookContext(1L));

            PullRequest pr = mockPullRequest("abc123", "feature/test", "main");
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));
            when(
                practiceReviewDetectionGate.evaluate(pr, TriggerEventNames.REVIEW_SUBMITTED, TriggerMode.AUTO)
            ).thenReturn(new GateDecision.Skip("no matching practices"));

            listener.onReviewSubmitted(event);

            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void shouldNotPropagateExceptionsFromSubmit() {
            var reviewData = createReviewData();
            var event = new ScmDomainEvent.ReviewSubmitted(reviewData, webhookContext(1L));

            PullRequest pr = mockPullRequest("abc123", "feature/test", "main");
            Repository repo = new Repository();
            repo.setId(100L);
            repo.setNameWithOwner("owner/repo");
            repo.setDefaultBranch("main");
            pr.setRepository(repo);
            pr.setNumber(PR_NUMBER);
            pr.setTitle("Test PR");
            pr.setHtmlUrl("https://github.com/owner/repo/pull/42");
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
        void shouldNotPropagateExceptionsFromGate() {
            var reviewData = createReviewData();
            var event = new ScmDomainEvent.ReviewSubmitted(reviewData, webhookContext(1L));

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
                var properties = new PracticeReviewProperties(true, true, false, "", 15, false, false, false); // runForAllUsers=true
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
         * Creates a real PR for collaboration tests with the fields the gate reads set.
         */
        private PullRequest setupCollaborationPR() {
            PullRequest pr = new PullRequest();
            pr.setId(PR_ID);
            pr.setHeadRefOid("abc123");
            pr.setHeadRefName("feature/test");
            pr.setBaseRefName("main");
            pr.setState(Issue.State.OPEN);
            pr.setDraft(false);

            Repository repo = new Repository();
            repo.setNameWithOwner("owner/repo");
            pr.setRepository(repo);

            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));
            return pr;
        }

        @Test
        void listenerWithRealGateSubmitsOnDetect() {
            var fixture = CollaborationFixture.create(agentJobService, pullRequestRepository);

            Workspace workspace = new Workspace();
            workspace.setId(WORKSPACE_ID);
            workspace.setWorkspaceSlug("test-workspace");
            workspace.getFeatures().setPracticesEnabled(true);
            when(fixture.workspaceResolver().resolveForRepository("owner/repo")).thenReturn(Optional.of(workspace));
            when(fixture.agentConfigChecker().hasRunnablePracticeConfig(WORKSPACE_ID, null)).thenReturn(true);

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
            var event = new ScmDomainEvent.PullRequestCreated(prData, webhookContext(1L));
            fixture.listener().onPullRequestCreated(event);

            verify(agentJobService).submit(
                eq(WORKSPACE_ID),
                eq(AgentJobType.PULL_REQUEST_REVIEW),
                any(PullRequestReviewSubmissionRequest.class)
            );
        }

        @Test
        void realGateSkipsWhenNoMatchingPractices() {
            var fixture = CollaborationFixture.create(agentJobService, pullRequestRepository);

            Workspace workspace = new Workspace();
            workspace.setId(WORKSPACE_ID);
            workspace.getFeatures().setPracticesEnabled(true);
            when(fixture.workspaceResolver().resolveForRepository("owner/repo")).thenReturn(Optional.of(workspace));
            when(fixture.agentConfigChecker().hasRunnablePracticeConfig(WORKSPACE_ID, null)).thenReturn(true);

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
            var event = new ScmDomainEvent.PullRequestCreated(prData, webhookContext(1L));
            fixture.listener().onPullRequestCreated(event);

            verify(agentJobService, never()).submit(any(), any(), any());
        }
    }
}
