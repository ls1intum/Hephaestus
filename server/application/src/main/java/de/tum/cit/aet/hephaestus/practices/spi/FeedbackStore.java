package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface FeedbackStore {
    FeedbackCompositionContext compositionContext(long workspaceId, UUID reviewId, List<UUID> admittedResultIds);

    PrivateFeedbackProgress privateProgress(
            long workspaceId, UUID reviewId, long developerId, Collection<UUID> admittedResultIds);

    PreparedPackage prepareAll(long workspaceId, UUID reviewId, List<FeedbackDraft> drafts);

    /**
     * Claims the conversation copy of READY conversation feedback for the running attempt of a Heph turn of its
     * developer. A repeat by the same attempt returns the fence it already holds; an ended attempt claims nothing.
     */
    Optional<CopyWriteFence> claimConversationCopy(long workspaceId, UUID feedbackId, UUID mentorTurnId, int attempt);

    void recordConversationReceipt(long workspaceId, CopyWriteFence fence, ConversationCopyReceipt receipt);

    FeedbackDecisionOutcome decide(long workspaceId, UUID feedbackId, long actorId, FeedbackDecisionInput input);

    void retry(long workspaceId, UUID feedbackId, long actorId);

    void respond(
            long workspaceId,
            UUID feedbackId,
            long developerId,
            @Nullable FeedbackResolution resolution,
            @Nullable FeedbackUsefulness usefulness,
            @Nullable String comment);
}
