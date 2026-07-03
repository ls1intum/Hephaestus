package de.tum.cit.aet.hephaestus.integration.slack.conversation;

import de.tum.cit.aet.hephaestus.agent.conversation.ConversationSourceLiveness;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Slack-owned implementation of the agent {@link ConversationSourceLiveness} SPI: resolves which
 * {@code CONVERSATION_THREAD}-derived rows may still surface, by joining {@code slack_thread} to
 * {@code slack_monitored_channel} and keeping only threads on an {@code ACTIVE} channel.
 *
 * <p><strong>Ownership.</strong> This lives in {@code integration.slack} because it reads Slack's own tables; the
 * agent {@code ConversationConsentGate} delegates here through the SPI, so the mentor derived-feedback content
 * sources never reach into the Slack schema. Raw {@link JdbcTemplate} with an explicit {@code workspace_id}
 * predicate (the tenancy {@code StatementInspector} only hooks Hibernate).
 */
@Component
public class SlackConversationSourceLiveness implements ConversationSourceLiveness {

    private final JdbcTemplate jdbc;

    public SlackConversationSourceLiveness(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<Long> activeThreadIds(long workspaceId, Collection<Long> threadIds) {
        if (threadIds.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = new ArrayList<>(threadIds);
        String placeholders = ids
            .stream()
            .map(id -> "?")
            .collect(Collectors.joining(","));
        Object[] args = new Object[ids.size() + 1];
        args[0] = workspaceId;
        for (int i = 0; i < ids.size(); i++) {
            args[i + 1] = ids.get(i);
        }
        return new HashSet<>(
            jdbc.queryForList(
                "SELECT t.id FROM slack_thread t " +
                    "JOIN slack_monitored_channel c " +
                    "  ON c.workspace_id = t.workspace_id AND c.slack_channel_id = t.slack_channel_id " +
                    "WHERE t.workspace_id = ? AND t.id IN (" +
                    placeholders +
                    ") AND c.consent_state = 'ACTIVE'",
                Long.class,
                args
            )
        );
    }
}
