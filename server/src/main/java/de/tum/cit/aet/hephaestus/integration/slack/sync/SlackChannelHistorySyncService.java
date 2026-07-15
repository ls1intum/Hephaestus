package de.tum.cit.aet.hephaestus.integration.slack.sync;

import com.slack.api.model.Message;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannelRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThread;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackTs;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackIngestService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService.HistoryPage;
import de.tum.cit.aet.hephaestus.integration.slack.retention.SlackRetentionSweeper;
import de.tum.cit.aet.hephaestus.integration.slack.webhook.SlackChannelMessageHandler;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Replays {@code conversations.history} (and, gap-driven, {@code conversations.replies}) for every ACTIVE monitored
 * channel through the SAME consent-gated ingest stack the event path uses — Slack's Events API drops an event after
 * three failed retries within ~6 minutes, so an outage longer than that would otherwise lose messages permanently.
 *
 * <p><strong>Consent invariants, by construction:</strong>
 * <ul>
 *   <li>The fetch floor is {@code max(consent_announced_at, retention cutoff, last watermark)} — pre-announcement
 *       history and content older than the retention window are never even requested. An ACTIVE channel with no
 *       announcement stamp is skipped (fail closed).</li>
 *   <li>Every message flows through {@link SlackIngestService#ingestChannelMessage}/{@code editMessage}, which
 *       re-check channel consent, the forward-only boundary, and the per-person opt-out firewall per message; the
 *       insert is idempotent ({@code ON CONFLICT DO NOTHING}), so overlap with delivered events is free.</li>
 *   <li>Channel consent is re-read before every API request, so a channel revoked mid-sync stops consuming budget
 *       immediately.</li>
 *   <li>Paused gaps are never backfilled: resuming a channel stamps the watermark to the resume instant
 *       (see {@code SlackChannelConsentService}), and PAUSED/PENDING/REVOKED channels are not in the sync set.</li>
 *   <li>The sync NEVER tombstones: a message absent from a page usually means pagination truncation, a filtered
 *       subtype, or a thread reply (never present in history) — inferring deletion from absence would mass-delete.
 *       Slack-side deletions are handled by the {@code message_deleted} event in every consent state.</li>
 * </ul>
 *
 * <p>The watermark advances only when a channel's window completed cleanly (cursor exhausted, no errors); a partial
 * window is simply re-fetched next night.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackChannelHistorySyncService {

    private static final Logger log = LoggerFactory.getLogger(SlackChannelHistorySyncService.class);

    private final SlackMonitoredChannelRepository monitoredChannelRepository;
    private final SlackThreadRepository threadRepository;
    private final SlackMessageService slackMessageService;
    private final SlackIngestService ingestService;
    private final ConnectionService connectionService;
    private final SlackSyncProperties properties;

    public SlackChannelHistorySyncService(
        SlackMonitoredChannelRepository monitoredChannelRepository,
        SlackThreadRepository threadRepository,
        SlackMessageService slackMessageService,
        SlackIngestService ingestService,
        ConnectionService connectionService,
        SlackSyncProperties properties
    ) {
        this.monitoredChannelRepository = monitoredChannelRepository;
        this.threadRepository = threadRepository;
        this.slackMessageService = slackMessageService;
        this.ingestService = ingestService;
        this.connectionService = connectionService;
        this.properties = properties;
    }

    /** Reconcile one workspace's ACTIVE channels within the request budget. Returns a summary for logging. */
    public WorkspaceSyncSummary syncWorkspace(long workspaceId) {
        SlackSyncBudget historyBudget = new SlackSyncBudget(
            properties.historyRequestBudget(),
            properties.historyRequestInterval()
        );
        SlackSyncBudget repliesBudget = new SlackSyncBudget(
            properties.repliesRequestBudget(),
            properties.historyRequestInterval()
        );
        Instant now = Instant.now();
        String retentionFloor = SlackTs.ofInstant(now.minus(Duration.ofDays(retentionWindowDays(workspaceId))));
        List<SlackMonitoredChannel> channels = monitoredChannelRepository.findForHistorySync(
            workspaceId,
            ConsentState.ACTIVE
        );

        int synced = 0;
        int skipped = 0;
        long ingested = 0;
        int failed = 0;
        for (SlackMonitoredChannel channel : channels) {
            if (!historyBudget.available()) {
                skipped = channels.size() - synced - skipped;
                break;
            }
            try {
                Long count = syncChannel(workspaceId, channel, retentionFloor, historyBudget, repliesBudget, now);
                if (count == null) {
                    skipped++;
                } else {
                    synced++;
                    ingested += count;
                }
            } catch (RuntimeException e) {
                // A channel that threw is both "not synced" (counted in skipped, preserving the progress
                // total) and a genuine partial failure (counted in failed) — the latter is the distinct
                // signal the runner elevates to SUCCEEDED_WITH_WARNINGS, unlike a benign nothing-to-sync skip.
                skipped++;
                failed++;
                log.warn(
                    "slack.sync: history sync failed for workspaceId={} channelId={} (watermark not advanced): {}",
                    workspaceId,
                    channel.getSlackChannelId(),
                    e.toString()
                );
            }
        }
        return new WorkspaceSyncSummary(
            channels.size(),
            synced,
            skipped,
            ingested,
            historyBudget.used() + repliesBudget.used(),
            !historyBudget.available(),
            failed
        );
    }

    /**
     * Sync one channel's window. Returns the number of messages routed through ingest on clean completion, or
     * {@code null} when the channel was skipped (no announcement stamp, consent flipped mid-sync, budget out).
     */
    private Long syncChannel(
        long workspaceId,
        SlackMonitoredChannel channel,
        String retentionFloor,
        SlackSyncBudget historyBudget,
        SlackSyncBudget repliesBudget,
        Instant windowEnd
    ) {
        String channelId = channel.getSlackChannelId();
        Instant announcedAt = channel.getConsentAnnouncedAt();
        if (announcedAt == null) {
            // An ACTIVE channel is always stamped at activation; a missing stamp fails closed, like ingest.
            return null;
        }
        String floor = SlackTs.max(
            SlackTs.max(SlackTs.ofInstant(announcedAt), retentionFloor),
            channel.getLastHistorySyncedTs()
        );
        String latest = SlackTs.ofInstant(windowEnd);
        if (floor == null || SlackTs.compare(floor, latest) >= 0) {
            return null;
        }

        long ingested = 0;
        String cursor = null;
        do {
            // Re-check consent before every request: a channel revoked mid-sync must stop consuming budget, and its
            // just-erased history must not be re-fetched.
            ConsentState state = monitoredChannelRepository.findConsentState(workspaceId, channelId).orElse(null);
            if (state != ConsentState.ACTIVE) {
                return null;
            }
            if (!historyBudget.acquire()) {
                return null; // budget exhausted mid-window: no watermark advance, re-fetched next night
            }
            HistoryPage page = slackMessageService.fetchHistoryPage(
                workspaceId,
                channelId,
                floor,
                latest,
                cursor,
                properties.historyPageLimit()
            );
            for (Message message : page.messages()) {
                ingested += routeThroughIngest(workspaceId, channel, message) ? 1 : 0;
                if (properties.repliesEnabled() && hasReplyGap(workspaceId, channelId, message)) {
                    ingested += syncReplies(workspaceId, channel, message, floor, repliesBudget);
                }
            }
            cursor = page.nextCursor();
        } while (cursor != null);

        monitoredChannelRepository.advanceHistoryWatermark(workspaceId, channelId, latest, Instant.now());
        return ingested;
    }

    /**
     * Route one fetched message through the gated ingest stack, applying the same author/subtype filters as the
     * event-path handler ({@link SlackChannelMessageHandler#CONTENT_BEARING_SUBTYPES}). Returns whether it was
     * eligible (the ingest stack itself may still refuse it — consent gates run inside).
     */
    private boolean routeThroughIngest(long workspaceId, SlackMonitoredChannel channel, Message message) {
        if (message.getBotId() != null) {
            return false;
        }
        String subtype = message.getSubtype() == null ? "" : message.getSubtype();
        if (!subtype.isEmpty() && !SlackChannelMessageHandler.CONTENT_BEARING_SUBTYPES.contains(subtype)) {
            return false;
        }
        String text = message.getText() == null ? "" : message.getText();
        if ("file_share".equals(subtype) && text.isBlank()) {
            return false;
        }
        String teamId = channel.getSlackTeamId();
        String channelId = channel.getSlackChannelId();
        if (message.getEdited() != null) {
            ingestService.editMessage(
                teamId,
                channelId,
                message.getTs(),
                message.getThreadTs(),
                message.getUser(),
                text
            );
        } else {
            ingestService.ingestChannelMessage(
                teamId,
                channelId,
                message.getTs(),
                message.getThreadTs(),
                message.getUser(),
                text
            );
        }
        return true;
    }

    /** Reply-gap detector: the parent claims more/nearer replies than our thread aggregate has seen. */
    private boolean hasReplyGap(long workspaceId, String channelId, Message parent) {
        String latestReply = parent.getLatestReply();
        Integer replyCount = parent.getReplyCount();
        if ((latestReply == null || latestReply.isBlank()) && (replyCount == null || replyCount == 0)) {
            return false;
        }
        SlackThread thread = threadRepository
            .findByWorkspaceIdAndSlackChannelIdAndSlackThreadTs(workspaceId, channelId, parent.getTs())
            .orElse(null);
        if (thread == null) {
            return true; // parent has replies we have never aggregated
        }
        if (latestReply != null && !latestReply.isBlank() && SlackTs.compare(latestReply, thread.getLastTs()) > 0) {
            return true;
        }
        return replyCount != null && replyCount > thread.getMessageCount();
    }

    /** Fetch a gapped thread's replies (bounded by the replies budget) through the same ingest stack. */
    private long syncReplies(
        long workspaceId,
        SlackMonitoredChannel channel,
        Message parent,
        String floor,
        SlackSyncBudget repliesBudget
    ) {
        long ingested = 0;
        String cursor = null;
        do {
            if (!repliesBudget.acquire()) {
                log.debug(
                    "slack.sync: replies budget exhausted for workspaceId={} channelId={} thread={}",
                    workspaceId,
                    channel.getSlackChannelId(),
                    parent.getTs()
                );
                return ingested;
            }
            HistoryPage page = slackMessageService.fetchRepliesPage(
                workspaceId,
                channel.getSlackChannelId(),
                parent.getTs(),
                floor,
                cursor,
                properties.historyPageLimit()
            );
            for (Message reply : page.messages()) {
                if (parent.getTs().equals(reply.getTs())) {
                    continue; // Slack includes the parent in conversations.replies
                }
                ingested += routeThroughIngest(workspaceId, channel, reply) ? 1 : 0;
            }
            cursor = page.nextCursor();
        } while (cursor != null);
        return ingested;
    }

    private int retentionWindowDays(long workspaceId) {
        int configured = connectionService
            .findSlackNotificationConfig(workspaceId)
            .map(ConnectionConfig.SlackConfig::retentionDaysOrDefault)
            .orElse(ConnectionConfig.SlackConfig.DEFAULT_RETENTION_DAYS);
        return Math.min(configured, SlackRetentionSweeper.MAX_RETENTION_DAYS);
    }

    /**
     * One workspace's sync outcome, for the scheduler's summary log. {@code failed} counts channels whose
     * history sync threw (a genuine partial failure, a subset of {@code skipped}); {@code failed > 0} is the
     * signal {@code SlackIntegrationSyncRunner} maps to {@code SUCCEEDED_WITH_WARNINGS}.
     */
    public record WorkspaceSyncSummary(
        int channels,
        int synced,
        int skipped,
        long ingested,
        int requestsUsed,
        boolean budgetExhausted,
        int failed
    ) {
        /** The workspace has no ACTIVE Slack connection, so nothing was attempted and no budget was spent. */
        public static WorkspaceSyncSummary notConnected() {
            return new WorkspaceSyncSummary(0, 0, 0, 0L, 0, false, 0);
        }
    }
}
