package de.tum.cit.aet.hephaestus.integration.slack.events;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMessageRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannelRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import de.tum.cit.aet.hephaestus.integration.slack.mentor.SlackMentorIdentityResolver;
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
 * Idempotent on {@code (workspace, channel, ts)}.
 *
 * <p><strong>Consent gate (S6).</strong> Seeing a message on a channel auto-creates the allow-list row in
 * {@code PENDING} (discovery: the bot only receives events for channels it was invited to), but a message is only
 * persisted once that channel's {@link ConsentState} is {@code ACTIVE}. A {@code PENDING}/{@code ANNOUNCED}/
 * {@code PAUSED}/{@code REVOKED} channel discovers itself but ingests nothing — approval is an explicit,
 * out-of-band consent action, never an implicit side effect of traffic.
 *
 * <p>On a genuinely new message the author's Slack id is resolved to the workspace {@code User} (member) id and
 * stamped onto {@code slack_message.author_member_id} (the participant-firewall stamp), and the thread aggregate
 * is upserted (window advance + participant union). {@code message_changed}/{@code message_deleted} edit and
 * tombstone the stored row (GDPR Art. 17).
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
    private final SlackThreadRepository threadRepository;
    private final SlackMentorIdentityResolver identityResolver;

    public SlackIngestService(
        SlackWorkspaceResolver workspaceResolver,
        SlackMonitoredChannelRepository monitoredChannelRepository,
        SlackMessageRepository messageRepository,
        SlackThreadRepository threadRepository,
        SlackMentorIdentityResolver identityResolver
    ) {
        this.workspaceResolver = workspaceResolver;
        this.monitoredChannelRepository = monitoredChannelRepository;
        this.messageRepository = messageRepository;
        this.threadRepository = threadRepository;
        this.identityResolver = identityResolver;
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

        // Discovery: register the channel on first sight (PENDING). This does NOT authorize ingestion.
        monitoredChannelRepository.insertIfAbsent(workspaceId, teamId, channelId);

        // Consent gate: only ACTIVE channels flow content. A brand-new channel is PENDING → nothing ingested.
        ConsentState consent = monitoredChannelRepository
            .findConsentState(workspaceId, channelId)
            .orElse(ConsentState.PENDING);
        if (consent != ConsentState.ACTIVE) {
            return;
        }

        // Firewall stamp: the workspace member id the author links to within this workspace (null when unlinked).
        Long authorMemberId = (authorSlackUserId == null || authorSlackUserId.isBlank())
            ? null
            : identityResolver.resolveMemberId(workspaceId, teamId, authorSlackUserId).orElse(null);

        int inserted = messageRepository.insertIfAbsent(
            workspaceId,
            teamId,
            channelId,
            ts,
            threadTs,
            authorSlackUserId,
            authorMemberId,
            text
        );
        if (inserted > 0) {
            // thread_ts := the reply's parent, or the message's own ts for a thread root.
            String aggregateThreadTs = (threadTs == null || threadTs.isBlank()) ? ts : threadTs;
            threadRepository.upsertOnMessage(workspaceId, channelId, aggregateThreadTs, ts, authorMemberId);
            log.debug("Ingested Slack message workspace={} channel={} ts={}", workspaceId, channelId, ts);
        }
    }

    /**
     * Slack {@code message_deleted}: tombstone the stored message (stamp {@code deleted_at}, null its text). Keyed
     * on the deleted message's {@code ts} (the caller passes {@code deleted_ts}, falling back to
     * {@code previous_message.ts}) — NOT the tombstone event's own {@code ts}.
     */
    @Transactional
    public void tombstoneMessage(String teamId, String channelId, String deletedTs) {
        if (channelId.isEmpty() || deletedTs.isEmpty()) {
            return;
        }
        workspaceResolver
            .resolveWorkspaceId(teamId)
            .ifPresent(workspaceId -> messageRepository.tombstone(workspaceId, channelId, deletedTs));
    }

    /**
     * Slack {@code message_changed}: replace the stored text with the edited body ({@code event.message}) and
     * stamp {@code edited_at}. A no-op when the message was never ingested (e.g. the channel was not ACTIVE at the
     * time) or is already tombstoned.
     */
    @Transactional
    public void editMessage(String teamId, String channelId, String ts, @Nullable String text) {
        if (channelId.isEmpty() || ts.isEmpty()) {
            return;
        }
        workspaceResolver
            .resolveWorkspaceId(teamId)
            .ifPresent(workspaceId -> messageRepository.applyEdit(workspaceId, channelId, ts, text));
    }

    /**
     * Channel erasure (S6): flip the channel to {@code REVOKED} so ingestion stops immediately and its stored
     * threads drop out of the participant projector (which gates on {@code consent_state = 'ACTIVE'}). The
     * retained rows age out through the bounded-retention sweep. Idempotent — a channel that was never
     * allow-listed is a no-op.
     */
    @Transactional
    public void eraseChannel(long workspaceId, String channelId) {
        monitoredChannelRepository.revokeConsent(workspaceId, channelId);
    }
}
