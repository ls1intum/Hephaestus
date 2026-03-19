package de.tum.in.www1.hephaestus.agent.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@DisplayName("AgentJobEventListener")
class AgentJobEventListenerTest extends BaseUnitTest {

    @Mock
    private AgentJobService agentJobService;

    @Mock
    private PullRequestRepository pullRequestRepository;

    private AgentJobEventListener listener;

    private static final RepositoryRef REPO_REF = new RepositoryRef(100L, "owner/repo", "main");

    @BeforeEach
    void setUp() {
        listener = new AgentJobEventListener(agentJobService, pullRequestRepository);
    }

    private EventPayload.PullRequestData createPrData(Issue.State state, boolean isDraft, boolean isMerged) {
        return new EventPayload.PullRequestData(
            456L,
            42,
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

    private PullRequest mockPullRequest(String headRefOid, String headRefName, String baseRefName) {
        PullRequest pr = mock(PullRequest.class);
        when(pr.getHeadRefOid()).thenReturn(headRefOid);
        when(pr.getHeadRefName()).thenReturn(headRefName);
        when(pr.getBaseRefName()).thenReturn(baseRefName);
        return pr;
    }

    @Nested
    @DisplayName("Filtering")
    class Filtering {

        @Test
        @DisplayName("should skip sync events")
        void shouldSkipSyncEvents() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new DomainEvent.PullRequestCreated(prData, syncContext());

            listener.onPullRequestCreated(event);

            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("should skip draft PRs")
        void shouldSkipDraftPRs() {
            var prData = createPrData(Issue.State.OPEN, true, false);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(1L));

            listener.onPullRequestCreated(event);

            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("should skip closed PRs")
        void shouldSkipClosedPRs() {
            var prData = createPrData(Issue.State.CLOSED, false, false);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(1L));

            listener.onPullRequestCreated(event);

            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("should skip merged PRs")
        void shouldSkipMergedPRs() {
            var prData = createPrData(Issue.State.MERGED, false, true);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(1L));

            listener.onPullRequestCreated(event);

            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("should skip when scopeId is null")
        void shouldSkipNullScopeId() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(null));

            listener.onPullRequestCreated(event);

            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("should skip when PR entity not found")
        void shouldSkipWhenPRNotFound() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(1L));
            when(pullRequestRepository.findByIdWithRepository(456L)).thenReturn(Optional.empty());

            listener.onPullRequestCreated(event);

            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("should skip when headRefOid is null (GitLab)")
        void shouldSkipWhenHeadRefOidIsNull() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(1L));

            PullRequest pr = mockPullRequest(null, "feature/test", "main");
            when(pullRequestRepository.findByIdWithRepository(456L)).thenReturn(Optional.of(pr));

            listener.onPullRequestCreated(event);

            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("should skip when headRefName is null")
        void shouldSkipWhenHeadRefNameIsNull() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(1L));

            PullRequest pr = mockPullRequest("abc123", null, "main");
            when(pullRequestRepository.findByIdWithRepository(456L)).thenReturn(Optional.of(pr));

            listener.onPullRequestCreated(event);

            verify(agentJobService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("should skip when baseRefName is null")
        void shouldSkipWhenBaseRefNameIsNull() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(1L));

            PullRequest pr = mockPullRequest("abc123", "feature/test", null);
            when(pullRequestRepository.findByIdWithRepository(456L)).thenReturn(Optional.of(pr));

            listener.onPullRequestCreated(event);

            verify(agentJobService, never()).submit(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("should submit job for valid PR created event")
        void shouldSubmitJobForValidPRCreated() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(1L));

            PullRequest pr = mockPullRequest("abc123", "feature/test", "main");
            when(pullRequestRepository.findByIdWithRepository(456L)).thenReturn(Optional.of(pr));
            when(agentJobService.submit(any(), any(), any())).thenReturn(Optional.empty());

            listener.onPullRequestCreated(event);

            verify(agentJobService).submit(
                eq(1L),
                eq(AgentJobType.PULL_REQUEST_REVIEW),
                any(PullRequestReviewSubmissionRequest.class)
            );
        }

        @Test
        @DisplayName("should submit job for valid PR ready event")
        void shouldSubmitJobForValidPRReady() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new DomainEvent.PullRequestReady(prData, webhookContext(1L));

            PullRequest pr = mockPullRequest("abc123", "feature/test", "main");
            when(pullRequestRepository.findByIdWithRepository(456L)).thenReturn(Optional.of(pr));
            when(agentJobService.submit(any(), any(), any())).thenReturn(Optional.empty());

            listener.onPullRequestReady(event);

            verify(agentJobService).submit(
                eq(1L),
                eq(AgentJobType.PULL_REQUEST_REVIEW),
                any(PullRequestReviewSubmissionRequest.class)
            );
        }

        @Test
        @DisplayName("should not propagate exceptions from submit")
        void shouldNotPropagateExceptionsFromSubmit() {
            var prData = createPrData(Issue.State.OPEN, false, false);
            var event = new DomainEvent.PullRequestCreated(prData, webhookContext(1L));

            PullRequest pr = mockPullRequest("abc123", "feature/test", "main");
            when(pullRequestRepository.findByIdWithRepository(456L)).thenReturn(Optional.of(pr));
            when(agentJobService.submit(any(), any(), any())).thenThrow(new RuntimeException("DB error"));

            // Should not throw
            listener.onPullRequestCreated(event);
        }
    }
}
