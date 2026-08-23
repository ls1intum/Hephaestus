package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliverySuppressedException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.config.ApplicationProperties;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountPreferencesQuery;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackAnchor;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFeedbackChannel;
import de.tum.cit.aet.hephaestus.integration.core.spi.ReviewSubject;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyEvaluationRecorder;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.observation.TrendDelta;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewCoverageService;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    private AccountPreferencesQuery accountPreferencesQuery;

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private FeedbackLedgerRecorder feedbackLedgerRecorder;

    @Mock
    private de.tum.cit.aet.hephaestus.practices.observation.ObservationTrendService observationTrendService;

    @Mock
    private PracticeReviewCoverageService coverageService;

    @Mock
    private DeliveryPolicyEvaluationRecorder evaluationRecorder;

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private PracticeFeedbackDispatchService dispatchService;

    private FeedbackDeliveryService service;

    private static final Long WORKSPACE_ID = 99L;
    private static final Long PULL_REQUEST_ID = 456L;
    private static final Long REPOSITORY_ID = 123L;
    private static final Long AUTHOR_ID = 789L;
    private static final String APP_BASE_URL = "https://hephaestus.example.com";

    private PracticeReviewProperties reviewProperties;
    private PracticeFeedbackCommentFormatter commentFormatter;

    private boolean silentModeEngaged;

    @BeforeEach
    void setUp() {
        silentModeEngaged = false;
        reviewProperties = reviewProperties(false);
        commentFormatter = new PracticeFeedbackCommentFormatter(
            new ApplicationProperties(null, new ApplicationProperties.Webapp(APP_BASE_URL))
        );
        service = new FeedbackDeliveryService(
            commentPoster,
            diffNotePoster,
            new PracticeFeedbackDeliveryPolicy(
                issueRepository,
                pullRequestRepository,
                repositoryToMonitorRepository,
                workspaceRepository,
                accountPreferencesQuery,
                reviewProperties,
                () -> silentModeEngaged,
                coverageService,
                evaluationRecorder,
                practiceRepository
            ),
            reviewProperties,
            feedbackLedgerRecorder,
            observationTrendService,
            commentFormatter,
            dispatchService
        );
        org.mockito.Mockito.lenient()
            .when(
                diffNotePoster.reconcileInlineNotes(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any()
                )
            )
            .thenReturn(new DiffNotePoster.DiffNoteResult(0, 0, List.of()));
        org.mockito.Mockito.lenient()
            .when(repositoryToMonitorRepository.existsByWorkspaceIdAndNameWithOwner(WORKSPACE_ID, "owner/repo"))
            .thenReturn(true);
        org.mockito.Mockito.lenient()
            .when(coverageService.assess(any(), any(), any(), any(), anyBoolean()))
            .thenReturn(
                new PracticeReviewCoverageService.CoverageAssessment(
                    de.tum.cit.aet.hephaestus.workspace.settings.ReviewRepositoryMode.ALL_MONITORED,
                    de.tum.cit.aet.hephaestus.workspace.settings.ReviewPersonMode.ALL_ELIGIBLE,
                    PracticeReviewCoverageService.SubjectStatus.RESOLVED_LINKED_HUMAN,
                    true,
                    true,
                    true,
                    true
                )
            );
        Practice automaticPractice = new Practice();
        automaticPractice.setSlug("practice");
        automaticPractice.setAutonomy(PracticeAutonomy.AUTOMATIC);
        org.mockito.Mockito.lenient()
            .when(practiceRepository.findByWorkspaceIdAndSlugIn(eq(WORKSPACE_ID), any()))
            .thenReturn(List.of(automaticPractice));
        org.mockito.Mockito.lenient()
            .when(workspaceRepository.findById(WORKSPACE_ID))
            .thenReturn(Optional.of(activePracticeWorkspace()));
        org.mockito.Mockito.lenient()
            .when(accountPreferencesQuery.preferencesForUserId(AUTHOR_ID))
            .thenReturn(Optional.of(new AccountPreferencesQuery.PreferencesView(false, true)));
        org.mockito.Mockito.lenient()
            .when(
                coverageService.admits(
                    any(Workspace.class),
                    nullable(String.class),
                    nullable(String.class),
                    nullable(ReviewSubject.class)
                )
            )
            .thenReturn(true);
        stubSummaryDispatch();
    }

    private Workspace activePracticeWorkspace() {
        var workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        workspace.getFeatures().setPracticesEnabled(true);
        return workspace;
    }

    private AgentJob createJob() {
        var job = new AgentJob();
        var workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        job.setWorkspace(workspace);

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("pull_request_id", PULL_REQUEST_ID);
        metadata.put("repository_id", REPOSITORY_ID);
        metadata.put("repository_full_name", "owner/repo");
        metadata.put("pr_number", 42);
        metadata.put("commit_sha", "abc123");
        job.setMetadata(metadata);

        return job;
    }

    private PullRequest createOpenPr() {
        var pr = new PullRequest();
        pr.setId(PULL_REQUEST_ID);
        pr.setNumber(42);
        pr.setState(Issue.State.OPEN);
        var repository = new Repository();
        repository.setId(REPOSITORY_ID);
        repository.setNameWithOwner("owner/repo");
        pr.setRepository(repository);
        var author = new User();
        author.setId(AUTHOR_ID);
        pr.setAuthor(author);
        return pr;
    }

    private void stubOpenPr() {
        when(pullRequestRepository.findByIdWithAuthorAndRepository(PULL_REQUEST_ID)).thenReturn(
            Optional.of(createOpenPr())
        );
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
            var delivery = new DeliveryContent("Fix the tests.", diffNotes, List.of());
            service.deliverFeedback(job, delivery, null, Set.of("practice"));

            verify(commentPoster).postFormattedBody(eq(job), any(String.class));
            verify(diffNotePoster).reconcileInlineNotes(eq(job), eq(diffNotes));
            assertThat(job.getDeliveryCommentId()).isEqualTo("IC_comment123");
        }

        @Test
        @DisplayName("re-review edits the prior summary in place instead of posting a new comment")
        void editsPriorSummaryInPlace() {
            AgentJob job = createJob();
            stubOpenPr();
            when(feedbackLedgerRecorder.priorLiveSummaryRef(eq(job))).thenReturn(Optional.of("IC_prior"));
            when(commentPoster.updateFormattedBody(eq(job), eq("IC_prior"), any(String.class))).thenReturn(
                new PullRequestCommentPoster.UpdateResult(PullRequestCommentPoster.UpdateResult.Kind.EDITED, "IC_prior")
            );

            service.deliverFeedback(
                job,
                new DeliveryContent("Re-reviewed: still fix the tests.", List.of(), List.of()),
                null,
                Set.of("practice")
            );

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
            when(commentPoster.updateFormattedBody(eq(job), eq("IC_prior"), any(String.class))).thenReturn(
                new PullRequestCommentPoster.UpdateResult(PullRequestCommentPoster.UpdateResult.Kind.GONE, null)
            );
            when(commentPoster.postFormattedBody(eq(job), any(String.class))).thenReturn("IC_new");

            service.deliverFeedback(
                job,
                new DeliveryContent("Fresh summary.", List.of(), List.of()),
                null,
                Set.of("practice")
            );

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
            when(commentPoster.updateFormattedBody(eq(job), eq("IC_prior"), any(String.class))).thenReturn(
                new PullRequestCommentPoster.UpdateResult(PullRequestCommentPoster.UpdateResult.Kind.TRANSIENT, null)
            );

            service.deliverFeedback(
                job,
                new DeliveryContent("Re-reviewed.", List.of(), List.of()),
                null,
                Set.of("practice")
            );

            verify(commentPoster, never()).postFormattedBody(eq(job), any(String.class));
            assertThat(job.getDeliveryCommentId()).isEqualTo("IC_prior");
            verify(feedbackLedgerRecorder).record(
                eq(job),
                any(),
                eq(ArtifactKinds.PULL_REQUEST),
                eq(List.of()),
                eq(false),
                eq(false)
            );
        }

        @Test
        void transientSummaryWithInlineDeliveryRecordsInlineOnly() {
            AgentJob job = createJob();
            stubOpenPr();
            when(feedbackLedgerRecorder.priorLiveSummaryRef(eq(job))).thenReturn(Optional.of("IC_prior"));
            when(commentPoster.updateFormattedBody(eq(job), eq("IC_prior"), any(String.class))).thenReturn(
                new PullRequestCommentPoster.UpdateResult(PullRequestCommentPoster.UpdateResult.Kind.TRANSIENT, null)
            );
            var note = new DiffNote("src/Foo.java", 10, null, "Fix this", "ck-foo");
            var signal = new InlineFeedbackChannel.DeliveredSignal(
                "ck-foo",
                new FeedbackAnchor.DiffAnchor("src/Foo.java", 10, null),
                InlineFeedbackChannel.Disposition.POSTED,
                "note-1",
                "disc-1"
            );
            when(diffNotePoster.reconcileInlineNotes(eq(job), any())).thenReturn(
                new DiffNotePoster.DiffNoteResult(1, 0, List.of(signal))
            );
            DeliveryContent delivery = new DeliveryContent("Re-reviewed.", List.of(note), List.of());

            service.deliverFeedback(job, delivery, null, Set.of("practice"));

            verify(commentPoster, never()).postFormattedBody(eq(job), any(String.class));
            verify(feedbackLedgerRecorder).record(
                eq(job),
                eq(delivery),
                eq(ArtifactKinds.PULL_REQUEST),
                eq(List.of(signal)),
                eq(false),
                eq(true)
            );
        }

        @Test
        void skipsWhenPrNotStubbed() {
            AgentJob job = createJob();

            var delivery = new DeliveryContent("This should not be posted.", List.of(), List.of());
            service.deliverFeedback(job, delivery, null, Set.of("practice"));

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
            when(commentPoster.postAside(eq(job), any(String.class), any(String.class))).thenReturn("IC_ping");
            when(
                observationTrendService.computeForTarget(ArtifactKinds.PULL_REQUEST, PULL_REQUEST_ID, WORKSPACE_ID)
            ).thenReturn(Optional.of(resolvedTrend()));

            footerService.deliverFeedback(
                job,
                new DeliveryContent("Re-reviewed.", List.of(), List.of()),
                null,
                Set.of("practice")
            );

            var body = ArgumentCaptor.forClass(String.class);
            verify(commentPoster).updateFormattedBody(eq(job), eq("IC_prior"), body.capture());
            assertThat(body.getValue()).contains("Progress since your last review").contains("Resolved");
            // The ping carries its own marker. Under the summary's, reconciliation would find the ping,
            // adopt it as the summary, and edit it in place on the next review.
            var pingBody = ArgumentCaptor.forClass(String.class);
            var pingMarker = ArgumentCaptor.forClass(String.class);
            verify(commentPoster).postAside(eq(job), pingBody.capture(), pingMarker.capture());
            assertThat(pingBody.getValue()).contains("Re-reviewed");
            assertThat(pingMarker.getValue()).startsWith("<!-- hephaestus:re-review-ping:");
            verify(commentPoster, never()).postFormattedBody(eq(job), any(String.class));
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
            when(pullRequestRepository.findByIdWithAuthorAndRepository(PULL_REQUEST_ID)).thenReturn(Optional.of(pr));

            var delivery = new DeliveryContent("Fix stuff.", List.of(), List.of());
            service.deliverFeedback(job, delivery, null, Set.of("practice"));

            verifyNoInteractions(commentPoster);
            verify(feedbackLedgerRecorder).recordSuppressedUnit(
                eq(job),
                eq(delivery),
                eq(FeedbackSuppressionReason.ARTIFACT_CLOSED)
            );
        }

        @Test
        void instanceSilentMode_isSuppressedAheadOfEveryOtherRule() {
            silentModeEngaged = true;
            AgentJob job = createJob();

            var delivery = new DeliveryContent("Fix stuff.", List.of(), List.of());
            service.deliverFeedback(job, delivery, null, Set.of("practice"));

            verifyNoInteractions(commentPoster);
            verify(feedbackLedgerRecorder).recordSuppressedUnit(
                eq(job),
                eq(delivery),
                eq(FeedbackSuppressionReason.INSTANCE_SILENCED)
            );
            verifyNoInteractions(pullRequestRepository);
        }

        @Test
        void shouldRecordSuppressionInsteadOfFailureWhenSummaryEgressCloses() {
            AgentJob job = createJob();
            stubOpenPr();
            var delivery = new DeliveryContent("Fix stuff.", List.of(), List.of());
            when(commentPoster.postFormattedBody(eq(job), any(String.class))).thenThrow(
                new JobDeliverySuppressedException("Silent Mode engaged", new IllegalStateException())
            );

            service.deliverFeedback(job, delivery, null, Set.of("practice"));

            verify(feedbackLedgerRecorder).recordSuppressedUnit(
                job,
                delivery,
                FeedbackSuppressionReason.INSTANCE_SILENCED
            );
            verify(feedbackLedgerRecorder, never()).recordUndelivered(any(), any());
        }

        @Test
        void shouldRecordLandedSummaryWhenInlineEgressCloses() {
            AgentJob job = createJob();
            stubOpenPr();
            var delivery = new DeliveryContent(
                "summary",
                List.of(new DiffNote("src/Foo.java", 10, null, "inline", "key")),
                List.of()
            );
            when(commentPoster.postFormattedBody(eq(job), any(String.class))).thenReturn("IC_landed");
            when(diffNotePoster.reconcileInlineNotes(eq(job), any())).thenThrow(
                new JobDeliverySuppressedException("Silent Mode engaged", new IllegalStateException())
            );

            service.deliverFeedback(job, delivery, null, Set.of("practice"));

            verify(feedbackLedgerRecorder).recordWithoutConversation(
                job,
                delivery,
                ArtifactKinds.PULL_REQUEST,
                List.of(),
                true,
                false
            );
            verify(feedbackLedgerRecorder).recordSuppressedRemainder(
                job,
                delivery,
                FeedbackSuppressionReason.INSTANCE_SILENCED,
                List.of()
            );
        }

        @Test
        void shouldKeepPriorSummaryLiveWhenInlineDeliveryIsSuppressedAfterTransientEdit() {
            AgentJob job = createJob();
            stubOpenPr();
            var delivery = new DeliveryContent(
                "updated summary",
                List.of(new DiffNote("src/Foo.java", 10, null, "inline", "key")),
                List.of()
            );
            when(feedbackLedgerRecorder.priorLiveSummaryRef(job)).thenReturn(Optional.of("IC_prior"));
            when(commentPoster.updateFormattedBody(eq(job), eq("IC_prior"), any(String.class))).thenReturn(
                new PullRequestCommentPoster.UpdateResult(PullRequestCommentPoster.UpdateResult.Kind.TRANSIENT, null)
            );
            when(diffNotePoster.reconcileInlineNotes(eq(job), any())).thenThrow(
                new JobDeliverySuppressedException("Silent Mode engaged", new IllegalStateException())
            );

            service.deliverFeedback(job, delivery, null, Set.of("practice"));

            verify(feedbackLedgerRecorder).recordSuppressedUnit(
                job,
                delivery,
                FeedbackSuppressionReason.INSTANCE_SILENCED
            );
            verify(feedbackLedgerRecorder, never()).recordWithoutConversation(
                any(),
                any(),
                any(),
                any(),
                anyBoolean(),
                anyBoolean()
            );
            verify(feedbackLedgerRecorder, never()).recordSuppressedRemainder(any(), any(), any(), any());
        }

        @Test
        void shouldRecordLandedWritesAndRemainderWhenInlineBatchIsSuppressed() {
            AgentJob job = createJob();
            stubOpenPr();
            var delivery = new DeliveryContent(
                "summary",
                List.of(
                    new DiffNote("src/Foo.java", 10, null, "inline", "key-1"),
                    new DiffNote("src/Bar.java", 20, null, "suppressed", "key-2")
                ),
                List.of()
            );
            InlineFeedbackChannel.DeliveredSignal signal = new InlineFeedbackChannel.DeliveredSignal(
                "key-1",
                new FeedbackAnchor.DiffAnchor("src/Foo.java", 10, null),
                InlineFeedbackChannel.Disposition.POSTED,
                "note-1",
                "discussion-1"
            );
            when(commentPoster.postFormattedBody(eq(job), any(String.class))).thenReturn("IC_landed");
            when(diffNotePoster.reconcileInlineNotes(eq(job), any())).thenReturn(
                new DiffNotePoster.DiffNoteResult(1, 0, List.of(signal), true, List.of("key-2"))
            );

            service.deliverFeedback(job, delivery, null, Set.of("practice"));

            verify(feedbackLedgerRecorder).recordWithoutConversation(
                job,
                delivery,
                ArtifactKinds.PULL_REQUEST,
                List.of(signal),
                true,
                true
            );
            verify(feedbackLedgerRecorder).recordSuppressedRemainder(
                job,
                delivery,
                FeedbackSuppressionReason.INSTANCE_SILENCED,
                List.of("key-2")
            );
        }

        @Test
        void skipsWhenPrMerged() {
            AgentJob job = createJob();
            var pr = createOpenPr();
            pr.setState(Issue.State.MERGED);
            when(pullRequestRepository.findByIdWithAuthorAndRepository(PULL_REQUEST_ID)).thenReturn(Optional.of(pr));

            var delivery = new DeliveryContent("Fix stuff.", List.of(), List.of());
            service.deliverFeedback(job, delivery, null, Set.of("practice"));

            verifyNoInteractions(commentPoster);
            verify(feedbackLedgerRecorder).recordSuppressedUnit(
                eq(job),
                eq(delivery),
                eq(FeedbackSuppressionReason.ARTIFACT_MERGED)
            );
        }

        @Test
        void deliversToMergedPrWhenWorkspaceOverridesProperty() {
            AgentJob job = createJob();
            var pr = createOpenPr();
            pr.setState(Issue.State.MERGED);
            when(pullRequestRepository.findByIdWithAuthorAndRepository(PULL_REQUEST_ID)).thenReturn(Optional.of(pr));

            Workspace ws = activePracticeWorkspace();
            ws.getReviewSettings().setDeliverToMerged(true);
            when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(ws));
            when(commentPoster.postFormattedBody(eq(job), any(String.class))).thenReturn("IC_comment789");

            service.deliverFeedback(
                job,
                new DeliveryContent("Fix stuff.", List.of(), List.of()),
                null,
                Set.of("practice")
            );

            var body = ArgumentCaptor.forClass(String.class);
            verify(commentPoster).postFormattedBody(eq(job), body.capture());
            assertThat(job.getDeliveryCommentId()).isEqualTo("IC_comment789");
            assertThat(body.getValue()).contains("Fix stuff.");
        }

        // A draft is not a reason to withhold: the binding already decided this was worth reviewing before
        // the job was submitted, so suppressing here would silently drop feedback it asked for.
        @Test
        void deliversToADraftBecauseTheBindingAlreadyDecidedItWasWorthReviewing() {
            AgentJob job = createJob();
            var pr = createOpenPr();
            pr.setDraft(true);
            when(pullRequestRepository.findByIdWithAuthorAndRepository(PULL_REQUEST_ID)).thenReturn(Optional.of(pr));
            when(commentPoster.postFormattedBody(eq(job), any(String.class))).thenReturn("IC_draft123");

            var delivery = new DeliveryContent("Fix stuff.", List.of(), List.of());
            service.deliverFeedback(job, delivery, null, Set.of("practice"));

            verify(feedbackLedgerRecorder, never()).recordSuppressedUnit(any(), any(), any());
            var body = ArgumentCaptor.forClass(String.class);
            verify(commentPoster).postFormattedBody(eq(job), body.capture());
            assertThat(body.getValue()).contains("Fix stuff.");
        }

        @Test
        void skipsWhenPracticeFeedbackDeliveryDisabled() {
            AgentJob job = createJob();
            stubOpenPr();
            when(accountPreferencesQuery.preferencesForUserId(AUTHOR_ID)).thenReturn(
                Optional.of(new AccountPreferencesQuery.PreferencesView(false, false))
            );

            var delivery = new DeliveryContent("Fix stuff.", List.of(), List.of());
            service.deliverFeedback(job, delivery, null, Set.of("practice"));

            verifyNoInteractions(commentPoster);
            verify(feedbackLedgerRecorder).recordSuppressedUnit(
                eq(job),
                eq(delivery),
                eq(FeedbackSuppressionReason.RECIPIENT_OPTED_OUT)
            );
        }

        @Test
        void skipsWhenRecipientCannotBeResolved() {
            AgentJob job = createJob();
            var pr = createOpenPr();
            pr.setAuthor(null);
            when(pullRequestRepository.findByIdWithAuthorAndRepository(PULL_REQUEST_ID)).thenReturn(Optional.of(pr));

            var delivery = new DeliveryContent("Fix stuff.", List.of(), List.of());
            service.deliverFeedback(job, delivery, null, Set.of("practice"));

            verifyNoInteractions(commentPoster);
            verifyNoInteractions(accountPreferencesQuery);
            verify(feedbackLedgerRecorder).recordSuppressedUnit(
                eq(job),
                eq(delivery),
                eq(FeedbackSuppressionReason.ARTIFACT_GONE)
            );
        }

        @Test
        void skipsWhenLivePullRequestDoesNotMatchDeliveryTarget() {
            AgentJob job = createJob();
            var pr = createOpenPr();
            ((ObjectNode) job.getMetadata()).put("repository_id", 999L);
            when(pullRequestRepository.findByIdWithAuthorAndRepository(PULL_REQUEST_ID)).thenReturn(Optional.of(pr));

            var delivery = new DeliveryContent("Fix stuff.", List.of(), List.of());
            service.deliverFeedback(job, delivery, null, Set.of("practice"));

            verifyNoInteractions(commentPoster);
            verifyNoInteractions(accountPreferencesQuery);
            verify(feedbackLedgerRecorder).recordSuppressedUnit(
                eq(job),
                eq(delivery),
                eq(FeedbackSuppressionReason.ARTIFACT_GONE)
            );
        }

        @Test
        void skipsWhenWorkspaceNoLongerMonitorsRepository() {
            AgentJob job = createJob();
            stubOpenPr();
            when(
                repositoryToMonitorRepository.existsByWorkspaceIdAndNameWithOwner(WORKSPACE_ID, "owner/repo")
            ).thenReturn(false);

            var delivery = new DeliveryContent("Fix stuff.", List.of(), List.of());
            service.deliverFeedback(job, delivery, null, Set.of("practice"));

            verifyNoInteractions(commentPoster);
            verifyNoInteractions(accountPreferencesQuery);
            verify(feedbackLedgerRecorder).recordSuppressedUnit(
                eq(job),
                eq(delivery),
                eq(FeedbackSuppressionReason.ARTIFACT_GONE)
            );
        }

        @Test
        void preferenceLookupFailureFailsJobWithoutConversationalEscalation() {
            AgentJob job = createJob();
            stubOpenPr();
            when(accountPreferencesQuery.preferencesForUserId(AUTHOR_ID)).thenThrow(
                new IllegalStateException("db down")
            );

            var delivery = new DeliveryContent("Fix stuff.", List.of(), List.of());

            assertThatThrownBy(() -> service.deliverFeedback(job, delivery, null, Set.of("practice")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("db down");
            verifyNoInteractions(commentPoster);
            verify(feedbackLedgerRecorder, never()).recordUndelivered(any(), any());
        }

        @Test
        void workspacePolicyLookupFailureFailsJobWithoutConversationalEscalation() {
            AgentJob job = createJob();
            stubOpenPr();
            when(
                repositoryToMonitorRepository.existsByWorkspaceIdAndNameWithOwner(WORKSPACE_ID, "owner/repo")
            ).thenThrow(new IllegalStateException("db down"));

            var delivery = new DeliveryContent("Fix stuff.", List.of(), List.of());

            assertThatThrownBy(() -> service.deliverFeedback(job, delivery, null, Set.of("practice")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("db down");
            verifyNoInteractions(commentPoster);
            verify(feedbackLedgerRecorder, never()).recordUndelivered(any(), any());
        }

        @Test
        void suspendedWorkspaceRecordsSuppressedDelivery() {
            AgentJob job = createJob();
            Workspace workspace = activePracticeWorkspace();
            workspace.setStatus(Workspace.WorkspaceStatus.SUSPENDED);
            when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace));

            var delivery = new DeliveryContent("Fix stuff.", List.of(), List.of());
            service.deliverFeedback(job, delivery, null, Set.of("practice"));

            verifyNoInteractions(pullRequestRepository, commentPoster, accountPreferencesQuery);
            verify(feedbackLedgerRecorder).recordSuppressedUnit(
                job,
                delivery,
                FeedbackSuppressionReason.WORKSPACE_DISABLED
            );
        }

        @Test
        void skipsWhenPrNotFound() {
            AgentJob job = createJob();
            when(pullRequestRepository.findByIdWithAuthorAndRepository(PULL_REQUEST_ID)).thenReturn(Optional.empty());

            var delivery = new DeliveryContent("Fix stuff.", List.of(), List.of());
            service.deliverFeedback(job, delivery, null, Set.of("practice"));

            verifyNoInteractions(commentPoster);
            verify(feedbackLedgerRecorder).recordSuppressedUnit(
                eq(job),
                eq(delivery),
                eq(FeedbackSuppressionReason.ARTIFACT_GONE)
            );
        }

        @Test
        void blankAfterSanitize_noInlineLanded_recordsEmptyAfterSanitize() {
            AgentJob job = createJob();
            stubOpenPr();

            var delivery = new DeliveryContent("", List.of(), List.of());
            service.deliverFeedback(job, delivery, null, Set.of("practice"));

            verify(commentPoster, never()).postFormattedBody(any(), any());
            verify(feedbackLedgerRecorder).recordSuppressedUnit(
                eq(job),
                eq(delivery),
                eq(FeedbackSuppressionReason.EMPTY_AFTER_SANITIZE)
            );
            verify(feedbackLedgerRecorder, never()).record(
                any(),
                any(),
                any(),
                any(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean()
            );
        }

        @Test
        void blankAfterSanitize_inlineLanded_recordsDeliveredWithoutSummary() {
            AgentJob job = createJob();
            stubOpenPr();
            var note = new DiffNote("src/Foo.java", 10, null, "Fix this", "ck-foo");
            var signal = new InlineFeedbackChannel.DeliveredSignal(
                "ck-foo",
                new FeedbackAnchor.DiffAnchor("src/Foo.java", 10, null),
                InlineFeedbackChannel.Disposition.POSTED,
                "note-1",
                "disc-1"
            );
            when(diffNotePoster.reconcileInlineNotes(any(), any())).thenReturn(
                new DiffNotePoster.DiffNoteResult(1, 0, List.of(signal))
            );

            var delivery = new DeliveryContent("", List.of(note), List.of());
            service.deliverFeedback(job, delivery, null, Set.of("practice"));

            verify(feedbackLedgerRecorder).record(
                eq(job),
                eq(delivery),
                eq(ArtifactKinds.PULL_REQUEST),
                eq(List.of(signal)),
                eq(false),
                eq(true)
            );
            verify(feedbackLedgerRecorder, never()).recordSuppressedUnit(any(), any(), any());
        }

        @Test
        void throwsWhenSummaryPostReturnsNoId() {
            AgentJob job = createJob();
            stubOpenPr();
            when(commentPoster.postFormattedBody(any(), any())).thenReturn(null);

            var delivery = new DeliveryContent("A real, non-blank summary body.", List.of(), List.of());

            assertThatThrownBy(() -> service.deliverFeedback(job, delivery, null, Set.of("practice"))).isInstanceOf(
                de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException.class
            );
            assertThat(job.getDeliveryCommentId()).isNull();
            verify(feedbackLedgerRecorder).recordUndelivered(eq(job), eq(delivery));
        }

        @Test
        void summaryLandedThenInlineFailed_doesNotRecordFalseUndelivered() {
            AgentJob job = createJob();
            stubOpenPr();
            when(commentPoster.postFormattedBody(any(), any())).thenReturn("IC_summary_1");
            when(diffNotePoster.reconcileInlineNotes(eq(job), any())).thenThrow(
                new de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException("no inline channel wired")
            );

            var delivery = new DeliveryContent(
                "Summary.",
                List.of(new DiffNote("src/Foo.java", 3, null, "x")),
                List.of()
            );
            assertThatThrownBy(() -> service.deliverFeedback(job, delivery, null, Set.of("practice"))).isInstanceOf(
                de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException.class
            );
            assertThat(job.getDeliveryCommentId()).isEqualTo("IC_summary_1");
            verify(feedbackLedgerRecorder, never()).recordUndelivered(any(), any());
        }

        @Test
        void entityStateUnchangedAfterFailure() {
            AgentJob job = createJob();
            stubOpenPr();
            when(commentPoster.postFormattedBody(any(), any())).thenThrow(new RuntimeException("GraphQL timeout"));

            var delivery = new DeliveryContent("Summary.", List.of(), List.of());
            assertThatThrownBy(() -> service.deliverFeedback(job, delivery, null, Set.of("practice"))).isInstanceOf(
                de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException.class
            );

            assertThat(job.getDeliveryCommentId()).isNull();
            assertThat(job.getDeliveryStatus()).isNull();
        }

        @Test
        void postsDiffNotesWhenMrNoteNull() {
            AgentJob job = createJob();
            stubOpenPr();
            var firstSignal = new InlineFeedbackChannel.DeliveredSignal(
                "ck-foo",
                new FeedbackAnchor.DiffAnchor("src/Foo.java", 10, null),
                InlineFeedbackChannel.Disposition.POSTED,
                "note-1",
                "disc-1"
            );
            var secondSignal = new InlineFeedbackChannel.DeliveredSignal(
                "ck-bar",
                new FeedbackAnchor.DiffAnchor("src/Bar.java", 20, null),
                InlineFeedbackChannel.Disposition.POSTED,
                "note-2",
                "disc-2"
            );
            when(diffNotePoster.reconcileInlineNotes(eq(job), any())).thenReturn(
                new DiffNotePoster.DiffNoteResult(2, 0, List.of(firstSignal, secondSignal))
            );

            var diffNotes = List.of(
                new DiffNote("src/Foo.java", 10, null, "Fix this", "ck-foo"),
                new DiffNote("src/Bar.java", 20, null, "And this", "ck-bar")
            );
            var delivery = new DeliveryContent(null, diffNotes, List.of());
            service.deliverFeedback(job, delivery, null, Set.of("practice"));

            verify(diffNotePoster).reconcileInlineNotes(eq(job), eq(diffNotes));
            verify(feedbackLedgerRecorder).record(
                eq(job),
                eq(delivery),
                eq(ArtifactKinds.PULL_REQUEST),
                eq(List.of(firstSignal, secondSignal)),
                eq(false),
                eq(true)
            );
            verify(feedbackLedgerRecorder, never()).recordSuppressedUnit(any(), any(), any());
        }

        @Test
        void nullSummaryWithoutInlineDeliveryIsNotRecordedAsDelivered() {
            AgentJob job = createJob();
            stubOpenPr();
            DeliveryContent delivery = new DeliveryContent(null, List.of(), List.of());

            service.deliverFeedback(job, delivery, null, Set.of("practice"));

            verify(feedbackLedgerRecorder, never()).recordSuppressedUnit(any(), any(), any());
            verify(feedbackLedgerRecorder, never()).record(any(), any(), any(), any(), anyBoolean(), anyBoolean());
        }

        @Test
        void emptyDiffNotesStillReconcilesToClearStaleNotesOnOpenPr() {
            AgentJob job = createJob();
            stubOpenPr();
            when(commentPoster.postFormattedBody(any(), any())).thenReturn("IC_comment789");

            service.deliverFeedback(
                job,
                new DeliveryContent("Summary only, nothing inline.", List.of(), List.of()),
                null,
                Set.of("practice")
            );

            verify(diffNotePoster).reconcileInlineNotes(eq(job), eq(List.of()));
        }

        @Test
        void suppressedClosedPrNeverReconcilesSoARerunCannotWipeTheDeliveredReview() {
            AgentJob job = createJob();
            var pr = createOpenPr();
            pr.setState(Issue.State.CLOSED);
            when(pullRequestRepository.findByIdWithAuthorAndRepository(PULL_REQUEST_ID)).thenReturn(Optional.of(pr));

            service.deliverFeedback(
                job,
                new DeliveryContent("Summary.", List.of(), List.of()),
                null,
                Set.of("practice")
            );

            verify(diffNotePoster, never()).reconcileInlineNotes(any(), any());
        }

        @Test
        void skipsWhenMetadataNull() {
            AgentJob job = createJob();
            job.setMetadata(null);

            var delivery = new DeliveryContent("Fix stuff.", List.of(), List.of());
            service.deliverFeedback(job, delivery, null, Set.of("practice"));

            verifyNoInteractions(commentPoster);
        }

        @Test
        void skipsWhenPullRequestIdMissing() {
            AgentJob job = createJob();
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("repository_full_name", "owner/repo");
            metadata.put("pr_number", 42);
            job.setMetadata(metadata);

            var delivery = new DeliveryContent("Fix stuff.", List.of(), List.of());
            service.deliverFeedback(job, delivery, null, Set.of("practice"));

            verifyNoInteractions(commentPoster);
            verifyNoInteractions(pullRequestRepository);
        }
    }

    @Nested
    class SummaryDemotion {

        private InlineFeedbackChannel.DeliveredSignal landedSignal(String findingFingerprint) {
            return new InlineFeedbackChannel.DeliveredSignal(
                findingFingerprint,
                new FeedbackAnchor.DiffAnchor("src/Foo.java", 10, null),
                InlineFeedbackChannel.Disposition.POSTED,
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
            when(commentPoster.updateFormattedBody(eq(job), eq("IC_summary"), any(String.class))).thenReturn(
                new PullRequestCommentPoster.UpdateResult(
                    PullRequestCommentPoster.UpdateResult.Kind.EDITED,
                    "IC_summary"
                )
            );

            var delivery = new DeliveryContent(
                "Full-line summary.",
                List.of(new DiffNote("src/Foo.java", 10, null, "x")),
                List.of()
            );
            service.deliverFeedback(
                job,
                delivery,
                deliveredKeys -> {
                    assertThat(deliveredKeys).containsExactly("corr-1");
                    return "Demoted summary body.";
                },
                Set.of("practice")
            );

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
            boolean[] recomposed = { false };

            service.deliverFeedback(
                job,
                new DeliveryContent("Full-line summary.", List.of(), List.of()),
                keys -> {
                    recomposed[0] = true;
                    return "should-not-be-used";
                },
                Set.of("practice")
            );

            assertThat(recomposed[0]).isFalse();
            verify(commentPoster, never()).updateFormattedBody(eq(job), any(String.class), any(String.class));
        }

        @Test
        @DisplayName("a FAILED inline signal contributes no delivered key — its summary line is never demoted")
        void failedSignalDoesNotDemote() {
            AgentJob job = createJob();
            stubOpenPr();
            when(commentPoster.postFormattedBody(eq(job), any(String.class))).thenReturn("IC_summary");
            var failed = new InlineFeedbackChannel.DeliveredSignal(
                "corr-failed",
                new FeedbackAnchor.DiffAnchor("src/Foo.java", 10, null),
                InlineFeedbackChannel.Disposition.FAILED,
                null,
                null
            );
            when(diffNotePoster.reconcileInlineNotes(eq(job), any())).thenReturn(
                new DiffNotePoster.DiffNoteResult(0, 1, List.of(failed))
            );

            service.deliverFeedback(
                job,
                new DeliveryContent("Full-line summary.", List.of(), List.of()),
                keys -> "demoted",
                Set.of("practice")
            );

            verify(commentPoster, never()).updateFormattedBody(eq(job), any(String.class), any(String.class));
        }
    }

    private FeedbackDeliveryService serviceWithProgressFooter() {
        var props = reviewProperties(true);
        return new FeedbackDeliveryService(
            commentPoster,
            diffNotePoster,
            new PracticeFeedbackDeliveryPolicy(
                issueRepository,
                pullRequestRepository,
                repositoryToMonitorRepository,
                workspaceRepository,
                accountPreferencesQuery,
                props,
                () -> silentModeEngaged,
                coverageService,
                evaluationRecorder,
                practiceRepository
            ),
            props,
            feedbackLedgerRecorder,
            observationTrendService,
            commentFormatter,
            dispatchService
        );
    }

    private void stubSummaryDispatch() {
        org.mockito.Mockito.lenient()
            .when(dispatchService.dispatchAutomaticSummary(any(), any(String.class), nullable(String.class), any()))
            .thenAnswer(invocation -> {
                AgentJob job = invocation.getArgument(0);
                String body = invocation.getArgument(1);
                String prior = invocation.getArgument(2);
                try {
                    if (prior != null) {
                        PullRequestCommentPoster.UpdateResult update = commentPoster.updateFormattedBody(
                            job,
                            prior,
                            body
                        );
                        if (update.kind() == PullRequestCommentPoster.UpdateResult.Kind.EDITED) {
                            return PracticeFeedbackDispatchService.Result.sent(update.externalId());
                        }
                        if (update.kind() == PullRequestCommentPoster.UpdateResult.Kind.TRANSIENT) {
                            return PracticeFeedbackDispatchService.Result.uncertain();
                        }
                    }
                    String externalRef = commentPoster.postFormattedBody(job, body);
                    return externalRef == null
                        ? PracticeFeedbackDispatchService.Result.uncertain()
                        : PracticeFeedbackDispatchService.Result.sent(externalRef);
                } catch (JobDeliverySuppressedException exception) {
                    return PracticeFeedbackDispatchService.Result.suppressed(
                        FeedbackSuppressionReason.INSTANCE_SILENCED
                    );
                } catch (RuntimeException exception) {
                    return PracticeFeedbackDispatchService.Result.uncertain();
                }
            });
    }

    private static PracticeReviewProperties reviewProperties(boolean progressFooter) {
        return new PracticeReviewProperties(false, 15, 5, progressFooter, false);
    }

    private static TrendDelta resolvedTrend() {
        var resolved = new TrendDelta.LocusTransition(
            "k1",
            TrendDelta.TransitionStatus.RESOLVED,
            "code-hygiene",
            "Unused import removed",
            Assessment.BAD, // priorAssessment — the gap the student last saw (RESOLVED ⇒ currentAssessment null)
            null,
            Severity.MINOR
        );
        return new TrendDelta(
            ArtifactKinds.PULL_REQUEST,
            PULL_REQUEST_ID,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.parse("2026-06-15T10:00:00Z"),
            Instant.parse("2026-06-14T10:00:00Z"),
            List.of(resolved)
        );
    }
}
