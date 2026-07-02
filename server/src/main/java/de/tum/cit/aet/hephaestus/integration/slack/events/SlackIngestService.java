package de.tum.cit.aet.hephaestus.integration.slack.events;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMessageRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannelRepository;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists inbound Slack channel messages as workspace-scoped content (the P3 substrate that
 * {@code SlackConversationContentSource} exposes to the mentor). Stores rendered text only (data minimization).
 * Idempotent on {@code (workspace, channel, ts)}. The allow-list row is auto-created on first sight (the bot only
 * receives events for channels it was invited to); consent gating of what is ultimately surfaced is the P3 message
 * handler's job, not this raw ingest path.
 *
 * <p>Persistence rides the {@code integration.slack.domain} JPA repositories — no raw {@code JdbcTemplate}. The
 * only non-JPA read is the team→workspace tenant resolution, isolated in {@link SlackWorkspaceResolver}.
 */
@Service
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackIngestService {

    private static final Logger log = LoggerFactory.getLogger(SlackIngestService.class);

    private final SlackWorkspaceResolver workspaceResolver;
    private final SlackMonitoredChannelRepository monitoredChannelRepository;
    private final SlackMessageRepository messageRepository;

    public SlackIngestService(
        SlackWorkspaceResolver workspaceResolver,
        SlackMonitoredChannelRepository monitoredChannelRepository,
        SlackMessageRepository messageRepository
    ) {
        this.workspaceResolver = workspaceResolver;
        this.monitoredChannelRepository = monitoredChannelRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public void ingestChannelMessage(
        String teamId,
        String channelId,
        String ts,
        @Nullable String threadTs,
        @Nullable String authorSlackUserId,
        @Nullable String text
    ) {
        if (channelId.isEmpty() || ts.isEmpty()) {
            return;
        }
        Optional<Long> workspaceOpt = workspaceResolver.resolveWorkspaceId(teamId);
        if (workspaceOpt.isEmpty()) {
            return;
        }
        long workspaceId = workspaceOpt.get();

        monitoredChannelRepository.insertIfAbsent(workspaceId, teamId, channelId);

        int inserted = messageRepository.insertIfAbsent(
            workspaceId,
            teamId,
            channelId,
            ts,
            threadTs,
            authorSlackUserId,
            text
        );
        if (inserted > 0) {
            log.debug("Ingested Slack message workspace={} channel={} ts={}", workspaceId, channelId, ts);
        }
    }
}
