package de.tum.cit.aet.hephaestus.integration.slack.sync.status;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * Slack's {@link ConnectionSyncStateProvider}: read-only, {@code O(DB + in-memory)} projection over
 * already-persisted state (design doc §3.2/§3.4) — never a live Slack API call.
 *
 * <p>Rate limit is always {@code null}: unlike GitHub/GitLab, Slack does not surface per-call
 * remaining/reset headers we track — the budget is a static, configured request ceiling
 * ({@link SlackSyncProperties#historyRequestBudget()}) the sync self-paces against, not a live
 * vendor-reported number. There is nothing to snapshot.
 *
 * <p>Backfill is always {@code null}: Slack's history sync is forward-only by design (the consent
 * floor is {@code max(consent_announced_at, retention cutoff, last watermark)} — see
 * {@code SlackChannelHistorySyncService}) and deliberately never replays pre-consent history. A
 * connection-level "how far back have we gone" rollup does not apply.
 */
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
            nextScheduledSyncAt(),
            null, // rateLimit — see class javadoc
            null, // backfill — see class javadoc
            false,
            null
        );
    }

    @Override
    public List<SyncResourceState> resources(IntegrationRef ref, long connectionId) {
        long workspaceId = ref.workspaceId();
        // Single grouped count for the workspace's channels (one query) instead of one count per channel.
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
            null, // upstreamCount — no cheap vendor count available without a live API call
            null, // lastError — per-resource error tracking not modeled for Slack in v1
            null,
            null
        );
    }

    /**
     * Next fire time of {@code hephaestus.sync.slack.cron}, computed in the server's default zone — the
     * same zone {@code @Scheduled(cron = ...)} uses when no explicit zone is configured.
     */
    private @Nullable Instant nextScheduledSyncAt() {
        String cron = properties.cron();
        if (!CronExpression.isValidExpression(cron)) {
            return null;
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime next = CronExpression.parse(cron).next(LocalDateTime.now(zone));
        return next == null ? null : next.atZone(zone).toInstant();
    }

    private static @Nullable Instant toInstant(@Nullable String slackTs) {
        Long micros = SlackTs.toEpochMicros(slackTs);
        if (micros == null) {
            return null;
        }
        return Instant.ofEpochSecond(micros / 1_000_000L, (micros % 1_000_000L) * 1_000L);
    }
}
