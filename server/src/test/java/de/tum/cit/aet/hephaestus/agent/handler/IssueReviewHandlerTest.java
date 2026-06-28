package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmission;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.task.TaskEnvelopeWriter;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Unit tests for the issue-detection handler's pure submission logic. */
class IssueReviewHandlerTest extends BaseUnitTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Mock
    private WorkspaceContextBuilder workspaceContextBuilder;

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private PracticeDetectionDeliveryService deliveryService;

    @Mock
    private PullRequestCommentPoster commentPoster;

    @Mock
    private FeedbackLedgerRecorder feedbackLedgerRecorder;

    private IssueReviewHandler handler;

    @BeforeEach
    void setUp() {
        handler = new IssueReviewHandler(
            objectMapper,
            workspaceContextBuilder,
            new TaskEnvelopeWriter(objectMapper),
            new PracticeCatalogInjector(objectMapper, practiceRepository),
            new PracticeDetectionResultParser(objectMapper),
            deliveryService,
            commentPoster,
            feedbackLedgerRecorder
        );
    }

    private IssueReviewSubmissionRequest sampleRequest() {
        return new IssueReviewSubmissionRequest(
            777L,
            12,
            123L,
            "owner/repo",
            "Add dark mode",
            "Users want a dark theme toggle in settings.",
            "OPEN",
            java.time.Instant.ofEpochMilli(1_700_000_000_000L)
        );
    }

    @Nested
    class JobType {

        @Test
        void returnsIssueReview() {
            assertThat(handler.jobType()).isEqualTo(AgentJobType.ISSUE_REVIEW);
        }
    }

    @Nested
    class CreateSubmission {

        @Test
        void buildsIssueMetadata() {
            JobSubmission submission = handler.createSubmission(sampleRequest());
            JsonNode metadata = submission.metadata();

            assertThat(metadata.get("artifact_type").asString()).isEqualTo("ISSUE");
            assertThat(metadata.get("repository_id").asLong()).isEqualTo(123L);
            assertThat(metadata.get("repository_full_name").asString()).isEqualTo("owner/repo");
            assertThat(metadata.get("issue_id").asLong()).isEqualTo(777L);
            assertThat(metadata.get("issue_number").asInt()).isEqualTo(12);
            assertThat(metadata.get("title").asString()).isEqualTo("Add dark mode");
            assertThat(metadata.get("state").asString()).isEqualTo("OPEN");
        }

        @Test
        void idempotencyKeyHasDisposableFreshnessSegment() {
            // Key ends in a trailing (updatedAt) freshness segment so extractCooldownKeyPrefix strips it and
            // cooldown scopes per-issue, NOT per-repo. An edited issue (new updatedAt) re-reviews. The "manual"
            // phase segment (no triggerEvent here) keeps an authoring pass distinct from an IssueClosed pass.
            JobSubmission submission = handler.createSubmission(sampleRequest());
            assertThat(submission.idempotencyKey()).isEqualTo("issue_review:owner/repo:12:manual:1700000000000");
        }

        @Test
        void rejectsWrongRequestType() {
            assertThatThrownBy(() -> handler.createSubmission(new WrongRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected IssueReviewSubmissionRequest");
        }
    }

    private record WrongRequest() implements de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmissionRequest {}

    /**
     * The issue-delivery path mirrors the MR path's suppression + soft-failure contract (tested for PRs
     * in {@code FeedbackDeliveryServiceTest}). These lock the issue-side equivalents so flipping the
     * suppression sense (post to closed issues) or letting the swallow propagate (mark good jobs FAILED)
     * cannot ship green.
     */
    @Nested
    class DeliverIssueFeedback {

        private AgentJob issueJob(String state) {
            var job = new AgentJob();
            var workspace = new Workspace();
            workspace.setId(1L);
            job.setWorkspace(workspace);
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("repository_full_name", "owner/repo");
            metadata.put("issue_number", 12);
            metadata.put("state", state);
            job.setMetadata(metadata);
            return job;
        }

        private DeliveryContent note() {
            return new DeliveryContent("One thing to tighten: add acceptance criteria.", List.of());
        }

        @Test
        void closedIssue_isSuppressed_neverPosts() {
            handler.postIssueNote(issueJob("CLOSED"), note());
            verify(commentPoster, never()).postIssueFormattedBody(any(), any());
        }

        @Test
        void openIssue_postsAndRecordsCommentId() {
            AgentJob job = issueJob("OPEN");
            when(commentPoster.postIssueFormattedBody(eq(job), any())).thenReturn("gid://gitlab/Note/9");

            handler.postIssueNote(job, note());

            verify(commentPoster).postIssueFormattedBody(eq(job), any());
            assertThat(job.getDeliveryCommentId()).isEqualTo("gid://gitlab/Note/9");
            // C12: a confirmed post records the ledger.
            verify(feedbackLedgerRecorder).record(eq(job), any(), any(), any());
        }

        @Test
        void posterFailure_isSwallowed_doesNotPropagate() {
            AgentJob job = issueJob("OPEN");
            when(commentPoster.postIssueFormattedBody(eq(job), any())).thenThrow(new RuntimeException("gitlab down"));

            assertThatCode(() -> handler.postIssueNote(job, note())).doesNotThrowAnyException();
            assertThat(job.getDeliveryCommentId()).isNull();
            // C12: a swallowed delivery failure means the student saw nothing — the ledger must NOT record a
            // phantom DELIVERED unit (which would also supersede the real prior, corrupting it like A3).
            verify(feedbackLedgerRecorder, never()).record(any(), any(), any(), any());
        }

        @Test
        void nullCommentId_doesNotRecordPhantomDelivered() {
            // C12: a best-effort post that returns no comment id (vendor returned nothing without raising) means
            // nothing landed — the ledger must NOT record a DELIVERED unit for feedback the student never saw.
            AgentJob job = issueJob("OPEN");
            when(commentPoster.postIssueFormattedBody(eq(job), any())).thenReturn(null);

            handler.postIssueNote(job, note());

            assertThat(job.getDeliveryCommentId()).isNull();
            verify(feedbackLedgerRecorder, never()).record(any(), any(), any(), any());
        }

        @Test
        void noDeliveryContent_isNoop() {
            handler.postIssueNote(issueJob("OPEN"), null);
            verify(commentPoster, never()).postIssueFormattedBody(any(), any());
        }
    }
}
