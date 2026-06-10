package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.account.UserPreferences;
import de.tum.cit.aet.hephaestus.account.UserPreferencesRepository;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

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
    private WorkspaceRepository workspaceRepository;

    private FeedbackDeliveryService service;

    private static final Long WORKSPACE_ID = 99L;
    private static final Long PULL_REQUEST_ID = 456L;
    private static final Long AUTHOR_ID = 789L;
    private static final String APP_BASE_URL = "https://hephaestus.example.com";

    private PracticeReviewProperties reviewProperties;

    @BeforeEach
    void setUp() {
        reviewProperties = new PracticeReviewProperties(false, true, false, APP_BASE_URL, 15);
        service = new FeedbackDeliveryService(
            commentPoster,
            diffNotePoster,
            userPreferencesRepository,
            pullRequestRepository,
            workspaceRepository,
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
    class DeliverFeedback {

        @Test
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
        void skipsWhenPrNotStubbed() {
            AgentJob job = createJob();

            var delivery = new DeliveryContent("This should not be posted.", List.of());
            service.deliverFeedback(job, delivery);

            verifyNoInteractions(commentPoster);
        }

        @Test
        void skipsWhenDeliveryNull() {
            AgentJob job = createJob();

            service.deliverFeedback(job, null);

            verifyNoInteractions(commentPoster);
            verifyNoInteractions(pullRequestRepository);
        }

        @Test
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
        void deliversToMergedPrWhenWorkspaceOverridesProperty() {
            // Split-brain guard: fleet property deliverToMerged=false, but this workspace overrides it
            // to true → the merged PR must still be delivered. Gate and delivery must agree per-workspace.
            AgentJob job = createJob();
            var pr = createOpenPr();
            pr.setState(Issue.State.MERGED);
            when(pullRequestRepository.findByIdWithAuthor(PULL_REQUEST_ID)).thenReturn(Optional.of(pr));

            Workspace ws = new Workspace();
            ws.setId(WORKSPACE_ID);
            ws.getReviewSettings().setDeliverToMerged(true);
            when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(ws));

            service.deliverFeedback(job, new DeliveryContent("Fix stuff.", List.of()));

            verify(commentPoster).postFormattedBody(eq(job), any(String.class));
        }

        @Test
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
        void skipsWhenPrNotFound() {
            AgentJob job = createJob();
            when(pullRequestRepository.findByIdWithAuthor(PULL_REQUEST_ID)).thenReturn(Optional.empty());

            var delivery = new DeliveryContent("Fix stuff.", List.of());
            service.deliverFeedback(job, delivery);

            verifyNoInteractions(commentPoster);
        }

        @Test
        void doesNotSetDeliveryStatusWhenNoteNull() {
            AgentJob job = createJob();
            stubOpenPr();
            when(commentPoster.postFormattedBody(any(), any())).thenReturn(null);

            var delivery = new DeliveryContent("Empty after sanitization.", List.of());
            service.deliverFeedback(job, delivery);

            assertThat(job.getDeliveryCommentId()).isNull();
        }

        @Test
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
        void doesNotThrowOnFailure() {
            AgentJob job = createJob();
            stubOpenPr();
            when(commentPoster.postFormattedBody(any(), any())).thenThrow(new RuntimeException("GraphQL timeout"));

            var delivery = new DeliveryContent("Summary.", List.of());
            service.deliverFeedback(job, delivery);
        }

        @Test
        void skipsWhenMetadataNull() {
            AgentJob job = createJob();
            job.setMetadata(null);

            var delivery = new DeliveryContent("Fix stuff.", List.of());
            service.deliverFeedback(job, delivery);

            verifyNoInteractions(commentPoster);
        }

        @Test
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
    class FormatPracticeNote {

        @Test
        @DisplayName("includes marker, body, and metadata footer")
        void correctMarkerAndStructure() {
            AgentJob job = createJob();
            job.setStartedAt(Instant.parse("2024-01-01T00:00:00Z"));
            job.setCompletedAt(Instant.parse("2024-01-01T00:01:30Z"));

            String result = FeedbackDeliveryService.formatPracticeNote("Test body content", job);

            assertThat(result).contains("<!-- hephaestus:practice-review:" + job.getId() + " -->");
            assertThat(result).contains("Test body content");
            assertThat(result).contains("Hephaestus Agent");
            assertThat(result).doesNotContain("<details>");
            assertThat(result).doesNotContain("<summary>");
        }

        @Test
        void noPreferencesLink() {
            AgentJob job = createJob();
            job.setStartedAt(Instant.parse("2024-01-01T00:00:00Z"));
            job.setCompletedAt(Instant.parse("2024-01-01T00:01:30Z"));

            String result = FeedbackDeliveryService.formatPracticeNote("Body", job);

            assertThat(result).doesNotContain("Configure AI review preferences");
            assertThat(result).doesNotContain("[Hephaestus]");
            assertThat(result).contains("Hephaestus Agent");
        }
    }
}
