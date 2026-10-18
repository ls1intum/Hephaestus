package de.tum.cit.aet.hephaestus.practices.review.feedbackmode;

import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackModeQuery;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackModeQueryService implements FeedbackModeQuery {
    private final PracticeRepository practices;
    private final WorkspaceReviewDefaultsProvider defaults;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, FeedbackMode> effectiveModes(long workspaceId, Collection<Long> practiceIds) {
        if (practiceIds.isEmpty()) return Map.of();
        var workspace = defaults.rawDefaultFeedbackMode(workspaceId);
        return practices.findByWorkspaceIdAndIdIn(workspaceId, practiceIds).stream()
                .collect(Collectors.toUnmodifiableMap(
                        Practice::getId, practice -> FeedbackModeResolver.effectiveModeOf(practice, workspace)));
    }
}
