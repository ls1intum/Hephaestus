package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.Collection;
import java.util.Set;

public interface ConversationSourceLiveness {
    Set<Long> activeThreadIds(long workspaceId, Collection<Long> threadIds);

    boolean isDeliverableThread(
            long workspaceId, long threadId, String channelId, String threadTimestamp, long participantId);
}
