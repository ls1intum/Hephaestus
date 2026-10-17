package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackSupersession {
    private final FeedbackRepository feedback;
    private final FeedbackCopyRepository copies;
    private final FeedbackVisibilityService visibility;
    private final FeedbackBasisRepository basis;
    private final ResultRepository results;
    private final ReviewRepository reviews;

    public boolean canReplaceConversation(long workspaceId, UUID previousId, long developerId, long practiceId) {
        var previous = feedback.lockByIdAndWorkspaceId(previousId, workspaceId).orElse(null);
        if (previous == null
                || previous.getDeveloperId() != developerId
                || previous.getDestination() != FeedbackDestination.CONVERSATION
                || previous.getState() != FeedbackState.READY
                || previous.getDeliveredAt() != null
                || visibility.withdrawn(workspaceId, previousId)
                || copies.hasWriteFence(workspaceId, previousId)) return false;
        var ids = basis.findByWorkspaceIdAndFeedbackIdOrderByOrdinalAscIdAsc(workspaceId, previousId).stream()
                .map(FeedbackBasis::getResultId)
                .distinct()
                .toList();
        var support = results.findByWorkspaceIdAndIdIn(workspaceId, ids);
        var primary = reviews.findByIdAndWorkspaceId(previous.getReviewId(), workspaceId)
                .orElseThrow()
                .getChecks()
                .path("feedbackComposition")
                .path(previousId.toString())
                .path("primaryResultId");
        if (primary.isString())
            return support.stream()
                    .anyMatch(result -> result.getId().toString().equals(primary.asString())
                            && result.getPracticeId() == practiceId);
        return !support.isEmpty() && support.stream().allMatch(result -> result.getPracticeId() == practiceId);
    }

    @Transactional
    public boolean replaceOpen(long workspaceId, UUID previousId, UUID nextId) {
        if (previousId.equals(nextId)) return false;
        var first = feedback.lockByIdAndWorkspaceId(
                        previousId.toString().compareTo(nextId.toString()) < 0 ? previousId : nextId, workspaceId)
                .orElseThrow();
        var second = feedback.lockByIdAndWorkspaceId(
                        previousId.toString().compareTo(nextId.toString()) < 0 ? nextId : previousId, workspaceId)
                .orElseThrow();
        var prior = first.getId().equals(previousId) ? first : second;
        var next = first.getId().equals(nextId) ? first : second;
        if (prior.getDeveloperId() != next.getDeveloperId()
                || prior.getDestination() != next.getDestination()
                || prior.getDestination() == FeedbackDestination.ON_WORK)
            throw new IllegalArgumentException("Replacement must address the same private destination and developer.");
        if (next.getState() != FeedbackState.READY && next.getState() != FeedbackState.DELIVERED) return false;
        if ((prior.getState() != FeedbackState.READY && prior.getState() != FeedbackState.DELIVERED)
                || visibility.withdrawn(workspaceId, previousId)
                || (prior.getDestination() == FeedbackDestination.CONVERSATION
                        && copies.hasWriteFence(workspaceId, previousId))) return false;
        feedback.recordReplacement(workspaceId, nextId, previousId);
        feedback.updateDelivery(workspaceId, previousId, FeedbackState.REPLACED, null, prior.getDeliveredAt());
        return true;
    }
}
