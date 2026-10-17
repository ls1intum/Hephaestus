package de.tum.cit.aet.hephaestus.integration.chat.item;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record ConversationThreadCandidate(
        long workspaceId,
        long threadId,
        String channelId,
        @Nullable String channelName,
        String threadTs,
        String lastTs,
        @Nullable String lastReviewedTs,
        List<Long> participantAccountIds) {
    public ConversationThreadCandidate {
        participantAccountIds = List.copyOf(participantAccountIds);
    }
}
