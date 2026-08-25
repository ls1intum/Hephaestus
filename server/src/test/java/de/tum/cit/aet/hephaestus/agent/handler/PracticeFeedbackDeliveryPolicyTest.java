package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountPreferencesQuery;
import de.tum.cit.aet.hephaestus.core.settings.spi.SilentModeQuery;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyEvaluationCommand;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyEvaluationRecorder;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyStage;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicySurface;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewCoverageService;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeDeliveryStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

class PracticeFeedbackDeliveryPolicyTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 3L;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private SilentModeQuery silentModeQuery;

    @Mock
    private DeliveryPolicyEvaluationRecorder evaluationRecorder;

    @Test
    void approvedFeedbackKeepsHumanAuthorizationAtPhysicalEgress() {
        assertThat(
            PracticeFeedbackDeliveryPolicy.requiredAutonomy(DeliveryPolicyStage.EGRESS, UUID.randomUUID())
        ).isEqualTo(PracticeAutonomy.HUMAN_APPROVAL);
    }

    @Test
    void automaticFeedbackRequiresAutomaticAuthorityAtPhysicalEgress() {
        assertThat(PracticeFeedbackDeliveryPolicy.requiredAutonomy(DeliveryPolicyStage.EGRESS, null)).isEqualTo(
            PracticeAutonomy.AUTOMATIC
        );
    }

    @Test
    void compositionIsAllowedBeforeAnyPracticeSetIsKnown() {
        AgentJob job = conversationJob();

        assertThat(policy().allowsComposition(job, DeliveryPolicySurface.IN_APP)).isTrue();
        assertThat(policy().allowsComposition(job, DeliveryPolicySurface.CONVERSATION)).isTrue();
    }

    @Test
    void silentModeStopsWhatLeavesTheInstanceAndLeavesTheDevelopersOwnPageAlone() {
        AgentJob job = conversationJob();
        when(silentModeQuery.isSilentModeEngaged()).thenReturn(true);

        assertThat(policy().allowsComposition(job, DeliveryPolicySurface.CONVERSATION)).isFalse();
        assertThat(policy().allowsComposition(job, DeliveryPolicySurface.IN_APP)).isTrue();
    }

    @Test
    void pausingSendingLeavesTheDevelopersOwnPageReadable() {
        AgentJob job = conversationJob();
        job.getWorkspace().getReviewSettings().setDeliveryStatus(PracticeDeliveryStatus.PAUSED);
        job.setPracticeRolloutRevision(job.getWorkspace().getReviewSettings().getRolloutRevision());

        assertThat(policy().allowsComposition(job, DeliveryPolicySurface.CONVERSATION)).isFalse();
        assertThat(policy().allowsComposition(job, DeliveryPolicySurface.IN_APP)).isTrue();
    }

    @Test
    void artifactCompositionMustUseTheTypedEntryPoint() {
        assertThat(
            org.assertj.core.api.Assertions.catchThrowable(() ->
                policy().allowsComposition(conversationJob(), DeliveryPolicySurface.ARTIFACT)
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    /** The resolver owns the check order; this class owns which fact lands in which slot. */
    @Test
    void aPausedWorkspaceIsRefusedForThePauseAndNotForTheRevision() {
        AgentJob job = conversationJob();
        job.getWorkspace().getReviewSettings().setDeliveryStatus(PracticeDeliveryStatus.PAUSED);
        job.setPracticeRolloutRevision(job.getWorkspace().getReviewSettings().getRolloutRevision());

        assertThat(policy().allowsComposition(job, DeliveryPolicySurface.CONVERSATION)).isFalse();
        assertThat(recordedRefusal()).isEqualTo(FeedbackSuppressionReason.WORKSPACE_DELIVERY_PAUSED);
    }

    @Test
    void aJobFromAnOlderRolloutIsRefusedForTheRevisionAndNotForThePause() {
        AgentJob job = conversationJob();
        job.getWorkspace().getReviewSettings().setDeliveryStatus(PracticeDeliveryStatus.ACTIVE);
        job.setPracticeRolloutRevision(job.getWorkspace().getReviewSettings().getRolloutRevision() - 1);

        assertThat(policy().allowsComposition(job, DeliveryPolicySurface.CONVERSATION)).isFalse();
        assertThat(recordedRefusal()).isEqualTo(FeedbackSuppressionReason.STALE_ROLLOUT_REVISION);
    }

    private FeedbackSuppressionReason recordedRefusal() {
        ArgumentCaptor<DeliveryPolicyEvaluationCommand> recorded = ArgumentCaptor.forClass(
            DeliveryPolicyEvaluationCommand.class
        );
        org.mockito.Mockito.verify(evaluationRecorder).record(recorded.capture());
        return recorded.getValue().result().refusal();
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
        return job;
    }

    private PracticeFeedbackDeliveryPolicy policy() {
        return new PracticeFeedbackDeliveryPolicy(
            mock(IssueRepository.class),
            mock(PullRequestRepository.class),
            mock(RepositoryToMonitorRepository.class),
            workspaceRepository,
            mock(AccountPreferencesQuery.class),
            mock(PracticeReviewProperties.class),
            silentModeQuery,
            mock(PracticeReviewCoverageService.class),
            evaluationRecorder,
            mock(PracticeRepository.class)
        );
    }
}
