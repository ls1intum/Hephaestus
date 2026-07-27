package de.tum.cit.aet.hephaestus.agent.job;

import static de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent.TriggerEventNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
import de.tum.cit.aet.hephaestus.practices.spi.PracticeDetectionReadiness;
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

    /**
     * Captures the one submission request the listener handed to the job service for
     * {@code workspaceId}. Every submitting path must assert on the payload, not on the fact that
     * a call happened — the job type and workspace id are identical across all six triggers.
     */
    private PullRequestReviewSubmissionRequest captureSubmission(Long workspaceId) {
        var captor = ArgumentCaptor.forClass(PullRequestReviewSubmissionRequest.class);
        verify(agentJobService).submit(eq(workspaceId), eq(AgentJobType.PULL_REQUEST_REVIEW), captor.capture());
        return captor.getValue();
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

            setupHappyPath();

            listener.onPullRequestCreated(event);

            assertThat(captureSubmission(WORKSPACE_ID).triggerEvent()).isEqualTo(
                TriggerEventNames.PULL_REQUEST_CREATED
            );
        }

        @Test
        void shouldUseWorkspaceIdFromGateNotContext() {
            var prData = createPrData(Issue.State.OPEN, false, false);
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

            var workspaceIdCaptor = ArgumentCaptor.forClass(Long.class);
            verify(agentJobService).submit(
                workspaceIdCaptor.capture(),
                eq(AgentJobType.PULL_REQUEST_REVIEW),
                any(PullRequestReviewSubmissionRequest.class)
            );
            assertThat(workspaceIdCaptor.getValue()).isEqualTo(42L).isNotEqualTo(99L);
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

            // Swallowing is the contract: this listener runs on the webhook consumer, and a thrown
            // exception would NAK the delivery and redeliver the same doomed submission.
            assertThatCode(() -> listener.onPullRequestCreated(event)).doesNotThrowAnyException();
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

            assertThat(captureSubmission(WORKSPACE_ID).triggerEvent()).isEqualTo(
                TriggerEventNames.PULL_REQUEST_SYNCHRONIZED
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

            // A ReviewSubmitted event carries no PullRequestData, so the request is rebuilt from the
            // entity via PullRequestData.from(pr). Assert the rebuilt payload, not just the call.
            var request = captureSubmission(WORKSPACE_ID);
            assertThat(request.triggerEvent()).isEqualTo(TriggerEventNames.REVIEW_SUBMITTED);
            assertThat(request.pullRequest().number()).isEqualTo(PR_NUMBER);
            assertThat(request.pullRequest().repository().nameWithOwner()).isEqualTo("owner/repo");
            assertThat(request.headRefOid()).isEqualTo("abc123");
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
            when(agentJobService.submit(any(), any(), any())).thenThrow(new RuntimeException("submission failed"));

            assertThatCode(() -> listener.onReviewSubmitted(event)).doesNotThrowAnyException();
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
    class RetrospectivePullRequestTests {

        /** A merged PR keeps its branch refs; the retrospective path must NOT short-circuit on merged state. */
        private PullRequest mergedPullRequest() {
            PullRequest pr = mockPullRequest("abc123", "feature/test", "main");
            pr.setState(Issue.State.MERGED);
            pr.setMerged(true);
            return pr;
        }

        @Test
        void onPullRequestMerged_routesThroughGateDespiteMergedState() {
            // The merged terminal state IS the reason this trigger runs — it must reach the gate, unlike the
            // live create/ready/sync handlers that short-circuit on merged.
            PullRequest pr = mergedPullRequest();
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));
            Workspace workspace = new Workspace();
            workspace.setId(WORKSPACE_ID);
            when(
                practiceReviewDetectionGate.evaluate(pr, TriggerEventNames.PULL_REQUEST_MERGED, TriggerMode.AUTO)
            ).thenReturn(new GateDecision.Detect(workspace, List.of()));
            when(agentJobService.submit(any(), any(), any())).thenReturn(Optional.empty());

            var prData = createPrData(Issue.State.MERGED, false, true);
            listener.onPullRequestMerged(new ScmDomainEvent.PullRequestMerged(prData, webhookContext(1L)));

            verify(practiceReviewDetectionGate).evaluate(pr, TriggerEventNames.PULL_REQUEST_MERGED, TriggerMode.AUTO);
            // The trigger the gate saw must be the trigger the job carries, or the runner selects
            // the wrong practice set for a retrospective review.
            assertThat(captureSubmission(WORKSPACE_ID).triggerEvent()).isEqualTo(TriggerEventNames.PULL_REQUEST_MERGED);
        }

        @Test
        void onPullRequestMerged_skipsSyncEvents() {
            // A sync replays EVERY historical merge — retrospective detection is for real-time transitions only.
            var prData = createPrData(Issue.State.MERGED, false, true);
            listener.onPullRequestMerged(new ScmDomainEvent.PullRequestMerged(prData, syncContext()));

            verify(pullRequestRepository, never()).findByIdWithAllForGate(any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void onPullRequestClosed_routesAbandonedCloseThroughGate() {
            PullRequest pr = mockPullRequest("abc123", "feature/test", "main");
            pr.setState(Issue.State.CLOSED);
            when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));
            Workspace workspace = new Workspace();
            workspace.setId(WORKSPACE_ID);
            when(
                practiceReviewDetectionGate.evaluate(pr, TriggerEventNames.PULL_REQUEST_CLOSED, TriggerMode.AUTO)
            ).thenReturn(new GateDecision.Detect(workspace, List.of()));
            when(agentJobService.submit(any(), any(), any())).thenReturn(Optional.empty());

            var prData = createPrData(Issue.State.CLOSED, false, false);
            // wasMerged=false → abandoned close, routed under PULL_REQUEST_CLOSED.
            listener.onPullRequestClosed(new ScmDomainEvent.PullRequestClosed(prData, false, webhookContext(1L)));

            verify(practiceReviewDetectionGate).evaluate(pr, TriggerEventNames.PULL_REQUEST_CLOSED, TriggerMode.AUTO);
            assertThat(captureSubmission(WORKSPACE_ID).triggerEvent()).isEqualTo(TriggerEventNames.PULL_REQUEST_CLOSED);
        }

        @Test
        void onPullRequestClosed_doesNotDoubleFireWhenWasMerged() {
            // On a merge the processors publish BOTH PullRequestClosed(wasMerged=true) AND PullRequestMerged.
            // The merge is owned by onPullRequestMerged, so the closed handler must short-circuit before any DB
            // work to avoid submitting a duplicate job for the same landing.
            var prData = createPrData(Issue.State.MERGED, false, true);
            listener.onPullRequestClosed(new ScmDomainEvent.PullRequestClosed(prData, true, webhookContext(1L)));

            verify(pullRequestRepository, never()).findByIdWithAllForGate(any());
            verify(practiceReviewDetectionGate, never()).evaluate(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void onPullRequestClosed_skipsSyncEvents() {
            var prData = createPrData(Issue.State.CLOSED, false, false);
            listener.onPullRequestClosed(new ScmDomainEvent.PullRequestClosed(prData, false, syncContext()));

            verify(pullRequestRepository, never()).findByIdWithAllForGate(any());
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
            PracticeDetectionReadiness practiceDetectionReadiness,
            PracticeRepository practiceRepository,
            WorkspaceResolver workspaceResolver
        ) {
            static CollaborationFixture create(
                AgentJobService agentJobService,
                PullRequestRepository pullRequestRepository
            ) {
                var userRoleChecker = mock(UserRoleChecker.class);
                var practiceDetectionReadiness = mock(PracticeDetectionReadiness.class);
                var practiceRepository = mock(PracticeRepository.class);
                var workspaceResolver = mock(WorkspaceResolver.class);
                var properties = new PracticeReviewProperties(true, true, false, "", 15, false, false); // runForAllUsers=true
                var realGate = new PracticeReviewDetectionGate(
                    properties,
                    userRoleChecker,
                    practiceDetectionReadiness,
                    practiceRepository,
                    workspaceResolver
                );
                var listener = new AgentJobEventListener(agentJobService, pullRequestRepository, realGate);
                return new CollaborationFixture(
                    listener,
                    userRoleChecker,
                    practiceDetectionReadiness,
                    practiceRepository,
                    workspaceResolver
                );
            }
        }

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
            when(fixture.practiceDetectionReadiness().hasRunnableAgent(WORKSPACE_ID)).thenReturn(true);

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

            // The practice this fixture registers triggers on PULL_REQUEST_CREATED only, so the
            // real gate's Detect must carry that trigger through to the submitted job.
            var request = captureSubmission(WORKSPACE_ID);
            assertThat(request.triggerEvent()).isEqualTo(TriggerEventNames.PULL_REQUEST_CREATED);
            assertThat(request.pullRequest().repository().nameWithOwner()).isEqualTo("owner/repo");
        }

        @Test
        void realGateSkipsWhenNoMatchingPractices() {
            var fixture = CollaborationFixture.create(agentJobService, pullRequestRepository);

            Workspace workspace = new Workspace();
            workspace.setId(WORKSPACE_ID);
            workspace.getFeatures().setPracticesEnabled(true);
            when(fixture.workspaceResolver().resolveForRepository("owner/repo")).thenReturn(Optional.of(workspace));
            when(fixture.practiceDetectionReadiness().hasRunnableAgent(WORKSPACE_ID)).thenReturn(true);

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
