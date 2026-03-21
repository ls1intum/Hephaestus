package de.tum.in.www1.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.account.UserPreferences;
import de.tum.in.www1.hephaestus.account.UserPreferencesRepository;
import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import de.tum.in.www1.hephaestus.agent.job.AgentJobRepository;
import de.tum.in.www1.hephaestus.agent.job.DeliveryStatus;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@DisplayName("FeedbackDeliveryService")
class FeedbackDeliveryServiceTest extends BaseUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private PullRequestCommentPoster commentPoster;

    @Mock
    private DiffNotePoster diffNotePoster;

    @Mock
    private UserPreferencesRepository userPreferencesRepository;

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private AgentJobRepository agentJobRepository;

    private FeedbackDeliveryService service;

    private static final Long WORKSPACE_ID = 99L;
    private static final Long PULL_REQUEST_ID = 456L;
    private static final Long AUTHOR_ID = 789L;

    @BeforeEach
    void setUp() {
        service = new FeedbackDeliveryService(
            commentPoster,
            diffNotePoster,
            userPreferencesRepository,
            pullRequestRepository,
            agentJobRepository
        );
    }

    private AgentJob createJob() {
        var job = new AgentJob();
        var workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        job.setWorkspace(workspace);

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("pull_request_id", PULL_REQUEST_ID);
        metadata.put("repository_full_name", "owner/repo");
        metadata.put("pr_number", 42);
        metadata.put("commit_sha", "abc123");
        job.setMetadata(metadata);

        return job;
    }

    private PullRequest createOpenPr() {
        var pr = new PullRequest();
        pr.setId(PULL_REQUEST_ID);
        pr.setState(Issue.State.OPEN);
        var author = new User();
        author.setId(AUTHOR_ID);
        pr.setAuthor(author);
        return pr;
    }

    private void stubOpenPr() {
        when(pullRequestRepository.findByIdWithAuthor(PULL_REQUEST_ID)).thenReturn(Optional.of(createOpenPr()));
    }

    @Nested
    @DisplayName("deliverFeedback")
    class DeliverFeedback {

        @Test
        @DisplayName("posts summary note when negative findings and mrNote present")
        void postsNoteWhenNegativeAndMrNote() {
            AgentJob job = createJob();
            stubOpenPr();
            when(
                agentJobRepository.findPreviousDeliveryCommentId(eq(WORKSPACE_ID), eq(PULL_REQUEST_ID), any())
            ).thenReturn(Optional.empty());
            when(commentPoster.postPracticeNote(eq(job), eq("Fix the tests."), eq(null))).thenReturn("IC_comment123");

            var delivery = new DeliveryContent("Fix the tests.", List.of());
            service.deliverFeedback(job, delivery, true);

            verify(commentPoster).postPracticeNote(job, "Fix the tests.", null);
            assertThat(job.getDeliveryCommentId()).isEqualTo("IC_comment123");
            assertThat(job.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        }

        @Test
        @DisplayName("skips posting when all findings are positive")
        void skipsWhenAllPositive() {
            AgentJob job = createJob();
            stubOpenPr();

            var delivery = new DeliveryContent("This should not be posted.", List.of());
            service.deliverFeedback(job, delivery, false);

            verifyNoInteractions(commentPoster);
        }

        @Test
        @DisplayName("skips posting when delivery is null")
        void skipsWhenDeliveryNull() {
            AgentJob job = createJob();

            service.deliverFeedback(job, null, true);

            verifyNoInteractions(commentPoster);
            verifyNoInteractions(pullRequestRepository);
        }

        @Test
        @DisplayName("skips posting when PR is closed")
        void skipsWhenPrClosed() {
            AgentJob job = createJob();
            var pr = createOpenPr();
            pr.setState(Issue.State.CLOSED);
            when(pullRequestRepository.findByIdWithAuthor(PULL_REQUEST_ID)).thenReturn(Optional.of(pr));

            var delivery = new DeliveryContent("Fix stuff.", List.of());
            service.deliverFeedback(job, delivery, true);

            verifyNoInteractions(commentPoster);
        }

        @Test
        @DisplayName("skips posting when PR is merged")
        void skipsWhenPrMerged() {
            AgentJob job = createJob();
            var pr = createOpenPr();
            pr.setState(Issue.State.MERGED);
            when(pullRequestRepository.findByIdWithAuthor(PULL_REQUEST_ID)).thenReturn(Optional.of(pr));

            var delivery = new DeliveryContent("Fix stuff.", List.of());
            service.deliverFeedback(job, delivery, true);

            verifyNoInteractions(commentPoster);
        }

        @Test
        @DisplayName("skips posting when PR is draft")
        void skipsWhenPrDraft() {
            AgentJob job = createJob();
            var pr = createOpenPr();
            pr.setDraft(true);
            when(pullRequestRepository.findByIdWithAuthor(PULL_REQUEST_ID)).thenReturn(Optional.of(pr));

            var delivery = new DeliveryContent("Fix stuff.", List.of());
            service.deliverFeedback(job, delivery, true);

            verifyNoInteractions(commentPoster);
        }

        @Test
        @DisplayName("skips posting when user opted out of AI review")
        void skipsWhenAiReviewDisabled() {
            AgentJob job = createJob();
            stubOpenPr();
            var prefs = new UserPreferences();
            prefs.setAiReviewEnabled(false);
            when(userPreferencesRepository.findByUserId(AUTHOR_ID)).thenReturn(Optional.of(prefs));

            var delivery = new DeliveryContent("Fix stuff.", List.of());
            service.deliverFeedback(job, delivery, true);

            verifyNoInteractions(commentPoster);
        }

        @Test
        @DisplayName("re-analysis updates existing comment via previousCommentId")
        void reAnalysisUpdatesExistingComment() {
            AgentJob job = createJob();
            stubOpenPr();
            when(
                agentJobRepository.findPreviousDeliveryCommentId(eq(WORKSPACE_ID), eq(PULL_REQUEST_ID), any())
            ).thenReturn(Optional.of("IC_previous123"));
            when(commentPoster.postPracticeNote(eq(job), eq("Updated review."), eq("IC_previous123"))).thenReturn(
                "IC_previous123"
            );

            var delivery = new DeliveryContent("Updated review.", List.of());
            service.deliverFeedback(job, delivery, true);

            verify(commentPoster).postPracticeNote(job, "Updated review.", "IC_previous123");
        }

        @Test
        @DisplayName("skips diff notes on re-analysis")
        void skipsDiffNotesOnReAnalysis() {
            AgentJob job = createJob();
            stubOpenPr();
            when(
                agentJobRepository.findPreviousDeliveryCommentId(eq(WORKSPACE_ID), eq(PULL_REQUEST_ID), any())
            ).thenReturn(Optional.of("IC_previous123"));
            when(commentPoster.postPracticeNote(any(), any(), any())).thenReturn("IC_previous123");

            var diffNotes = List.of(new DiffNote("src/Foo.java", 10, null, "Fix this"));
            var delivery = new DeliveryContent("Summary.", diffNotes);
            service.deliverFeedback(job, delivery, true);

            verifyNoInteractions(diffNotePoster);
        }

        @Test
        @DisplayName("posts diff notes on first analysis")
        void postsDiffNotesOnFirstAnalysis() {
            AgentJob job = createJob();
            stubOpenPr();
            when(
                agentJobRepository.findPreviousDeliveryCommentId(eq(WORKSPACE_ID), eq(PULL_REQUEST_ID), any())
            ).thenReturn(Optional.empty());
            when(commentPoster.postPracticeNote(any(), any(), any())).thenReturn("IC_new123");
            when(diffNotePoster.postDiffNotes(eq(job), any())).thenReturn(new DiffNotePoster.DiffNoteResult(1, 0));

            var diffNotes = List.of(new DiffNote("src/Foo.java", 10, null, "Fix this"));
            var delivery = new DeliveryContent("Summary.", diffNotes);
            service.deliverFeedback(job, delivery, true);

            verify(diffNotePoster).postDiffNotes(eq(job), eq(diffNotes));
        }

        @Test
        @DisplayName("skips diff notes when hasNegative is false")
        void skipsDiffNotesWhenAllPositive() {
            AgentJob job = createJob();
            stubOpenPr();
            when(
                agentJobRepository.findPreviousDeliveryCommentId(eq(WORKSPACE_ID), eq(PULL_REQUEST_ID), any())
            ).thenReturn(Optional.empty());

            var diffNotes = List.of(new DiffNote("src/Foo.java", 10, null, "Fix this"));
            var delivery = new DeliveryContent("Summary.", diffNotes);
            service.deliverFeedback(job, delivery, false);

            verifyNoInteractions(commentPoster);
            verifyNoInteractions(diffNotePoster);
        }

        @Test
        @DisplayName("skips posting when PR not found")
        void skipsWhenPrNotFound() {
            AgentJob job = createJob();
            when(pullRequestRepository.findByIdWithAuthor(PULL_REQUEST_ID)).thenReturn(Optional.empty());

            var delivery = new DeliveryContent("Fix stuff.", List.of());
            service.deliverFeedback(job, delivery, true);

            verifyNoInteractions(commentPoster);
        }

        @Test
        @DisplayName("does not set delivery status when postPracticeNote returns null")
        void doesNotSetDeliveryStatusWhenNoteNull() {
            AgentJob job = createJob();
            stubOpenPr();
            when(
                agentJobRepository.findPreviousDeliveryCommentId(eq(WORKSPACE_ID), eq(PULL_REQUEST_ID), any())
            ).thenReturn(Optional.empty());
            when(commentPoster.postPracticeNote(any(), any(), any())).thenReturn(null);

            var delivery = new DeliveryContent("Empty after sanitization.", List.of());
            service.deliverFeedback(job, delivery, true);

            assertThat(job.getDeliveryCommentId()).isNull();
            assertThat(job.getDeliveryStatus()).isNotEqualTo(DeliveryStatus.DELIVERED);
        }

        @Test
        @DisplayName("delivers feedback when PR author is null (skips preference check)")
        void deliversWhenAuthorNull() {
            AgentJob job = createJob();
            var pr = createOpenPr();
            pr.setAuthor(null);
            when(pullRequestRepository.findByIdWithAuthor(PULL_REQUEST_ID)).thenReturn(Optional.of(pr));
            when(
                agentJobRepository.findPreviousDeliveryCommentId(eq(WORKSPACE_ID), eq(PULL_REQUEST_ID), any())
            ).thenReturn(Optional.empty());
            when(commentPoster.postPracticeNote(any(), any(), any())).thenReturn("IC_comment456");

            var delivery = new DeliveryContent("Fix stuff.", List.of());
            service.deliverFeedback(job, delivery, true);

            verify(commentPoster).postPracticeNote(eq(job), eq("Fix stuff."), eq(null));
            verifyNoInteractions(userPreferencesRepository);
        }

        @Test
        @DisplayName("entity state unchanged after delivery failure")
        void entityStateUnchangedAfterFailure() {
            AgentJob job = createJob();
            stubOpenPr();
            when(agentJobRepository.findPreviousDeliveryCommentId(any(), any(), any())).thenReturn(Optional.empty());
            when(commentPoster.postPracticeNote(any(), any(), any())).thenThrow(
                new RuntimeException("GraphQL timeout")
            );

            var delivery = new DeliveryContent("Summary.", List.of());
            service.deliverFeedback(job, delivery, true);

            // Entity should NOT be modified on failure
            assertThat(job.getDeliveryCommentId()).isNull();
            assertThat(job.getDeliveryStatus()).isNull();
        }

        @Test
        @DisplayName("posts diff notes when mrNote is null but hasNegative is true")
        void postsDiffNotesWhenMrNoteNull() {
            AgentJob job = createJob();
            stubOpenPr();
            when(
                agentJobRepository.findPreviousDeliveryCommentId(eq(WORKSPACE_ID), eq(PULL_REQUEST_ID), any())
            ).thenReturn(Optional.empty());
            when(diffNotePoster.postDiffNotes(eq(job), any())).thenReturn(new DiffNotePoster.DiffNoteResult(2, 0));

            var diffNotes = List.of(
                new DiffNote("src/Foo.java", 10, null, "Fix this"),
                new DiffNote("src/Bar.java", 20, null, "And this")
            );
            var delivery = new DeliveryContent(null, diffNotes);
            service.deliverFeedback(job, delivery, true);

            // Summary note skipped (mrNote null), but diff notes should still be posted
            verify(diffNotePoster).postDiffNotes(eq(job), eq(diffNotes));
            verifyNoInteractions(commentPoster);
        }

        @Test
        @DisplayName("does not throw on feedback delivery failure (soft failure)")
        void doesNotThrowOnFailure() {
            AgentJob job = createJob();
            stubOpenPr();
            when(agentJobRepository.findPreviousDeliveryCommentId(any(), any(), any())).thenReturn(Optional.empty());
            when(commentPoster.postPracticeNote(any(), any(), any())).thenThrow(
                new RuntimeException("GraphQL timeout")
            );

            var delivery = new DeliveryContent("Summary.", List.of());

            // Should NOT throw — delivery is best-effort
            service.deliverFeedback(job, delivery, true);
        }
    }

    @Nested
    @DisplayName("formatPracticeNote")
    class FormatPracticeNote {

        @Test
        @DisplayName("includes marker, disclaimer, body, and metadata footer")
        void correctMarkerAndStructure() {
            AgentJob job = createJob();
            job.setStartedAt(Instant.parse("2024-01-01T00:00:00Z"));
            job.setCompletedAt(Instant.parse("2024-01-01T00:01:30Z"));

            String result = FeedbackDeliveryService.formatPracticeNote("Test body content", job);

            assertThat(result).contains("<!-- hephaestus:practice-review:" + job.getId() + " -->");
            assertThat(result).contains("automated practice review");
            assertThat(result).contains("Test body content");
            assertThat(result).contains("Hephaestus Agent");
            // Should NOT be wrapped in <details>
            assertThat(result).doesNotContain("<details>");
            assertThat(result).doesNotContain("<summary>");
        }
    }
}
