package de.tum.cit.aet.hephaestus.practices.feedback.approval;

import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.practices.review.autonomy.AutonomyResolver;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeedbackApprovalEligibility {

    private final PracticeRepository practiceRepository;
    private final WorkspaceReviewDefaultsProvider defaultsProvider;

    public boolean isEligible(Long workspaceId, UUID feedbackId) {
        var practices = practiceRepository.findContributingPractices(workspaceId, feedbackId);
        if (practices.isEmpty()) return false;
        PracticeAutonomy workspaceDefault =
                defaultsProvider.forWorkspace(workspaceId).defaultAutonomy();
        return practices.stream()
                .allMatch(practice -> AutonomyResolver.effectiveAutonomyOf(practice, workspaceDefault)
                        == PracticeAutonomy.HUMAN_APPROVAL);
    }
}
