package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountPreferencesQuery;
import de.tum.cit.aet.hephaestus.core.settings.spi.SilentModeQuery;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ReviewSubject;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReview;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReviewRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyEvaluationCommand;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyEvaluationRecorder;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyStage;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicySurface;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackApproval;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackApprovalDecision;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackApprovalRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewCoverageService;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.practices.review.ReviewSubjectStatus;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeDeliveryStatus;
import de.tum.cit.aet.hephaestus.workspace.settings.ReviewPersonMode;
import de.tum.cit.aet.hephaestus.workspace.settings.ReviewRepositoryMode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

class PracticeFeedbackDeliveryPolicyTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 3L;
    private static final long PULL_REQUEST_ID = 41L;
    private static final long REPOSITORY_ID = 42L;
    private static final long AUTHOR_ID = 43L;
    private static final long REVIEWER_ID = 44L;
    private static final long REVIEW_ID = 45L;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private SilentModeQuery silentModeQuery;

    @Mock
    private DeliveryPolicyEvaluationRecorder evaluationRecorder;

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private PullRequestReviewRepository pullRequestReviewRepository;

    @Mock
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Mock
    private AccountPreferencesQuery accountPreferencesQuery;

    @Mock
    private PracticeReviewCoverageService coverageService;

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private FeedbackApprovalRepository approvalRepository;

    @Test
    void compositionIsAllowedBeforeAnyPracticeSetIsKnown() {
        AgentJob job = conversationJob();

        assertThat(policy().allowsComposition(job, DeliveryPolicySurface.IN_APP))
                .isTrue();
        assertThat(policy().allowsComposition(job, DeliveryPolicySurface.CONVERSATION))
                .isTrue();
    }

    @Test
    void repositorylessCompositionStillRequiresCurrentCoverageAndConsent() {
        AgentJob job = conversationJob();
        when(coverageService.assessRepositoryless(any(), any())).thenReturn(coverage(false));

        assertThat(policy().allowsComposition(job, DeliveryPolicySurface.CONVERSATION))
                .isFalse();
        assertThat(recordedRefusal()).isEqualTo(FeedbackSuppressionReason.OUTSIDE_CURRENT_COVERAGE);
    }

    @Test
    void repositorylessCompositionHonorsTheRecipientsCurrentPreference() {
        AgentJob job = conversationJob();
        when(accountPreferencesQuery.practiceFeedbackDeliveryEnabled(AUTHOR_ID)).thenReturn(false);

        assertThat(policy().allowsComposition(job, DeliveryPolicySurface.CONVERSATION))
                .isFalse();
        assertThat(recordedRefusal()).isEqualTo(FeedbackSuppressionReason.RECIPIENT_OPTED_OUT);
    }

    @Test
    void silentModeStopsWhatLeavesTheInstanceAndLeavesTheDevelopersOwnPageAlone() {
        AgentJob job = conversationJob();
        when(silentModeQuery.isSilentModeEngaged()).thenReturn(true);

        assertThat(policy().allowsComposition(job, DeliveryPolicySurface.CONVERSATION))
                .isFalse();
        assertThat(policy().allowsComposition(job, DeliveryPolicySurface.IN_APP))
                .isTrue();
    }

    @Test
    void shouldStopConversationWithPauseReasonButKeepInAppReadableWhenSendingIsPaused() {
        AgentJob job = conversationJob();
        job.getWorkspace().getReviewSettings().setDeliveryStatus(PracticeDeliveryStatus.PAUSED);
        job.setPracticeRolloutRevision(job.getWorkspace().getReviewSettings().getRolloutRevision());

        assertThat(policy().allowsComposition(job, DeliveryPolicySurface.CONVERSATION))
                .isFalse();
        assertThat(recordedRefusal()).isEqualTo(FeedbackSuppressionReason.WORKSPACE_DELIVERY_PAUSED);
        assertThat(policy().allowsComposition(job, DeliveryPolicySurface.IN_APP))
                .isTrue();
    }

    @Test
    void artifactCompositionMustUseTheTypedEntryPoint() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                        () -> policy().allowsComposition(conversationJob(), DeliveryPolicySurface.ARTIFACT)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aJobFromAnOlderRolloutIsRefusedForTheRevisionAndNotForThePause() {
        AgentJob job = conversationJob();
        job.getWorkspace().getReviewSettings().setDeliveryStatus(PracticeDeliveryStatus.ACTIVE);
        job.setPracticeRolloutRevision(job.getWorkspace().getReviewSettings().getRolloutRevision() - 1);

        assertThat(policy().allowsComposition(job, DeliveryPolicySurface.CONVERSATION))
                .isFalse();
        assertThat(recordedRefusal()).isEqualTo(FeedbackSuppressionReason.STALE_ROLLOUT_REVISION);
    }

    @Test
    void explicitApprovalMayReleaseAnOlderProposalAfterResume() {
        AgentJob job = pullRequestJob();
        job.setPracticeRolloutRevision(job.getWorkspace().getReviewSettings().getRolloutRevision() - 1);
        PullRequest pullRequest = openPullRequest();
        stubPullRequestEvaluation(pullRequest, coverage(true));
        when(accountPreferencesQuery.practiceFeedbackDeliveryEnabled(AUTHOR_ID)).thenReturn(true);
        UUID feedbackId = UUID.randomUUID();
        Practice practice = new Practice();
        practice.setSlug("review-quality");
        practice.setAutonomy(PracticeAutonomy.HUMAN_APPROVAL);
        when(practiceRepository.findByWorkspaceIdAndSlugIn(WORKSPACE_ID, java.util.Set.of("review-quality")))
                .thenReturn(java.util.List.of(practice));
        when(approvalRepository.findByFeedbackIdAndWorkspaceId(feedbackId, WORKSPACE_ID))
                .thenReturn(Optional.of(FeedbackApproval.builder()
                        .feedbackId(feedbackId)
                        .workspaceId(WORKSPACE_ID)
                        .decision(FeedbackApprovalDecision.APPROVED)
                        .build()));

        var decision = policy().evaluatePullRequest(
                        job, DeliveryPolicyStage.EGRESS, feedbackId, java.util.Set.of("review-quality"));

        assertThat(decision.allowed()).isTrue();
        assertThat(recordedEvaluation().result().checks()).anySatisfy(check -> {
            assertThat(check.check().name()).isEqualTo("ROLLOUT_REVISION");
            assertThat(check.status().name()).isEqualTo("NOT_APPLICABLE");
        });
    }

    @Test
    void repositorylessFeedbackIdentityDoesNotGrantHumanApproval() {
        AgentJob job = conversationJob();
        UUID feedbackId = UUID.randomUUID();
        Practice practice = new Practice();
        practice.setSlug("review-quality");
        practice.setAutonomy(PracticeAutonomy.HUMAN_APPROVAL);
        when(practiceRepository.findByWorkspaceIdAndSlugIn(WORKSPACE_ID, java.util.Set.of("review-quality")))
                .thenReturn(java.util.List.of(practice));

        var decision = policy().evaluateRepositoryless(
                        job,
                        DeliveryPolicyStage.EGRESS,
                        feedbackId,
                        DeliveryPolicySurface.CONVERSATION,
                        AUTHOR_ID,
                        java.util.Set.of("review-quality"));

        assertThat(decision.allowed()).isFalse();
        assertThat(recordedRefusal()).isEqualTo(FeedbackSuppressionReason.PRACTICE_REQUIRES_APPROVAL);
    }

    @Test
    void staleAutomaticRepositorylessFeedbackIsNotReleasedAsAnApproval() {
        AgentJob job = conversationJob();
        job.setPracticeRolloutRevision(job.getPracticeRolloutRevision() - 1);
        UUID feedbackId = UUID.randomUUID();
        Practice practice = new Practice();
        practice.setSlug("review-quality");
        practice.setAutonomy(PracticeAutonomy.AUTOMATIC);
        when(practiceRepository.findByWorkspaceIdAndSlugIn(WORKSPACE_ID, java.util.Set.of("review-quality")))
                .thenReturn(java.util.List.of(practice));

        var decision = policy().evaluateRepositoryless(
                        job,
                        DeliveryPolicyStage.EGRESS,
                        feedbackId,
                        DeliveryPolicySurface.CONVERSATION,
                        AUTHOR_ID,
                        java.util.Set.of("review-quality"));

        assertThat(decision.allowed()).isFalse();
        assertThat(recordedRefusal()).isEqualTo(FeedbackSuppressionReason.STALE_ROLLOUT_REVISION);
    }

    @Test
    void pullRequestMapsRecipientPreferenceIntoTheDecisiveConsentRefusal() {
        AgentJob job = pullRequestJob();
        PullRequest pullRequest = openPullRequest();
        stubPullRequestEvaluation(pullRequest, coverage(true));
        when(accountPreferencesQuery.practiceFeedbackDeliveryEnabled(AUTHOR_ID)).thenReturn(false);

        PracticeFeedbackDeliveryPolicy.Decision<PullRequest> decision = policy().evaluatePullRequest(job);

        assertThat(decision.allowed()).isFalse();
        DeliveryPolicyEvaluationCommand recorded = recordedEvaluation();
        assertThat(recorded.result().refusal()).isEqualTo(FeedbackSuppressionReason.RECIPIENT_OPTED_OUT);
        assertThat(recorded.facts().recipientConsent()).isFalse();
        assertThat(recorded.facts().subject()).isEqualTo(ReviewSubjectStatus.RESOLVED_LINKED_HUMAN);
        assertThat(recorded.facts().repository()).isEqualTo("owner/repo");
        assertThat(recorded.facts().baseBranch()).isEqualTo("main");
    }

    @Test
    void pullRequestMapsCurrentCoverageIntoTheDecisiveCoverageRefusal() {
        AgentJob job = pullRequestJob();
        PullRequest pullRequest = openPullRequest();
        stubPullRequestEvaluation(pullRequest, coverage(false));
        when(accountPreferencesQuery.practiceFeedbackDeliveryEnabled(AUTHOR_ID)).thenReturn(true);

        PracticeFeedbackDeliveryPolicy.Decision<PullRequest> decision = policy().evaluatePullRequest(job);

        assertThat(decision.allowed()).isFalse();
        DeliveryPolicyEvaluationCommand recorded = recordedEvaluation();
        assertThat(recorded.result().refusal()).isEqualTo(FeedbackSuppressionReason.OUTSIDE_CURRENT_COVERAGE);
        assertThat(recorded.facts().repositoryMatched()).isTrue();
        assertThat(recorded.facts().branchMatched()).isTrue();
        assertThat(recorded.facts().personMatched()).isFalse();
        assertThat(recorded.facts().recipientConsent()).isTrue();
    }

    @Test
    void reviewerFeedbackUsesTheReviewerForCoverageAndConsent() {
        AgentJob job = pullRequestJob();
        var metadata = org.junit.jupiter.api.Assertions.assertInstanceOf(
                tools.jackson.databind.node.ObjectNode.class, job.getMetadata());
        metadata.put("subject_role", "REVIEWER");
        metadata.put("review_id", REVIEW_ID);
        metadata.put("about_user_id", REVIEWER_ID);
        PullRequest pullRequest = openPullRequest();
        PullRequestReview review = new PullRequestReview();
        review.setId(REVIEW_ID);
        review.setPullRequest(pullRequest);
        User reviewer = new User();
        reviewer.setId(REVIEWER_ID);
        reviewer.setType(User.Type.USER);
        review.setAuthor(reviewer);
        when(pullRequestReviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
        stubPullRequestEvaluation(pullRequest, coverage(true));
        when(accountPreferencesQuery.practiceFeedbackDeliveryEnabled(REVIEWER_ID))
                .thenReturn(false);

        PracticeFeedbackDeliveryPolicy.Decision<PullRequest> decision = policy().evaluatePullRequest(job);

        assertThat(decision.allowed()).isFalse();
        assertThat(recordedRefusal()).isEqualTo(FeedbackSuppressionReason.RECIPIENT_OPTED_OUT);
        org.mockito.Mockito.verify(coverageService)
                .assess(any(), eq("owner/repo"), eq("main"), eq(new ReviewSubject(REVIEWER_ID, true)), eq(true));
    }

    private FeedbackSuppressionReason recordedRefusal() {
        return recordedEvaluation().result().refusal();
    }

    private DeliveryPolicyEvaluationCommand recordedEvaluation() {
        ArgumentCaptor<DeliveryPolicyEvaluationCommand> recorded =
                ArgumentCaptor.forClass(DeliveryPolicyEvaluationCommand.class);
        org.mockito.Mockito.verify(evaluationRecorder).record(recorded.capture());
        return recorded.getValue();
    }

    private AgentJob conversationJob() {
        Workspace workspace = WorkspaceTestFixtures.activeWorkspace("compose");
        workspace.setId(WORKSPACE_ID);
        workspace.getFeatures().setPracticesEnabled(true);
        lenient().when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace));

        AgentJob job = new AgentJob();
        job.setId(UUID.randomUUID());
        job.setWorkspace(workspace);
        job.setArtifactKind(ArtifactKind.of("chat.conversation_thread"));
        job.setPracticeRolloutRevision(workspace.getReviewSettings().getRolloutRevision());
        var metadata = tools.jackson.databind.json.JsonMapper.builder().build().createObjectNode();
        metadata.put("about_user_id", AUTHOR_ID);
        job.setMetadata(metadata);
        lenient().when(coverageService.assessRepositoryless(any(), any())).thenReturn(coverage(true));
        lenient()
                .when(accountPreferencesQuery.practiceFeedbackDeliveryEnabled(AUTHOR_ID))
                .thenReturn(true);
        return job;
    }

    private AgentJob pullRequestJob() {
        AgentJob job = conversationJob();
        job.setArtifactKind(ArtifactKind.of("scm.pull_request"));
        var metadata = tools.jackson.databind.json.JsonMapper.builder().build().createObjectNode();
        metadata.put("pull_request_id", PULL_REQUEST_ID);
        metadata.put("repository_id", REPOSITORY_ID);
        metadata.put("repository_full_name", "owner/repo");
        metadata.put("pr_number", 17);
        job.setMetadata(metadata);
        return job;
    }

    private PullRequest openPullRequest() {
        PullRequest pullRequest = new PullRequest();
        pullRequest.setId(PULL_REQUEST_ID);
        pullRequest.setNumber(17);
        pullRequest.setState(Issue.State.OPEN);
        pullRequest.setBaseRefName("main");
        Repository repository = new Repository();
        repository.setId(REPOSITORY_ID);
        repository.setNameWithOwner("owner/repo");
        pullRequest.setRepository(repository);
        User author = new User();
        author.setId(AUTHOR_ID);
        author.setType(User.Type.USER);
        pullRequest.setAuthor(author);
        return pullRequest;
    }

    private void stubPullRequestEvaluation(
            PullRequest pullRequest, PracticeReviewCoverageService.CoverageAssessment coverage) {
        when(pullRequestRepository.findByIdWithAuthorAndRepository(PULL_REQUEST_ID))
                .thenReturn(Optional.of(pullRequest));
        when(repositoryToMonitorRepository.existsByWorkspaceIdAndNameWithOwner(WORKSPACE_ID, "owner/repo"))
                .thenReturn(true);
        when(coverageService.assess(any(), eq("owner/repo"), eq("main"), any(), eq(true)))
                .thenReturn(coverage);
    }

    private static PracticeReviewCoverageService.CoverageAssessment coverage(boolean admitted) {
        return new PracticeReviewCoverageService.CoverageAssessment(
                ReviewRepositoryMode.ALL_MONITORED,
                ReviewPersonMode.SELECTED,
                ReviewSubjectStatus.RESOLVED_LINKED_HUMAN,
                true,
                true,
                admitted,
                admitted);
    }

    private PracticeFeedbackDeliveryPolicy policy() {
        return new PracticeFeedbackDeliveryPolicy(
                issueRepository,
                pullRequestRepository,
                pullRequestReviewRepository,
                repositoryToMonitorRepository,
                workspaceRepository,
                accountPreferencesQuery,
                mock(PracticeReviewProperties.class),
                silentModeQuery,
                coverageService,
                evaluationRecorder,
                practiceRepository,
                approvalRepository);
    }
}
