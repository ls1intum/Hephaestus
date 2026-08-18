package de.tum.cit.aet.hephaestus.agent.conversation;

import java.util.Collection;
import java.util.Set;

/** Fail-closed source checks for conversation-derived agent data. */
public interface ConversationSourceLiveness {
    Set<Long> activeThreadIds(long workspaceId, Collection<Long> threadIds);

    boolean isDeliverableThread(
        long workspaceId,
        long threadId,
        String channelId,
        String threadTimestamp,
        long participantId
    );
}
