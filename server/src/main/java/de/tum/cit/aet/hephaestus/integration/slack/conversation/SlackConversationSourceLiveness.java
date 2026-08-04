package de.tum.cit.aet.hephaestus.integration.slack.conversation;

import de.tum.cit.aet.hephaestus.agent.conversation.ConversationSourceLiveness;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SlackConversationSourceLiveness implements ConversationSourceLiveness {

    private final SlackThreadRepository threadRepository;

    public SlackConversationSourceLiveness(SlackThreadRepository threadRepository) {
        this.threadRepository = threadRepository;
    }

    @Override
    public Set<Long> activeThreadIds(long workspaceId, Collection<Long> threadIds) {
        if (threadIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(threadRepository.findActiveThreadIds(workspaceId, threadIds));
    }

    @Override
    public boolean isDeliverableThread(
        long workspaceId,
        long threadId,
        String channelId,
        String threadTimestamp,
        long participantId
    ) {
        return threadRepository.existsDeliverableThread(
            threadId,
            workspaceId,
            channelId,
            threadTimestamp,
            participantId
        );
    }
}
