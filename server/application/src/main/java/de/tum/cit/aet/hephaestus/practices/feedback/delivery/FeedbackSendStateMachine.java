package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackCopy;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackCopyRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSendRepository;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackSendState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStopReason;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
public class FeedbackSendStateMachine {

    private final FeedbackRepository feedbackRepository;
    private final FeedbackSendRepository sends;
    private final FeedbackCopyRepository copies;
    private final Clock clock;

    public record Projection(
            FeedbackState state,
            @Nullable FeedbackStopReason stopReason,
            @Nullable Instant deliveredAt) {}

    public static Projection project(Feedback feedback, List<FeedbackCopy> copies) {
        Instant deliveredAt = feedback.getDeliveredAt();
        for (FeedbackCopy copy : copies) {
            Instant postedAt = copy.getPostedAt();
            if (postedAt != null && (deliveredAt == null || postedAt.isBefore(deliveredAt))) {
                deliveredAt = postedAt;
            }
        }
        if (copies.stream().anyMatch(copy -> copy.getState() == FeedbackCopyState.WRITE_STARTED)) {
            return new Projection(FeedbackState.UNCONFIRMED, FeedbackStopReason.DELIVERY_UNCONFIRMED, deliveredAt);
        }
        if (feedback.getState() == FeedbackState.REPLACED || feedback.getState() == FeedbackState.REJECTED) {
            return new Projection(feedback.getState(), feedback.getStopReason(), deliveredAt);
        }
        if (deliveredAt != null) {
            return new Projection(FeedbackState.DELIVERED, null, deliveredAt);
        }
        if (copies.stream().anyMatch(copy -> copy.getState() == FeedbackCopyState.FAILED)) {
            return new Projection(FeedbackState.FAILED, null, null);
        }
        if (!copies.isEmpty() && copies.stream().allMatch(copy -> copy.getState() == FeedbackCopyState.WITHHELD)) {
            @Nullable FeedbackStopReason reason = feedback.getStopReason();
            for (FeedbackCopy copy : copies) {
                if (copy.getStopReason() != null) {
                    reason = copy.getStopReason();
                    break;
                }
            }
            if (reason == null) {
                throw new IllegalArgumentException("Withheld feedback needs its recorded reason.");
            }
            return new Projection(FeedbackState.WITHHELD, reason, null);
        }
        return new Projection(feedback.getState(), feedback.getStopReason(), null);
    }

    @Transactional
    public boolean settle(
            long workspaceId,
            UUID feedbackId,
            String owner,
            FeedbackSendState target,
            Instant nextAttemptAt,
            @Nullable String error) {
        if (target == FeedbackSendState.SENDING) {
            throw new IllegalArgumentException("Settlement must release the send lease.");
        }
        Feedback feedback = feedbackRepository
                .lockByIdAndWorkspaceId(feedbackId, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("Feedback", feedbackId.toString()));
        var send = sends.lockByFeedbackId(workspaceId, feedbackId);
        Instant now = clock.instant();
        if (send.isEmpty()
                || send.get().getState() != FeedbackSendState.SENDING
                || !owner.equals(send.get().getLeaseOwner())) {
            return false;
        }
        var projected = project(feedback, copies.lockByFeedbackId(workspaceId, feedbackId));
        if (sends.settle(workspaceId, feedbackId, owner, target.name(), now, nextAttemptAt, bounded(error)) != 1) {
            return false;
        }
        feedbackRepository.updateDelivery(
                workspaceId, feedbackId, projected.state(), projected.stopReason(), projected.deliveredAt());
        return true;
    }

    private static @Nullable String bounded(@Nullable String error) {
        return error == null || error.length() <= 512 ? error : error.substring(0, 512);
    }
}
