package de.tum.cit.aet.hephaestus.agent.job;

import static de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent.TriggerEventNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.handler.IssueReviewSubmissionRequest;
import de.tum.cit.aet.hephaestus.integration.core.events.EventContext;
import de.tum.cit.aet.hephaestus.integration.core.events.RepositoryRef;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmEventPayload;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.DataSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.practices.review.GateDecision;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewDetectionGate;
import de.tum.cit.aet.hephaestus.practices.review.TriggerMode;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

class IssueAgentJobEventListenerTest extends BaseUnitTest {

    private static final RepositoryRef REPO_REF = new RepositoryRef(100L, "owner/repo", "main");
    private static final Long ISSUE_ID = 789L;
    private static final int ISSUE_NUMBER = 7;
    private static final Long REPO_ID = 100L;
    private static final Long WORKSPACE_ID = 1L;

    @Mock
    private AgentJobService agentJobService;

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private PracticeReviewDetectionGate practiceReviewDetectionGate;

    private IssueAgentJobEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new IssueAgentJobEventListener(agentJobService, issueRepository, practiceReviewDetectionGate);
    }

    // Helpers

    private ScmEventPayload.IssueData createIssueData(Issue.State state) {
        return new ScmEventPayload.IssueData(
            ISSUE_ID,
            ISSUE_NUMBER,
            "Test issue",
            "body",
            state,
            null,
            "https://github.com/owner/repo/issues/7",
            false,
            REPO_REF,
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

    private ScmEventPayload.LabelData createLabelData() {
        return new ScmEventPayload.LabelData(500L, "bug", "ff0000", null);
    }

    /**
     * Creates a real Issue with the fields the listener reads set: id, number, title, body, state,
     * updatedAt, and a repository carrying id + nameWithOwner.
     */
    private Issue createIssue(Issue.State state) {
        Issue issue = new Issue();
        issue.setId(ISSUE_ID);
        issue.setNumber(ISSUE_NUMBER);
        issue.setTitle("Test issue");
        issue.setBody("body");
        issue.setState(state);
        issue.setUpdatedAt(Instant.now());

        Repository repo = new Repository();
        repo.setId(REPO_ID);
        repo.setNameWithOwner("owner/repo");
        issue.setRepository(repo);
        return issue;
    }

    /** Sets up a valid issue mock and a gate Detect decision for happy-path tests. */
    private Issue setupHappyPath() {
        Issue issue = createIssue(Issue.State.OPEN);
        when(issueRepository.findByIdWithRepositoryAndAssignees(ISSUE_ID)).thenReturn(Optional.of(issue));

        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        var detect = new GateDecision.Detect(workspace, List.of());
        when(practiceReviewDetectionGate.evaluateIssue(eq(issue), any(), any())).thenReturn(detect);
        when(agentJobService.submit(any(), any(), any())).thenReturn(Optional.empty());

        return issue;
    }

    // Test Groups

    @Nested
    class FilteringTests {

        @Test
        void shouldSkipSyncEvents() {
            var issueData = createIssueData(Issue.State.OPEN);
            var event = new ScmDomainEvent.IssueCreated(issueData, syncContext());

            listener.onIssueCreated(event);

            verify(issueRepository, never()).findByIdWithRepositoryAndAssignees(anyLong());
            verify(practiceReviewDetectionGate, never()).evaluateIssue(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void shouldSkipClosedIssues() {
            var issueData = createIssueData(Issue.State.CLOSED);
            var event = new ScmDomainEvent.IssueCreated(issueData, webhookContext(1L));

            listener.onIssueCreated(event);

            verify(issueRepository, never()).findByIdWithRepositoryAndAssignees(anyLong());
            verify(practiceReviewDetectionGate, never()).evaluateIssue(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void shouldSkipWhenIssueNotFound() {
            var issueData = createIssueData(Issue.State.OPEN);
            var event = new ScmDomainEvent.IssueCreated(issueData, webhookContext(1L));
            when(issueRepository.findByIdWithRepositoryAndAssignees(ISSUE_ID)).thenReturn(Optional.empty());

            listener.onIssueCreated(event);

            verify(practiceReviewDetectionGate, never()).evaluateIssue(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void shouldSkipWhenIssueHasNullRepository() {
            var issueData = createIssueData(Issue.State.OPEN);
            var event = new ScmDomainEvent.IssueCreated(issueData, webhookContext(1L));

            Issue issue = createIssue(Issue.State.OPEN);
            issue.setRepository(null);
            when(issueRepository.findByIdWithRepositoryAndAssignees(ISSUE_ID)).thenReturn(Optional.of(issue));

            listener.onIssueCreated(event);

            verify(practiceReviewDetectionGate, never()).evaluateIssue(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }
    }

    @Nested
    class GateIntegrationTests {

        @Test
        void shouldSkipWhenGateReturnsSkip() {
            var issueData = createIssueData(Issue.State.OPEN);
            var event = new ScmDomainEvent.IssueCreated(issueData, webhookContext(1L));

            Issue issue = createIssue(Issue.State.OPEN);
            when(issueRepository.findByIdWithRepositoryAndAssignees(ISSUE_ID)).thenReturn(Optional.of(issue));
            when(
                practiceReviewDetectionGate.evaluateIssue(issue, TriggerEventNames.ISSUE_CREATED, TriggerMode.AUTO)
            ).thenReturn(new GateDecision.Skip("no matching practices"));

            listener.onIssueCreated(event);

            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void shouldSubmitWhenGateReturnsDetect() {
            var issueData = createIssueData(Issue.State.OPEN);
            var event = new ScmDomainEvent.IssueCreated(issueData, webhookContext(99L));

            setupHappyPath();

            listener.onIssueCreated(event);

            verify(agentJobService).submit(
                eq(WORKSPACE_ID),
                eq(AgentJobType.ISSUE_REVIEW),
                any(IssueReviewSubmissionRequest.class)
            );
        }

        @Test
        void shouldUseWorkspaceIdFromGateNotContext() {
            var issueData = createIssueData(Issue.State.OPEN);
            // Context has scopeId=99, but the gate resolves workspace with id=42
            var event = new ScmDomainEvent.IssueCreated(issueData, webhookContext(99L));

            Issue issue = createIssue(Issue.State.OPEN);
            when(issueRepository.findByIdWithRepositoryAndAssignees(ISSUE_ID)).thenReturn(Optional.of(issue));

            Workspace workspace = new Workspace();
            workspace.setId(42L);
            var detect = new GateDecision.Detect(workspace, List.of());
            when(
                practiceReviewDetectionGate.evaluateIssue(issue, TriggerEventNames.ISSUE_CREATED, TriggerMode.AUTO)
            ).thenReturn(detect);
            when(agentJobService.submit(any(), any(), any())).thenReturn(Optional.empty());

            listener.onIssueCreated(event);

            verify(agentJobService).submit(eq(42L), eq(AgentJobType.ISSUE_REVIEW), any());
        }

        @Test
        void shouldBuildCorrectSubmissionRequest() {
            var issueData = createIssueData(Issue.State.OPEN);
            var event = new ScmDomainEvent.IssueCreated(issueData, webhookContext(1L));

            Issue issue = createIssue(Issue.State.OPEN);
            Instant updatedAt = Instant.parse("2026-01-01T00:00:00Z");
            issue.setUpdatedAt(updatedAt);
            when(issueRepository.findByIdWithRepositoryAndAssignees(ISSUE_ID)).thenReturn(Optional.of(issue));

            Workspace workspace = new Workspace();
            workspace.setId(WORKSPACE_ID);
            var detect = new GateDecision.Detect(workspace, List.of());
            when(practiceReviewDetectionGate.evaluateIssue(eq(issue), any(), any())).thenReturn(detect);
            when(agentJobService.submit(any(), any(), any())).thenReturn(Optional.empty());

            listener.onIssueCreated(event);

            var captor = ArgumentCaptor.forClass(IssueReviewSubmissionRequest.class);
            verify(agentJobService).submit(eq(WORKSPACE_ID), eq(AgentJobType.ISSUE_REVIEW), captor.capture());

            IssueReviewSubmissionRequest request = captor.getValue();
            assertThat(request.issueId()).isEqualTo(ISSUE_ID);
            assertThat(request.issueNumber()).isEqualTo(ISSUE_NUMBER);
            assertThat(request.repositoryId()).isEqualTo(REPO_ID);
            assertThat(request.repositoryFullName()).isEqualTo("owner/repo");
            assertThat(request.title()).isEqualTo("Test issue");
            assertThat(request.body()).isEqualTo("body");
            assertThat(request.state()).isEqualTo("OPEN");
            assertThat(request.updatedAt()).isEqualTo(updatedAt);
        }

        @Test
        void shouldDefaultBodyToEmptyWhenNull() {
            var issueData = createIssueData(Issue.State.OPEN);
            var event = new ScmDomainEvent.IssueCreated(issueData, webhookContext(1L));

            Issue issue = createIssue(Issue.State.OPEN);
            issue.setBody(null);
            when(issueRepository.findByIdWithRepositoryAndAssignees(ISSUE_ID)).thenReturn(Optional.of(issue));

            Workspace workspace = new Workspace();
            workspace.setId(WORKSPACE_ID);
            when(practiceReviewDetectionGate.evaluateIssue(eq(issue), any(), any())).thenReturn(
                new GateDecision.Detect(workspace, List.of())
            );
            when(agentJobService.submit(any(), any(), any())).thenReturn(Optional.empty());

            listener.onIssueCreated(event);

            var captor = ArgumentCaptor.forClass(IssueReviewSubmissionRequest.class);
            verify(agentJobService).submit(eq(WORKSPACE_ID), eq(AgentJobType.ISSUE_REVIEW), captor.capture());
            assertThat(captor.getValue().body()).isEmpty();
        }

        @Test
        void shouldPassIssueCreatedTriggerEventName() {
            Issue issue = setupHappyPath();
            var issueData = createIssueData(Issue.State.OPEN);

            listener.onIssueCreated(new ScmDomainEvent.IssueCreated(issueData, webhookContext(1L)));

            verify(practiceReviewDetectionGate).evaluateIssue(issue, TriggerEventNames.ISSUE_CREATED, TriggerMode.AUTO);
        }

        @Test
        void shouldPassIssueLabeledTriggerEventName() {
            Issue issue = setupHappyPath();
            var issueData = createIssueData(Issue.State.OPEN);

            listener.onIssueLabeled(new ScmDomainEvent.IssueLabeled(issueData, createLabelData(), webhookContext(1L)));

            verify(practiceReviewDetectionGate).evaluateIssue(issue, TriggerEventNames.ISSUE_LABELED, TriggerMode.AUTO);
        }

        @Test
        void shouldNotPropagateExceptionsFromGate() {
            var issueData = createIssueData(Issue.State.OPEN);
            var event = new ScmDomainEvent.IssueCreated(issueData, webhookContext(1L));

            Issue issue = createIssue(Issue.State.OPEN);
            when(issueRepository.findByIdWithRepositoryAndAssignees(ISSUE_ID)).thenReturn(Optional.of(issue));
            when(
                practiceReviewDetectionGate.evaluateIssue(issue, TriggerEventNames.ISSUE_CREATED, TriggerMode.AUTO)
            ).thenThrow(new RuntimeException("DB connectivity error"));

            // Should not throw — outer catch handles gate exceptions
            listener.onIssueCreated(event);

            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        void shouldNotPropagateExceptionsFromSubmit() {
            var issueData = createIssueData(Issue.State.OPEN);
            var event = new ScmDomainEvent.IssueCreated(issueData, webhookContext(1L));

            Issue issue = createIssue(Issue.State.OPEN);
            when(issueRepository.findByIdWithRepositoryAndAssignees(ISSUE_ID)).thenReturn(Optional.of(issue));

            Workspace workspace = new Workspace();
            workspace.setId(WORKSPACE_ID);
            when(
                practiceReviewDetectionGate.evaluateIssue(issue, TriggerEventNames.ISSUE_CREATED, TriggerMode.AUTO)
            ).thenReturn(new GateDecision.Detect(workspace, List.of()));
            when(agentJobService.submit(any(), any(), any())).thenThrow(new RuntimeException("NATS error"));

            // Should not throw — outer catch handles submit exceptions
            listener.onIssueCreated(event);
        }
    }

    @Nested
    class RetrospectiveIssueClosedTests {

        @Test
        void onIssueClosed_routesClosedIssueThroughGateDespiteClosedState() {
            // The closed terminal state IS this trigger's reason to run — it must reach the gate, unlike the
            // live create/labeled handlers that short-circuit on a closed issue.
            Issue issue = createIssue(Issue.State.CLOSED);
            when(issueRepository.findByIdWithRepositoryAndAssignees(ISSUE_ID)).thenReturn(Optional.of(issue));
            Workspace workspace = new Workspace();
            workspace.setId(WORKSPACE_ID);
            when(
                practiceReviewDetectionGate.evaluateIssue(issue, TriggerEventNames.ISSUE_CLOSED, TriggerMode.AUTO)
            ).thenReturn(new GateDecision.Detect(workspace, List.of()));
            when(agentJobService.submit(any(), any(), any())).thenReturn(Optional.empty());

            var issueData = createIssueData(Issue.State.CLOSED);
            listener.onIssueClosed(new ScmDomainEvent.IssueClosed(issueData, "completed", webhookContext(1L)));

            verify(practiceReviewDetectionGate).evaluateIssue(issue, TriggerEventNames.ISSUE_CLOSED, TriggerMode.AUTO);
            verify(agentJobService).submit(
                eq(WORKSPACE_ID),
                eq(AgentJobType.ISSUE_REVIEW),
                any(IssueReviewSubmissionRequest.class)
            );
        }

        @Test
        void onIssueClosed_skipsSyncEventsToAvoidMassReplayStorm() {
            // The sync-skip guard: a history replay must NOT fire a retrospective review for every issue the
            // repository ever closed. Without it, one sync = a mass-replay job storm.
            var issueData = createIssueData(Issue.State.CLOSED);
            listener.onIssueClosed(new ScmDomainEvent.IssueClosed(issueData, "completed", syncContext()));

            verify(issueRepository, never()).findByIdWithRepositoryAndAssignees(anyLong());
            verify(practiceReviewDetectionGate, never()).evaluateIssue(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }
    }

    @Nested
    class IssueLabeledTests {

        @Test
        void shouldSubmitWhenGatePasses() {
            var issueData = createIssueData(Issue.State.OPEN);
            var event = new ScmDomainEvent.IssueLabeled(issueData, createLabelData(), webhookContext(1L));

            setupHappyPath();

            listener.onIssueLabeled(event);

            verify(agentJobService).submit(
                eq(WORKSPACE_ID),
                eq(AgentJobType.ISSUE_REVIEW),
                any(IssueReviewSubmissionRequest.class)
            );
        }

        @Test
        void shouldSkipSyncEvents() {
            var issueData = createIssueData(Issue.State.OPEN);
            var event = new ScmDomainEvent.IssueLabeled(issueData, createLabelData(), syncContext());

            listener.onIssueLabeled(event);

            verify(issueRepository, never()).findByIdWithRepositoryAndAssignees(anyLong());
            verify(practiceReviewDetectionGate, never()).evaluateIssue(any(), any(), any());
            verify(agentJobService, never()).submit(any(), any(), any());
        }
    }
}
