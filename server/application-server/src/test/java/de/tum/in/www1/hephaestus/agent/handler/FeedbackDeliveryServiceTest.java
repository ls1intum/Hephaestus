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
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.practices.review.PracticeReviewProperties;
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

    private FeedbackDeliveryService service;

    private static final Long WORKSPACE_ID = 99L;
    private static final Long PULL_REQUEST_ID = 456L;
    private static final Long AUTHOR_ID = 789L;
    private static final String APP_BASE_URL = "https://hephaestus.example.com";

    private PracticeReviewProperties reviewProperties;

    @BeforeEach
    void setUp() {
        reviewProperties = new PracticeReviewProperties(false, true, false, APP_BASE_URL);
        service = new FeedbackDeliveryService(
            commentPoster,
            diffNotePoster,
            userPreferencesRepository,
            pullRequestRepository,
            reviewProperties
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
        @DisplayName("posts summary note and diff notes when negative findings present")
        void postsNoteAndDiffNotes() {
            AgentJob job = createJob();
            stubOpenPr();
            when(commentPoster.postFormattedBody(eq(job), any(String.class))).thenReturn("IC_comment123");
            when(diffNotePoster.postDiffNotes(eq(job), any())).thenReturn(new DiffNotePoster.DiffNoteResult(1, 0));

            var diffNotes = List.of(new DiffNote("src/Foo.java", 10, null, "Fix this"));
            var delivery = new DeliveryContent("Fix the tests.", diffNotes);
            service.deliverFeedback(job, delivery);

            verify(commentPoster).postFormattedBody(eq(job), any(String.class));
            verify(diffNotePoster).postDiffNotes(eq(job), eq(diffNotes));
            assertThat(job.getDeliveryCommentId()).isEqualTo("IC_comment123");
        }

        @Test
        @DisplayName("skips posting when PR not found in DB (no stub)")
        void skipsWhenPrNotStubbed() {
            AgentJob job = createJob();

            var delivery = new DeliveryContent("This should not be posted.", List.of());
            service.deliverFeedback(job, delivery);

            verifyNoInteractions(commentPoster);
        }

        @Test
        @DisplayName("skips posting when delivery is null")
        void skipsWhenDeliveryNull() {
            AgentJob job = createJob();

            service.deliverFeedback(job, null);

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
            service.deliverFeedback(job, delivery);

            verifyNoInteractions(commentPoster);
        }

        @Test
        @DisplayName("skips posting when PR is merged and deliverToMerged is false")
        void skipsWhenPrMerged() {
            AgentJob job = createJob();
            var pr = createOpenPr();
            pr.setState(Issue.State.MERGED);
            when(pullRequestRepository.findByIdWithAuthor(PULL_REQUEST_ID)).thenReturn(Optional.of(pr));

            var delivery = new DeliveryContent("Fix stuff.", List.of());
            service.deliverFeedback(job, delivery);

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
            service.deliverFeedback(job, delivery);

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
            service.deliverFeedback(job, delivery);

            verifyNoInteractions(commentPoster);
        }

        @Test
        @DisplayName("skips posting when PR not found")
        void skipsWhenPrNotFound() {
            AgentJob job = createJob();
            when(pullRequestRepository.findByIdWithAuthor(PULL_REQUEST_ID)).thenReturn(Optional.empty());

            var delivery = new DeliveryContent("Fix stuff.", List.of());
            service.deliverFeedback(job, delivery);

            verifyNoInteractions(commentPoster);
        }

        @Test
        @DisplayName("does not set delivery status when postFormattedBody returns null")
        void doesNotSetDeliveryStatusWhenNoteNull() {
            AgentJob job = createJob();
            stubOpenPr();
            when(commentPoster.postFormattedBody(any(), any())).thenReturn(null);

            var delivery = new DeliveryContent("Empty after sanitization.", List.of());
            service.deliverFeedback(job, delivery);

            assertThat(job.getDeliveryCommentId()).isNull();
        }

        @Test
        @DisplayName("delivers feedback when PR author is null (skips preference check)")
        void deliversWhenAuthorNull() {
            AgentJob job = createJob();
            var pr = createOpenPr();
            pr.setAuthor(null);
            when(pullRequestRepository.findByIdWithAuthor(PULL_REQUEST_ID)).thenReturn(Optional.of(pr));
            when(commentPoster.postFormattedBody(any(), any())).thenReturn("IC_comment456");

            var delivery = new DeliveryContent("Fix stuff.", List.of());
            service.deliverFeedback(job, delivery);

            verify(commentPoster).postFormattedBody(eq(job), any(String.class));
            verifyNoInteractions(userPreferencesRepository);
        }

        @Test
        @DisplayName("entity state unchanged after delivery failure")
        void entityStateUnchangedAfterFailure() {
            AgentJob job = createJob();
            stubOpenPr();
            when(commentPoster.postFormattedBody(any(), any())).thenThrow(new RuntimeException("GraphQL timeout"));

            var delivery = new DeliveryContent("Summary.", List.of());
            service.deliverFeedback(job, delivery);

            assertThat(job.getDeliveryCommentId()).isNull();
            assertThat(job.getDeliveryStatus()).isNull();
        }

        @Test
        @DisplayName("posts diff notes when mrNote is null but hasNegative is true")
        void postsDiffNotesWhenMrNoteNull() {
            AgentJob job = createJob();
            stubOpenPr();
            when(diffNotePoster.postDiffNotes(eq(job), any())).thenReturn(new DiffNotePoster.DiffNoteResult(2, 0));

            var diffNotes = List.of(
                new DiffNote("src/Foo.java", 10, null, "Fix this"),
                new DiffNote("src/Bar.java", 20, null, "And this")
            );
            var delivery = new DeliveryContent(null, diffNotes);
            service.deliverFeedback(job, delivery);

            verify(diffNotePoster).postDiffNotes(eq(job), eq(diffNotes));
        }

        @Test
        @DisplayName("does not throw on feedback delivery failure (soft failure)")
        void doesNotThrowOnFailure() {
            AgentJob job = createJob();
            stubOpenPr();
            when(commentPoster.postFormattedBody(any(), any())).thenThrow(new RuntimeException("GraphQL timeout"));

            var delivery = new DeliveryContent("Summary.", List.of());
            service.deliverFeedback(job, delivery);
        }

        @Test
        @DisplayName("skips posting when metadata is null")
        void skipsWhenMetadataNull() {
            AgentJob job = createJob();
            job.setMetadata(null);

            var delivery = new DeliveryContent("Fix stuff.", List.of());
            service.deliverFeedback(job, delivery);

            verifyNoInteractions(commentPoster);
        }

        @Test
        @DisplayName("skips posting when pull_request_id missing from metadata")
        void skipsWhenPullRequestIdMissing() {
            AgentJob job = createJob();
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("repository_full_name", "owner/repo");
            metadata.put("pr_number", 42);
            job.setMetadata(metadata);

            var delivery = new DeliveryContent("Fix stuff.", List.of());
            service.deliverFeedback(job, delivery);

            verifyNoInteractions(commentPoster);
            verifyNoInteractions(pullRequestRepository);
        }
    }

    @Nested
    @DisplayName("formatPracticeNote")
    class FormatPracticeNote {

        @Test
        @DisplayName("includes marker, body, and metadata footer")
        void correctMarkerAndStructure() {
            AgentJob job = createJob();
            job.setStartedAt(Instant.parse("2024-01-01T00:00:00Z"));
            job.setCompletedAt(Instant.parse("2024-01-01T00:01:30Z"));

            String result = FeedbackDeliveryService.formatPracticeNote("Test body content", job, null);

            assertThat(result).contains("<!-- hephaestus:practice-review:" + job.getId() + " -->");
            assertThat(result).contains("Test body content");
            assertThat(result).contains("Hephaestus Agent");
            assertThat(result).doesNotContain("<details>");
            assertThat(result).doesNotContain("<summary>");
        }

        @Test
        @DisplayName("includes preferences footer when appBaseUrl is set")
        void includesPreferencesFooter() {
            AgentJob job = createJob();
            job.setStartedAt(Instant.parse("2024-01-01T00:00:00Z"));
            job.setCompletedAt(Instant.parse("2024-01-01T00:01:30Z"));

            String result = FeedbackDeliveryService.formatPracticeNote("Body", job, "https://hephaestus.example.com");

            assertThat(result).contains("[Hephaestus](https://hephaestus.example.com)");
            assertThat(result).contains("[Configure AI review preferences](https://hephaestus.example.com/settings)");
        }

        @Test
        @DisplayName("omits preferences footer when appBaseUrl is empty")
        void omitsPreferencesFooterWhenEmpty() {
            AgentJob job = createJob();
            job.setStartedAt(Instant.parse("2024-01-01T00:00:00Z"));
            job.setCompletedAt(Instant.parse("2024-01-01T00:01:30Z"));

            String result = FeedbackDeliveryService.formatPracticeNote("Body", job, "");

            assertThat(result).doesNotContain("Configure AI review preferences");
            assertThat(result).contains("Hephaestus Agent");
        }

        @Test
        @DisplayName("omits preferences footer when appBaseUrl is null")
        void omitsPreferencesFooterWhenNull() {
            AgentJob job = createJob();
            job.setStartedAt(Instant.parse("2024-01-01T00:00:00Z"));
            job.setCompletedAt(Instant.parse("2024-01-01T00:01:30Z"));

            String result = FeedbackDeliveryService.formatPracticeNote("Body", job, null);

            assertThat(result).doesNotContain("Configure AI review preferences");
        }
    }
}
