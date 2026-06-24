package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.account.UserPreferences;
import de.tum.cit.aet.hephaestus.account.UserPreferencesRepository;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.core.spi.FindingAnchor;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFindingChannel;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.finding.TrendDelta;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
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

    @Mock
    private FeedbackLedgerRecorder feedbackLedgerRecorder;

    @Mock
    private de.tum.cit.aet.hephaestus.practices.finding.FindingTrendService findingTrendService;

    private FeedbackDeliveryService service;

    private static final Long WORKSPACE_ID = 99L;
    private static final Long PULL_REQUEST_ID = 456L;
    private static final Long AUTHOR_ID = 789L;
    private static final String APP_BASE_URL = "https://hephaestus.example.com";

    private PracticeReviewProperties reviewProperties;

    @BeforeEach
    void setUp() {
        reviewProperties = new PracticeReviewProperties(false, true, false, APP_BASE_URL, 15, false, false, false);
        service = new FeedbackDeliveryService(
            commentPoster,
            diffNotePoster,
            userPreferencesRepository,
            pullRequestRepository,
            workspaceRepository,
            reviewProperties,
            feedbackLedgerRecorder,
            findingTrendService
        );
        // Inline reconciliation now runs on every OPEN-PR delivery — even with zero diff notes — to clear an
        // earlier run's stale notes. Default it to a benign result so tests that don't pin it don't NPE.
        org.mockito.Mockito.lenient()
            .when(
                diffNotePoster.reconcileInlineNotes(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any()
                )
            )
            .thenReturn(new DiffNotePoster.DiffNoteResult(0, 0, List.of()));
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
            when(diffNotePoster.reconcileInlineNotes(eq(job), any())).thenReturn(
                new DiffNotePoster.DiffNoteResult(1, 0, List.of())
            );

            var diffNotes = List.of(new DiffNote("src/Foo.java", 10, null, "Fix this"));
            var delivery = new DeliveryContent("Fix the tests.", diffNotes);
            service.deliverFeedback(job, delivery);

            verify(commentPoster).postFormattedBody(eq(job), any(String.class));
            verify(diffNotePoster).reconcileInlineNotes(eq(job), eq(diffNotes));
            assertThat(job.getDeliveryCommentId()).isEqualTo("IC_comment123");
        }

        @Test
        @DisplayName("re-review edits the prior summary in place instead of posting a new comment")
        void editsPriorSummaryInPlace() {
            AgentJob job = createJob();
            stubOpenPr();
            // A live summary already exists on this continuity line → edit it, do not post anew.
            when(feedbackLedgerRecorder.priorLiveSummaryRef(eq(job))).thenReturn(Optional.of("IC_prior"));
            when(commentPoster.updateFormattedBody(eq(job), eq("IC_prior"), any(String.class))).thenReturn(
                new PullRequestCommentPoster.UpdateResult(PullRequestCommentPoster.UpdateResult.Kind.EDITED, "IC_prior")
            );

            service.deliverFeedback(job, new DeliveryContent("Re-reviewed: still fix the tests.", List.of()));

            verify(commentPoster).updateFormattedBody(eq(job), eq("IC_prior"), any(String.class));
            verify(commentPoster, never()).postFormattedBody(eq(job), any(String.class));
            assertThat(job.getDeliveryCommentId()).isEqualTo("IC_prior");
        }

        @Test
        @DisplayName("when the prior summary can't be edited (deleted by a human), falls back to a fresh post")
        void fallsBackToNewPostWhenEditCannotLand() {
            AgentJob job = createJob();
            stubOpenPr();
            when(feedbackLedgerRecorder.priorLiveSummaryRef(eq(job))).thenReturn(Optional.of("IC_prior"));
            // Edit found the prior comment GONE (a human deleted it) → post a fresh one.
            when(commentPoster.updateFormattedBody(eq(job), eq("IC_prior"), any(String.class))).thenReturn(
                new PullRequestCommentPoster.UpdateResult(PullRequestCommentPoster.UpdateResult.Kind.GONE, null)
            );
            when(commentPoster.postFormattedBody(eq(job), any(String.class))).thenReturn("IC_new");

            service.deliverFeedback(job, new DeliveryContent("Fresh summary.", List.of()));

            verify(commentPoster).updateFormattedBody(eq(job), eq("IC_prior"), any(String.class));
            verify(commentPoster).postFormattedBody(eq(job), any(String.class));
            assertThat(job.getDeliveryCommentId()).isEqualTo("IC_new");
        }

        @Test
        @DisplayName("a TRANSIENT update error keeps the prior summary and does NOT post a duplicate (B4)")
        void transientUpdateKeepsPriorSummaryNoFreshPost() {
            AgentJob job = createJob();
            stubOpenPr();
            when(feedbackLedgerRecorder.priorLiveSummaryRef(eq(job))).thenReturn(Optional.of("IC_prior"));
            // A rate-limit / network blip → TRANSIENT: keep the live summary, never create-fallback (no double-post).
            when(commentPoster.updateFormattedBody(eq(job), eq("IC_prior"), any(String.class))).thenReturn(
                new PullRequestCommentPoster.UpdateResult(PullRequestCommentPoster.UpdateResult.Kind.TRANSIENT, null)
            );

            service.deliverFeedback(job, new DeliveryContent("Re-reviewed.", List.of()));

            verify(commentPoster, never()).postFormattedBody(eq(job), any(String.class));
            assertThat(job.getDeliveryCommentId()).isEqualTo("IC_prior"); // still points at the live summary
        }

        @Test
        void skipsWhenPrNotStubbed() {
            AgentJob job = createJob();

            var delivery = new DeliveryContent("This should not be posted.", List.of());
            service.deliverFeedback(job, delivery);

            verifyNoInteractions(commentPoster);
        }

        @Test
        @DisplayName("with the progress-footer flag on, a meaningful re-review appends the footer and posts an A4 ping")
        void appendsProgressFooterAndPingsOnMeaningfulReReview() {
            var footerService = serviceWithProgressFooter();
            AgentJob job = createJob();
            stubOpenPr();
            when(feedbackLedgerRecorder.priorLiveSummaryRef(eq(job))).thenReturn(Optional.of("IC_prior"));
            when(commentPoster.updateFormattedBody(eq(job), eq("IC_prior"), any(String.class))).thenReturn(
                new PullRequestCommentPoster.UpdateResult(PullRequestCommentPoster.UpdateResult.Kind.EDITED, "IC_prior")
            );
            when(commentPoster.postFormattedBody(eq(job), any(String.class))).thenReturn("IC_ping");
            when(
                findingTrendService.computeForTarget(WorkArtifact.PULL_REQUEST, PULL_REQUEST_ID, WORKSPACE_ID)
            ).thenReturn(Optional.of(resolvedTrend()));

            footerService.deliverFeedback(job, new DeliveryContent("Re-reviewed.", List.of()));

            // (a) the edited summary body carries the rendered footer
            var body = ArgumentCaptor.forClass(String.class);
            verify(commentPoster).updateFormattedBody(eq(job), eq("IC_prior"), body.capture());
            assertThat(body.getValue()).contains("Progress since your last review").contains("Resolved");
            // (b) the A4 ping fired as a separate notifying note (edit-in-place pings nobody on its own)
            var ping = ArgumentCaptor.forClass(String.class);
            verify(commentPoster).postFormattedBody(eq(job), ping.capture());
            assertThat(ping.getValue()).contains("hephaestus:re-review-ping").contains("Re-reviewed");
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
            when(commentPoster.postFormattedBody(eq(job), any(String.class))).thenReturn("IC_comment789");

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
        void throwsWhenSummaryPostReturnsNoId() {
            // Integrity failure: a real, non-blank summary body was submitted but the provider
            // returned no comment id — the developer sees nothing, so the job must fail loud.
            AgentJob job = createJob();
            stubOpenPr();
            when(commentPoster.postFormattedBody(any(), any())).thenReturn(null);

            var delivery = new DeliveryContent("A real, non-blank summary body.", List.of());

            assertThatThrownBy(() -> service.deliverFeedback(job, delivery)).isInstanceOf(
                de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException.class
            );
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
            when(diffNotePoster.reconcileInlineNotes(eq(job), any())).thenReturn(
                new DiffNotePoster.DiffNoteResult(2, 0, List.of())
            );

            var diffNotes = List.of(
                new DiffNote("src/Foo.java", 10, null, "Fix this"),
                new DiffNote("src/Bar.java", 20, null, "And this")
            );
            var delivery = new DeliveryContent(null, diffNotes);
            service.deliverFeedback(job, delivery);

            verify(diffNotePoster).reconcileInlineNotes(eq(job), eq(diffNotes));
        }

        @Test
        void emptyDiffNotesStillReconcilesToClearStaleNotesOnOpenPr() {
            // G1 regression: a re-review that now produces ZERO inline notes must STILL reconcile so an
            // earlier run's stale line-numbered notes are cleared (the empty-diff pathology). Reconciliation
            // runs with an empty list — the clear half of clear-then-post.
            AgentJob job = createJob();
            stubOpenPr();
            when(commentPoster.postFormattedBody(any(), any())).thenReturn("IC_comment789");

            service.deliverFeedback(job, new DeliveryContent("Summary only, nothing inline.", List.of()));

            verify(diffNotePoster).reconcileInlineNotes(eq(job), eq(List.of()));
        }

        @Test
        void suppressedPrNeverReconciles_noDataLoss() {
            // Symmetric guard: a CLOSED PR is suppressed upstream and must NEVER reach reconciliation —
            // otherwise a re-run on a closed PR would wipe the delivered review (data loss).
            AgentJob job = createJob();
            var pr = createOpenPr();
            pr.setState(Issue.State.CLOSED);
            when(pullRequestRepository.findByIdWithAuthor(PULL_REQUEST_ID)).thenReturn(Optional.of(pr));

            service.deliverFeedback(job, new DeliveryContent("Summary.", List.of()));

            verify(diffNotePoster, never()).reconcileInlineNotes(any(), any());
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
    class SummaryDemotion {

        private InlineFindingChannel.DeliveredSignal landedSignal(String findingFingerprint) {
            return new InlineFindingChannel.DeliveredSignal(
                findingFingerprint,
                new FindingAnchor.DiffAnchor("src/Foo.java", 10, null),
                InlineFindingChannel.Disposition.POSTED,
                "note-1",
                "thread-1"
            );
        }

        @Test
        @DisplayName("re-edits the summary in place with the demoted body once a keyed inline note lands")
        void reEditsSummaryAfterInlineDelivery() {
            AgentJob job = createJob();
            stubOpenPr();
            when(commentPoster.postFormattedBody(eq(job), any(String.class))).thenReturn("IC_summary");
            when(diffNotePoster.reconcileInlineNotes(eq(job), any())).thenReturn(
                new DiffNotePoster.DiffNoteResult(1, 0, List.of(landedSignal("corr-1")))
            );
            // The demotion edit lands.
            when(commentPoster.updateFormattedBody(eq(job), eq("IC_summary"), any(String.class))).thenReturn(
                new PullRequestCommentPoster.UpdateResult(
                    PullRequestCommentPoster.UpdateResult.Kind.EDITED,
                    "IC_summary"
                )
            );

            var delivery = new DeliveryContent(
                "Full-line summary.",
                List.of(new DiffNote("src/Foo.java", 10, null, "x"))
            );
            // The recomposer must be CALLED with exactly the delivered key set, and its output must be what
            // gets edited in place — a no-op that ignored the signals would never invoke updateFormattedBody.
            service.deliverFeedback(job, delivery, deliveredKeys -> {
                assertThat(deliveredKeys).containsExactly("corr-1");
                return "Demoted summary body.";
            });

            ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
            verify(commentPoster).updateFormattedBody(eq(job), eq("IC_summary"), body.capture());
            assertThat(body.getValue()).contains("Demoted summary body.");
        }

        @Test
        @DisplayName("no demotion edit when nothing landed inline — the full-line summary stays as posted")
        void noReEditWhenNoInlineDelivered() {
            AgentJob job = createJob();
            stubOpenPr();
            when(commentPoster.postFormattedBody(eq(job), any(String.class))).thenReturn("IC_summary");
            // Inline reconcile produced ZERO delivered signals (default benign stub, no signals).
            boolean[] recomposed = { false };

            service.deliverFeedback(job, new DeliveryContent("Full-line summary.", List.of()), keys -> {
                recomposed[0] = true;
                return "should-not-be-used";
            });

            // The recomposer is never consulted and the summary is never re-edited.
            assertThat(recomposed[0]).isFalse();
            verify(commentPoster, never()).updateFormattedBody(eq(job), any(String.class), any(String.class));
        }

        @Test
        @DisplayName("a FAILED inline signal contributes no delivered key — its summary line is never demoted")
        void failedSignalDoesNotDemote() {
            AgentJob job = createJob();
            stubOpenPr();
            when(commentPoster.postFormattedBody(eq(job), any(String.class))).thenReturn("IC_summary");
            var failed = new InlineFindingChannel.DeliveredSignal(
                "corr-failed",
                new FindingAnchor.DiffAnchor("src/Foo.java", 10, null),
                InlineFindingChannel.Disposition.FAILED,
                null,
                null
            );
            when(diffNotePoster.reconcileInlineNotes(eq(job), any())).thenReturn(
                new DiffNotePoster.DiffNoteResult(0, 1, List.of(failed))
            );

            service.deliverFeedback(job, new DeliveryContent("Full-line summary.", List.of()), keys -> "demoted");

            // No non-FAILED key → no demotion edit; the full-line fallback summary already posted stands.
            verify(commentPoster, never()).updateFormattedBody(eq(job), any(String.class), any(String.class));
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

    private FeedbackDeliveryService serviceWithProgressFooter() {
        var props = new PracticeReviewProperties(false, true, false, APP_BASE_URL, 15, true, false, false);
        return new FeedbackDeliveryService(
            commentPoster,
            diffNotePoster,
            userPreferencesRepository,
            pullRequestRepository,
            workspaceRepository,
            props,
            feedbackLedgerRecorder,
            findingTrendService
        );
    }

    private static TrendDelta resolvedTrend() {
        var resolved = new TrendDelta.LocusTransition(
            "k1",
            TrendDelta.TransitionStatus.RESOLVED,
            "code-hygiene",
            "Unused import removed",
            Presence.NOT_OBSERVED,
            null,
            Severity.MINOR,
            0.8f
        );
        return new TrendDelta(
            WorkArtifact.PULL_REQUEST,
            PULL_REQUEST_ID,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.parse("2026-06-15T10:00:00Z"),
            Instant.parse("2026-06-14T10:00:00Z"),
            List.of(resolved)
        );
    }
}
