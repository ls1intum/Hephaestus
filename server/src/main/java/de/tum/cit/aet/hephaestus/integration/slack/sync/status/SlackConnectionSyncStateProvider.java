package de.tum.cit.aet.hephaestus.integration.slack.sync.status;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.framework.CronSchedules;
import de.tum.cit.aet.hephaestus.integration.core.spi.BackfillSummary;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionSyncDetails;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionSyncStateProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.core.spi.RateLimitSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncResourceState;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMessageRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannelRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackTs;
import de.tum.cit.aet.hephaestus.integration.slack.sync.SlackSyncProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Read-only Slack sync state built without vendor API calls. */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackConnectionSyncStateProvider implements ConnectionSyncStateProvider {

    private final ConnectionRepository connectionRepository;
    private final SlackMonitoredChannelRepository monitoredChannelRepository;
    private final SlackMessageRepository messageRepository;
    private final SlackSyncProperties properties;

    public SlackConnectionSyncStateProvider(
        ConnectionRepository connectionRepository,
        SlackMonitoredChannelRepository monitoredChannelRepository,
        SlackMessageRepository messageRepository,
        SlackSyncProperties properties
    ) {
        this.connectionRepository = connectionRepository;
        this.monitoredChannelRepository = monitoredChannelRepository;
        this.messageRepository = messageRepository;
        this.properties = properties;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.SLACK;
    }

    @Override
    public ConnectionSyncDetails describe(IntegrationRef ref, long connectionId) {
        Boolean webhookRegistered = connectionRepository
            .findByIdAndWorkspaceId(connectionId, ref.workspaceId())
            .map(Connection::getState)
            .map(state -> state == IntegrationState.ACTIVE ? Boolean.TRUE : null)
            .orElse(null);

        return new ConnectionSyncDetails(
            webhookRegistered,
            CronSchedules.nextRun(properties.cron()),
            null,
            null,
            false,
            null
        );
    }

    @Override
    public List<SyncResourceState> resources(IntegrationRef ref, long connectionId) {
        long workspaceId = ref.workspaceId();
        Map<String, Long> itemCountByChannelId = messageRepository
            .countGroupedByChannelId(workspaceId)
            .stream()
            .collect(
                Collectors.toMap(
                    SlackMessageRepository.ChannelItemCount::getSlackChannelId,
                    SlackMessageRepository.ChannelItemCount::getItemCount
                )
            );

        return monitoredChannelRepository
            .findByWorkspaceIdAndConsentStateNot(workspaceId, ConsentState.REVOKED)
            .stream()
            .map(channel -> toResourceState(channel, itemCountByChannelId))
            .toList();
    }

    private SyncResourceState toResourceState(SlackMonitoredChannel channel, Map<String, Long> itemCountByChannelId) {
        Long itemCount = itemCountByChannelId.getOrDefault(channel.getSlackChannelId(), 0L);
        String name = channel.getChannelName() != null ? channel.getChannelName() : channel.getSlackChannelId();
        return new SyncResourceState(
            channel.getId(),
            channel.getSlackChannelId(),
            name,
            SyncResourceState.Type.CHANNEL,
            channel.getConsentState().name(),
            toInstant(channel.getLastHistorySyncedTs()),
            itemCount,
            null,
            null,
            null,
            null
        );
    }

    private static @Nullable Instant toInstant(@Nullable String slackTs) {
        Long micros = SlackTs.toEpochMicros(slackTs);
        if (micros == null) {
            return null;
        }
        return Instant.ofEpochSecond(micros / 1_000_000L, (micros % 1_000_000L) * 1_000L);
    }
}
