package de.tum.cit.aet.hephaestus.practices.review.feedbackmode;

import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.dto.FeedbackModeRollupDTO;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackModeRollupService {
    private final PracticeRepository practices;
    private final WorkspaceReviewDefaultsProvider defaults;

    @Transactional(readOnly = true)
    public FeedbackModeRollupDTO rollup(Long workspaceId) {
        FeedbackMode workspace = defaults.rawDefaultFeedbackMode(workspaceId);
        int off = 0;
        int approval = 0;
        int automatic = 0;
        for (var practice : practices.findByWorkspaceId(workspaceId)) {
            switch (FeedbackModeResolver.effectiveModeOf(practice, workspace)) {
                case OFF -> off++;
                case APPROVE_FIRST -> approval++;
                case AUTOMATIC -> automatic++;
            }
        }
        return new FeedbackModeRollupDTO(
                off, approval, automatic, workspace == null ? FeedbackMode.APPROVE_FIRST : workspace);
    }
}
