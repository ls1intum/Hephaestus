package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.practices.feedback.decision.FeedbackDecision;
import de.tum.cit.aet.hephaestus.practices.feedback.decision.FeedbackDecisionRepository;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDecisionAction;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackVisibilityService {

    private final FeedbackDecisionRepository decisions;

    public boolean withdrawn(long workspaceId, UUID feedbackId) {
        return decisions
                .findLatestVisibility(workspaceId, feedbackId)
                .map(decision -> decision.getAction() == FeedbackDecisionAction.WITHDRAW)
                .orElse(false);
    }

    public Map<UUID, Instant> withdrawnAt(long workspaceId, Collection<UUID> feedbackIds) {
        if (feedbackIds.isEmpty()) {
            return Map.of();
        }
        return decisions.findCurrentVisibilityForFeedbackIds(workspaceId, feedbackIds).stream()
                .filter(row -> row.getAction() == FeedbackDecisionAction.WITHDRAW)
                .collect(Collectors.toMap(FeedbackDecision::getFeedbackId, FeedbackDecision::getDecidedAt));
    }
}
