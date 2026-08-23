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
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyEvaluationRecorder;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyStage;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicySurface;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewCoverageService;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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

    /**
     * Composition runs before anything has decided which practices contribute, so the caller has no slug
     * set to offer. Reading that as a refusal closed the in-app and conversation lanes for every
     * workspace, and both listeners gate on this one method.
     */
    @Test
    void compositionIsAllowedBeforeAnyPracticeSetIsKnown() {
        AgentJob job = conversationJob();

        assertThat(policy().allowsComposition(job, DeliveryPolicySurface.IN_APP)).isTrue();
        assertThat(policy().allowsComposition(job, DeliveryPolicySurface.CONVERSATION)).isTrue();
    }

    /** The unaskable authority question is not a bypass: the instance-wide brake still stops it. */
    @Test
    void silentModeStillStopsComposition() {
        AgentJob job = conversationJob();
        when(silentModeQuery.isSilentModeEngaged()).thenReturn(true);

        assertThat(policy().allowsComposition(job, DeliveryPolicySurface.IN_APP)).isFalse();
    }

    @Test
    void artifactCompositionMustUseTheTypedEntryPoint() {
        assertThat(
            org.assertj.core.api.Assertions.catchThrowable(() ->
                policy().allowsComposition(conversationJob(), DeliveryPolicySurface.ARTIFACT)
            )
        ).isInstanceOf(IllegalArgumentException.class);
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
