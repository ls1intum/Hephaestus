package de.tum.cit.aet.hephaestus.practices.feedback.approval;

import de.tum.cit.aet.hephaestus.core.settings.spi.SilentModeQuery;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.practices.review.autonomy.AutonomyResolver;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeDeliveryStatus;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackApprovalEligibility {

    private final PracticeRepository practiceRepository;
    private final WorkspaceReviewDefaultsProvider defaultsProvider;
    private final WorkspaceRepository workspaceRepository;
    private final SilentModeQuery silentModeQuery;

    @Transactional(readOnly = true)
    public boolean isEligible(Long workspaceId, UUID feedbackId) {
        var practices = practiceRepository.findContributingPractices(workspaceId, feedbackId);
        if (practices.isEmpty()) return false;
        PracticeAutonomy workspaceDefault = defaultsProvider.forWorkspace(workspaceId).defaultAutonomy();
        return practices
            .stream()
            .allMatch(
                practice ->
                    AutonomyResolver.effectiveAutonomyOf(practice, workspaceDefault) == PracticeAutonomy.HUMAN_APPROVAL
            );
    }

    /**
     * The brake that would refuse this workspace's feedback at egress right now, or null if none would.
     * Approving spends the proposal whatever happens next, so a brake the operator can lift has to refuse
     * the decision rather than let it succeed and be discarded on the way out.
     */
    @Transactional(readOnly = true)
    public @Nullable FeedbackSuppressionReason brakeOnDelivery(Long workspaceId) {
        if (silentModeQuery.isSilentModeEngaged()) {
            return FeedbackSuppressionReason.INSTANCE_SILENCED;
        }
        boolean paused = workspaceRepository
            .findById(workspaceId)
            .map(workspace -> workspace.getReviewSettings().getDeliveryStatus() == PracticeDeliveryStatus.PAUSED)
            .orElse(false);
        return paused ? FeedbackSuppressionReason.WORKSPACE_DELIVERY_PAUSED : null;
    }
}
