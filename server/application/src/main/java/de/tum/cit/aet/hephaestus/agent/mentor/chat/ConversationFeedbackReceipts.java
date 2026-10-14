package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.spi.ConversationCopyOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ConversationCopyReceipt;
import de.tum.cit.aet.hephaestus.practices.spi.CopyWriteFence;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStore;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Connects a prepared conversation copy to its actual mentor transport receipt. */
@Service
@RequiredArgsConstructor
@ConditionalOnCaptureRole
public class ConversationFeedbackReceipts {
    private final FeedbackStore feedback;

    public Optional<CopyWriteFence> claim(long workspaceId, UUID feedbackId, UUID chatMessageId) {
        return feedback.claimConversationCopy(workspaceId, feedbackId, chatMessageId);
    }

    public void record(long workspaceId, CopyWriteFence fence, MentorTurnReceipt receipt, Instant settledAt) {
        feedback.recordConversationReceipt(workspaceId, fence, from(receipt, settledAt));
    }

    public static ConversationCopyReceipt from(MentorTurnReceipt receipt, Instant settledAt) {
        if (receipt.unconfirmed()) {
            return new ConversationCopyReceipt(
                    receipt.chatMessageId(),
                    ConversationCopyOutcome.UNCONFIRMED,
                    settledAt,
                    "The mentor reply's delivery could not be confirmed.");
        }
        if (receipt.confirmedAt() != null) {
            return new ConversationCopyReceipt(
                    receipt.chatMessageId(), ConversationCopyOutcome.DELIVERED, receipt.confirmedAt(), null);
        }
        return new ConversationCopyReceipt(
                receipt.chatMessageId(),
                ConversationCopyOutcome.FAILED,
                settledAt,
                "The mentor reply was not delivered.");
    }
}
