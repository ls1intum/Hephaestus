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

    Optional<CopyWriteFence> claimConversationCopy(long workspaceId, UUID feedbackId, UUID chatMessageId);

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
