package de.tum.cit.aet.hephaestus.integration.slack.domain;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

public class SlackMessageAccessImpl implements SlackMessageAccess {
    @PersistenceContext
    private @Nullable EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public List<SlackMessageKeyRow> findReadableMessageKeysByWorkspaceId(
            long workspaceId, Collection<SlackMessageKeyRow> messages) {
        if (messages.isEmpty()) return List.of();
        var distinct = new ArrayList<>(new LinkedHashSet<>(messages));
        // Bind each pair together. Independent IN sets would also read uncited cross-pairs.
        String pairs = IntStream.range(0, distinct.size())
                .mapToObj(index -> "(m.slackChannelId=:channel" + index + " AND m.slackTs=:message" + index + ")")
                .collect(Collectors.joining(" OR "));
        var query = Objects.requireNonNull(entityManager)
                .createQuery("""
                SELECT new de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMessageKeyRow(m.slackChannelId,m.slackTs)
                FROM SlackMessage m JOIN SlackMonitoredChannel c
                  ON c.workspaceId=m.workspaceId AND c.slackChannelId=m.slackChannelId
                WHERE m.workspaceId=:workspaceId AND (
                """ + pairs + ") " + SlackMessageRepository.MESSAGE_READ_GUARD, SlackMessageKeyRow.class);
        query.setParameter("workspaceId", workspaceId);
        for (int index = 0; index < distinct.size(); index++) {
            query.setParameter("channel" + index, distinct.get(index).channelId());
            query.setParameter("message" + index, distinct.get(index).messageTs());
        }
        return query.getResultList();
    }
}
