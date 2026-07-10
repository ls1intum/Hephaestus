package de.tum.cit.aet.hephaestus.integration.slack.conversation;

import de.tum.cit.aet.hephaestus.agent.conversation.ConversationSourceLiveness;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Slack-owned implementation of the agent {@link ConversationSourceLiveness} SPI: resolves which
 * {@code CONVERSATION_THREAD}-derived rows may still surface, by joining {@code slack_thread} to
 * {@code slack_monitored_channel} and keeping only threads on an {@code ACTIVE} channel.
 *
 * <p><strong>Ownership.</strong> This lives in {@code integration.slack} because it reads Slack's own tables; the
 * agent {@code ConversationConsentGate} delegates here through the SPI, so the mentor derived-feedback content
 * sources never reach into the Slack schema.
 *
 * <p><strong>Persistence.</strong> Delegates to {@link SlackThreadRepository#findActiveThreadIds} — scoped JPQL,
 * no raw {@code JdbcTemplate}.
 */
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
}
