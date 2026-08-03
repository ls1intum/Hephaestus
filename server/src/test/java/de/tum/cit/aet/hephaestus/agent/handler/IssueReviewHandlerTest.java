package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmission;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.task.TaskEnvelopeWriter;
import de.tum.cit.aet.hephaestus.config.ApplicationProperties;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountPreferencesQuery;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

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

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Mock
    private AccountPreferencesQuery accountPreferencesQuery;

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    private IssueReviewHandler handler;

    private boolean silentModeEngaged;

    @BeforeEach
    void setUp() {
        silentModeEngaged = false;
        handler = new IssueReviewHandler(
            objectMapper,
            workspaceContextBuilder,
            new TaskEnvelopeWriter(objectMapper),
            new PracticeCatalogInjector(objectMapper, practiceRepository),
            new PracticeDetectionResultParser(objectMapper),
            deliveryService,
            commentPoster,
            feedbackLedgerRecorder,
            new PracticeFeedbackDeliveryPolicy(
                issueRepository,
                pullRequestRepository,
                repositoryToMonitorRepository,
                workspaceRepository,
                accountPreferencesQuery,
                new PracticeReviewProperties(false, true, false, 15, false, false),
                () -> silentModeEngaged
            ),
            new PracticeFeedbackCommentFormatter(
                new ApplicationProperties(null, new ApplicationProperties.Webapp("https://hephaestus.example"))
            )
        );
        lenient()
            .when(repositoryToMonitorRepository.existsByWorkspaceIdAndNameWithOwner(1L, "owner/repo"))
            .thenReturn(true);
        lenient().when(workspaceRepository.findById(1L)).thenReturn(Optional.of(activePracticeWorkspace()));
    }

    private Workspace activePracticeWorkspace() {
        var workspace = new Workspace();
        workspace.setId(1L);
        workspace.getFeatures().setPracticesEnabled(true);
        return workspace;
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
            "https://github.com/owner/repo/issues/12",
            java.time.Instant.ofEpochMilli(1_700_000_000_000L),
            null
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
            assertThat(metadata.get("issue_url").asString()).isEqualTo("https://github.com/owner/repo/issues/12");
        }

        @Test
        void idempotencyKeyHasDisposableFreshnessSegment() {
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

    @Nested
    class DeliverIssueFeedback {

        private AgentJob issueJob(String state) {
            var job = new AgentJob();
            var workspace = new Workspace();
            workspace.setId(1L);
            job.setWorkspace(workspace);
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("repository_id", 123L);
            metadata.put("repository_full_name", "owner/repo");
            metadata.put("issue_id", 777L);
            metadata.put("issue_number", 12);
            metadata.put("state", state);
            job.setMetadata(metadata);
            return job;
        }

        private Issue stubCurrentIssue(Issue.State state) {
            Issue issue = new Issue();
            issue.setNumber(12);
            issue.setState(state);
            Repository repository = new Repository();
            repository.setId(123L);
            repository.setNameWithOwner("owner/repo");
            issue.setRepository(repository);
            User author = new User();
            author.setId(5L);
            issue.setAuthor(author);
            when(issueRepository.findByIdWithAuthorAndRepository(777L)).thenReturn(Optional.of(issue));
            return issue;
        }

        private DeliveryContent note() {
            return new DeliveryContent("One thing to tighten: add acceptance criteria.", List.of(), List.of());
        }

        @Test
        void disabledPracticeFeatureStopsDeliveryWithoutLedgerEgress() {
            AgentJob job = issueJob("OPEN");
            Workspace workspace = activePracticeWorkspace();
            workspace.getFeatures().setPracticesEnabled(false);
            when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

            handler.postIssueNote(job, note());

            verify(commentPoster, never()).postIssueFormattedBody(any(), any());
            verify(feedbackLedgerRecorder, never()).recordSuppressedUnit(any(), any(), any());
            verify(feedbackLedgerRecorder, never()).recordUndelivered(any(), any());
            verify(issueRepository, never()).findByIdWithAuthorAndRepository(777L);
            verify(accountPreferencesQuery, never()).preferencesForUserId(5L);
        }

        @Test
        void instanceSilentMode_isSuppressedAheadOfEveryOtherRule() {
            silentModeEngaged = true;
            AgentJob job = issueJob("OPEN");
            DeliveryContent delivery = note();

            handler.postIssueNote(job, delivery);

            verify(commentPoster, never()).postIssueFormattedBody(any(), any());
            verify(feedbackLedgerRecorder).recordSuppressedUnit(
                eq(job),
                eq(delivery),
                eq(FeedbackSuppressionReason.INSTANCE_SILENCED)
            );
            // The brake short-circuits before the policy touches the artifact or the recipient.
            verify(issueRepository, never()).findByIdWithAuthorAndRepository(anyLong());
        }

        @Test
        void closedSnapshotAfterReopen_isSuppressed() {
            AgentJob job = issueJob("CLOSED");
            stubCurrentIssue(Issue.State.OPEN);
            DeliveryContent delivery = note();

            handler.postIssueNote(job, delivery);

            verify(commentPoster, never()).postIssueFormattedBody(any(), any());
            verify(feedbackLedgerRecorder).recordSuppressedUnit(
                eq(job),
                eq(delivery),
                eq(FeedbackSuppressionReason.ARTIFACT_CLOSED)
            );
        }

        @Test
        void currentIssueClosedAfterSubmission_isSuppressed() {
            AgentJob job = issueJob("OPEN");
            stubCurrentIssue(Issue.State.CLOSED);

            handler.postIssueNote(job, note());

            verify(commentPoster, never()).postIssueFormattedBody(any(), any());
            verify(feedbackLedgerRecorder).recordSuppressedUnit(
                eq(job),
                any(),
                eq(FeedbackSuppressionReason.ARTIFACT_CLOSED)
            );
        }

        @Test
        void missingIssue_isSuppressedAsGone() {
            AgentJob job = issueJob("OPEN");

            handler.postIssueNote(job, note());

            verify(commentPoster, never()).postIssueFormattedBody(any(), any());
            verify(feedbackLedgerRecorder).recordSuppressedUnit(
                eq(job),
                any(),
                eq(FeedbackSuppressionReason.ARTIFACT_GONE)
            );
        }

        @Test
        void authorlessIssue_isSuppressedAsGone() {
            AgentJob job = issueJob("OPEN");
            stubCurrentIssue(Issue.State.OPEN).setAuthor(null);

            handler.postIssueNote(job, note());

            verify(commentPoster, never()).postIssueFormattedBody(any(), any());
            verify(feedbackLedgerRecorder).recordSuppressedUnit(
                eq(job),
                any(),
                eq(FeedbackSuppressionReason.ARTIFACT_GONE)
            );
        }

        @Test
        void tombstonedIssue_isSuppressedAsGone() {
            AgentJob job = issueJob("OPEN");
            stubCurrentIssue(Issue.State.OPEN).setDeletedAt(Instant.now());

            handler.postIssueNote(job, note());

            verify(commentPoster, never()).postIssueFormattedBody(any(), any());
            verify(feedbackLedgerRecorder).recordSuppressedUnit(
                eq(job),
                any(),
                eq(FeedbackSuppressionReason.ARTIFACT_GONE)
            );
        }

        @Test
        void mismatchedTargetMetadata_isSuppressedAsGone() {
            AgentJob job = issueJob("OPEN");
            ((ObjectNode) job.getMetadata()).put("repository_id", 999L);
            stubCurrentIssue(Issue.State.OPEN);

            handler.postIssueNote(job, note());

            verify(commentPoster, never()).postIssueFormattedBody(any(), any());
            verify(feedbackLedgerRecorder).recordSuppressedUnit(
                eq(job),
                any(),
                eq(FeedbackSuppressionReason.ARTIFACT_GONE)
            );
        }

        @Test
        void repositoryRemovedFromWorkspace_isSuppressedAsGone() {
            AgentJob job = issueJob("OPEN");
            stubCurrentIssue(Issue.State.OPEN);
            when(repositoryToMonitorRepository.existsByWorkspaceIdAndNameWithOwner(1L, "owner/repo")).thenReturn(false);

            handler.postIssueNote(job, note());

            verify(commentPoster, never()).postIssueFormattedBody(any(), any());
            verify(feedbackLedgerRecorder).recordSuppressedUnit(
                eq(job),
                any(),
                eq(FeedbackSuppressionReason.ARTIFACT_GONE)
            );
        }

        @Test
        void optedOutAuthor_isSuppressedWithoutConversationalEscalation() {
            AgentJob job = issueJob("OPEN");
            stubCurrentIssue(Issue.State.OPEN);
            when(accountPreferencesQuery.preferencesForUserId(5L)).thenReturn(
                Optional.of(new AccountPreferencesQuery.PreferencesView(false, false))
            );

            handler.postIssueNote(job, note());

            verify(commentPoster, never()).postIssueFormattedBody(any(), any());
            verify(feedbackLedgerRecorder).recordSuppressedUnit(
                eq(job),
                any(),
                eq(FeedbackSuppressionReason.RECIPIENT_OPTED_OUT)
            );
            verify(feedbackLedgerRecorder, never()).recordUndelivered(any(), any());
        }

        @Test
        void preferenceLookupFailure_failsClosedWithoutConversationalEscalation() {
            AgentJob job = issueJob("OPEN");
            stubCurrentIssue(Issue.State.OPEN);
            when(accountPreferencesQuery.preferencesForUserId(5L)).thenThrow(
                new IllegalStateException("database unavailable")
            );

            assertThatThrownBy(() -> handler.postIssueNote(job, note())).isInstanceOf(IllegalStateException.class);

            verify(commentPoster, never()).postIssueFormattedBody(any(), any());
            verify(feedbackLedgerRecorder, never()).recordUndelivered(any(), any());
        }

        @Test
        void blankAfterSanitize_isSuppressed_withEmptyAfterSanitizeReason() {
            AgentJob job = issueJob("OPEN");
            stubCurrentIssue(Issue.State.OPEN);
            DeliveryContent delivery = new DeliveryContent("", List.of(), List.of());

            handler.postIssueNote(job, delivery);

            verify(commentPoster, never()).postIssueFormattedBody(any(), any());
            verify(feedbackLedgerRecorder).recordSuppressedUnit(
                eq(job),
                eq(delivery),
                eq(FeedbackSuppressionReason.EMPTY_AFTER_SANITIZE)
            );
        }

        @Test
        void openIssueWithoutPreferences_postsLinkedCommentAndRecordsCommentId() {
            AgentJob job = issueJob("OPEN");
            stubCurrentIssue(Issue.State.OPEN);
            when(commentPoster.postIssueFormattedBody(eq(job), any())).thenReturn("gid://gitlab/Note/9");

            handler.postIssueNote(job, note());

            ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
            verify(commentPoster).postIssueFormattedBody(eq(job), body.capture());
            assertThat(body.getValue()).contains(
                "[Manage comments and Slack reminders](https://hephaestus.example/settings#practice-feedback)"
            );
            assertThat(job.getDeliveryCommentId()).isEqualTo("gid://gitlab/Note/9");
            verify(feedbackLedgerRecorder).record(eq(job), any(), any(), any());
            verify(feedbackLedgerRecorder, never()).recordUndelivered(any(), any());
        }

        @Test
        void posterFailure_isSwallowed_doesNotPropagate() {
            AgentJob job = issueJob("OPEN");
            stubCurrentIssue(Issue.State.OPEN);
            when(commentPoster.postIssueFormattedBody(eq(job), any())).thenThrow(new RuntimeException("gitlab down"));

            assertThatCode(() -> handler.postIssueNote(job, note())).doesNotThrowAnyException();
            assertThat(job.getDeliveryCommentId()).isNull();
            verify(feedbackLedgerRecorder, never()).record(any(), any(), any(), any());
            verify(feedbackLedgerRecorder).recordUndelivered(eq(job), any());
        }

        @Test
        void jobDeliveryException_propagates_andDoesNotRecord() {
            AgentJob job = issueJob("OPEN");
            stubCurrentIssue(Issue.State.OPEN);
            when(commentPoster.postIssueFormattedBody(eq(job), any())).thenThrow(
                new de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException("delivery failed")
            );

            assertThatThrownBy(() -> handler.postIssueNote(job, note())).isInstanceOf(
                de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException.class
            );
            assertThat(job.getDeliveryCommentId()).isNull();
            verify(feedbackLedgerRecorder, never()).record(any(), any(), any(), any());
            verify(feedbackLedgerRecorder).recordUndelivered(eq(job), any());
        }

        @Test
        void nullCommentId_doesNotRecordPhantomDelivered() {
            AgentJob job = issueJob("OPEN");
            stubCurrentIssue(Issue.State.OPEN);
            when(commentPoster.postIssueFormattedBody(eq(job), any())).thenReturn(null);

            handler.postIssueNote(job, note());

            assertThat(job.getDeliveryCommentId()).isNull();
            verify(feedbackLedgerRecorder, never()).record(any(), any(), any(), any());
            verify(feedbackLedgerRecorder).recordUndelivered(eq(job), any());
        }

        @Test
        void noDeliveryContent_isNoop() {
            handler.postIssueNote(issueJob("OPEN"), null);
            verify(commentPoster, never()).postIssueFormattedBody(any(), any());
            verify(feedbackLedgerRecorder, never()).recordUndelivered(any(), any());
        }
    }
}
