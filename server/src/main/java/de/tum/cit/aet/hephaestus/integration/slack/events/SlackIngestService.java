package de.tum.cit.aet.hephaestus.integration.slack.events;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMessageRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannelRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import de.tum.cit.aet.hephaestus.integration.slack.mentor.SlackMentorIdentityResolver;
import de.tum.cit.aet.hephaestus.practices.spi.ConversationFeedbackErasure;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists inbound Slack channel messages as workspace-scoped content (the substrate that
 * {@code SlackConversationContentSource} exposes to the mentor). Stores rendered text only (data minimization).
 * Idempotent on {@code (workspace, channel, ts)}.
 *
 * <p><strong>Capability flag (fail-closed layer 1).</strong> Channel/group message ingestion is gated by
 * {@code hephaestus.integration.slack.conversation-ingest.enabled}. The capability is <em>available by default</em>
 * so each workspace can self-serve, but availability alone reads nothing — while the flag is off,
 * {@link #ingestChannelMessage} returns immediately (no discovery row, no store) and the whole channel-ingest →
 * conversation-detection → conversation-feedback subsystem is inert; setting it to {@code false} hard-disables the
 * subsystem fleet-wide. Even with the flag on, ingestion still requires the per-channel consent gate ({@code ACTIVE})
 * and the per-person opt-out firewall below, and is forward-only, so nothing is read until a channel is deliberately
 * activated. The DM/mentor path and everything else in the Slack integration are unaffected by this flag.
 *
 * <p><strong>Consent gate (fail-closed layer 2).</strong> Even with the capability flag on, a message is only
 * persisted once its channel's {@link ConsentState} is {@code ACTIVE}. Channels enter the allow-list (as
 * {@code PENDING}) when the bot is invited ({@code member_joined_channel}) or via the admin directory — approval is
 * an explicit, out-of-band consent action, never an implicit side effect of traffic.
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
    private final SlackChannelConsentGate consentGate;
    private final SlackParticipantConsentGate participantConsentGate;
    private final SlackMessageRepository messageRepository;
    private final SlackThreadRepository threadRepository;
    private final SlackMentorIdentityResolver identityResolver;
    private final ConversationFeedbackErasure conversationFeedbackErasure;

    /**
     * Capability flag, available by default. Gates the channel-ingest entry point ({@link #ingestChannelMessage})
     * as the first fail-closed layer in front of the per-channel consent gate and the per-person firewall: while
     * this is {@code false}, channel/group messages are never ingested at all, so the conversation-detection/feedback
     * subsystem downstream of it stays completely dormant. Bound from
     * {@code hephaestus.integration.slack.conversation-ingest.enabled}.
     */
    private final boolean conversationIngestEnabled;

    public SlackIngestService(
        SlackWorkspaceResolver workspaceResolver,
        SlackMonitoredChannelRepository monitoredChannelRepository,
        SlackChannelConsentGate consentGate,
        SlackParticipantConsentGate participantConsentGate,
        SlackMessageRepository messageRepository,
        SlackThreadRepository threadRepository,
        SlackMentorIdentityResolver identityResolver,
        ConversationFeedbackErasure conversationFeedbackErasure,
        @Value("${hephaestus.integration.slack.conversation-ingest.enabled:true}") boolean conversationIngestEnabled
    ) {
        this.workspaceResolver = workspaceResolver;
        this.monitoredChannelRepository = monitoredChannelRepository;
        this.consentGate = consentGate;
        this.participantConsentGate = participantConsentGate;
        this.messageRepository = messageRepository;
        this.threadRepository = threadRepository;
        this.identityResolver = identityResolver;
        this.conversationFeedbackErasure = conversationFeedbackErasure;
        this.conversationIngestEnabled = conversationIngestEnabled;
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
        // Fail-closed layer 1: the channel-ingest capability flag. Available by default, but when disabled nothing
        // is discovered, gated, or stored — the subsystem is fully dormant regardless of channel/person consent.
        if (!conversationIngestEnabled) {
            return;
        }
        if (channelId.isEmpty() || ts.isEmpty()) {
            return;
        }
        Optional<Long> workspaceOpt = workspaceResolver.resolveWorkspaceId(teamId);
        if (workspaceOpt.isEmpty()) {
            return;
        }
        long workspaceId = workspaceOpt.get();

        // Consent gate (single authority): only ACTIVE channels flow content. Fails closed on an absent row.
        // Channels enter the allow-list via bot member_joined_channel registration or the admin directory —
        // never as a side effect of message traffic.
        if (!consentGate.ingestAllowed(workspaceId, channelId)) {
            return;
        }

        // Forward-only invariant: on an ACTIVE channel, ingestion is bounded to messages that arrived strictly after
        // the consent announcement was posted (consent_announced_at). Pre-announcement history never enters — a
        // channel activated today does not retroactively ingest the backlog people wrote before they were told. An
        // ACTIVE channel is always stamped at activation, so a missing stamp fails closed (store nothing).
        Instant announcedAt = monitoredChannelRepository.findConsentAnnouncedAt(workspaceId, channelId).orElse(null);
        if (announcedAt == null || !isAfterAnnouncement(ts, announcedAt)) {
            return;
        }

        // Person firewall: an individual who opted out of ingestion is never stored,
        // even on an ACTIVE channel with the capability on. This composes the two-layer gate into:
        //   ingest iff conversationIngestEnabled AND channel == ACTIVE AND NOT participantOptedOut(workspace, author).
        // Deny-if-opted-out / allow-if-absent, keyed on the author's Slack id (an unauthored/blank sender has no
        // person to gate, so it proceeds — it stamps no member id and unions nothing into participant_member_ids).
        if (
            authorSlackUserId != null &&
            !authorSlackUserId.isBlank() &&
            !participantConsentGate.ingestionAllowed(workspaceId, authorSlackUserId)
        ) {
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
     * Whether a Slack message {@code ts} arrived strictly after the channel's consent announcement — the
     * forward-only comparison. A Slack {@code ts} is {@code <epoch-seconds>.<micros>}; parse it as a fractional
     * epoch and compare against {@code announcedAt} rendered the same way. An unparseable {@code ts} fails closed.
     */
    private static boolean isAfterAnnouncement(String ts, Instant announcedAt) {
        try {
            double messageEpoch = Double.parseDouble(ts);
            double announcedEpoch = announcedAt.getEpochSecond() + announcedAt.getNano() / 1_000_000_000.0;
            return messageEpoch > announcedEpoch;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Slack {@code message_deleted}: tombstone the stored message (stamp {@code deleted_at}, null its text). Keyed
     * on the deleted message's {@code ts} (the caller passes {@code deleted_ts}, falling back to
     * {@code previous_message.ts}) — NOT the tombstone event's own {@code ts}.
     */
    @Transactional
    public void tombstoneMessage(String teamId, String channelId, String deletedTs) {
        if (!conversationIngestEnabled || channelId.isEmpty() || deletedTs.isEmpty()) {
            return;
        }
        Optional<Long> workspaceOpt = workspaceResolver.resolveWorkspaceId(teamId);
        if (workspaceOpt.isEmpty()) {
            return;
        }
        long workspaceId = workspaceOpt.get();
        // Deliberately NOT gated on ACTIVE consent: a message stored while the channel was ACTIVE still exists
        // during PAUSED, and the author's deletion must erase our copy in every state. The forward-only window
        // below still applies (a pre-announcement message was never stored), and the durable upsert both
        // tombstones an ingested row AND blocks a later out-of-order base insert from resurrecting the content.
        // No participant-firewall check: a tombstone is contentless (text NULL).
        Instant announcedAt = monitoredChannelRepository.findConsentAnnouncedAt(workspaceId, channelId).orElse(null);
        if (announcedAt == null || !isAfterAnnouncement(deletedTs, announcedAt)) {
            return;
        }
        messageRepository.tombstone(workspaceId, teamId, channelId, deletedTs, Instant.now());
    }

    /**
     * Slack {@code message_changed}: replace the stored text with the edited body ({@code event.message}) and
     * stamp {@code edited_at}. Durable against out-of-order delivery, at parity with the tombstone: when the base
     * insert has not yet arrived (a NAK-redelivered reorder), the edited body is routed back through the fully-gated
     * {@link #ingestChannelMessage} so it is stored durably AND consent-safely (consent + forward-only watermark +
     * participant firewall + identity stamp), rather than lost. A no-op only when the row is already tombstoned
     * (never resurrected). Honors the channel-ingest kill switch, like ingest and tombstone.
     */
    @Transactional
    public void editMessage(
        String teamId,
        String channelId,
        String ts,
        @Nullable String threadTs,
        @Nullable String authorSlackUserId,
        @Nullable String text
    ) {
        // Fail-closed layer 1: honor the channel-ingest kill switch (ingest + tombstone both do). editMessage can now
        // INSERT via re-ingest.
        if (!conversationIngestEnabled || channelId.isEmpty() || ts.isEmpty()) {
            return;
        }
        Optional<Long> workspaceOpt = workspaceResolver.resolveWorkspaceId(teamId);
        if (workspaceOpt.isEmpty()) {
            return;
        }
        long workspaceId = workspaceOpt.get();

        // Normal case: the base row exists -> scoped UPDATE swaps in the edited text. The deleted_at IS NULL guard in
        // applyEdit means a delete-then-edit reorder no-ops (updated == 0, row present) and never resurrects a tombstone.
        int updated = messageRepository.applyEdit(workspaceId, channelId, ts, text, Instant.now());
        if (updated > 0) {
            return;
        }
        // Durable edit-before-insert: the edit raced ahead of a NAK-redelivered base insert (row absent). Route the
        // edited body through the SAME fail-closed ingest stack (consent + forward-only watermark + participant firewall
        // + identity stamp) so it is stored durably AND consent-safely; the later insertIfAbsent (ON CONFLICT DO NOTHING)
        // then no-ops. existsBy distinguishes a TOMBSTONED row (updated == 0 but present -> skip, no resurrection) from a
        // genuinely-absent one (re-ingest).
        if (!messageRepository.existsByWorkspaceIdAndSlackChannelIdAndSlackTs(workspaceId, channelId, ts)) {
            ingestChannelMessage(teamId, channelId, ts, threadTs, authorSlackUserId, text);
            // Stamp edited_at (insertIfAbsent does not): a row born from message_changed genuinely is an edited message,
            // so the projector's `edited` flag reads true. Idempotent second write on the now-present row.
            messageRepository.applyEdit(workspaceId, channelId, ts, text, Instant.now());
        }
    }

    /**
     * Channel erasure: flip the channel to {@code REVOKED} so ingestion stops immediately and its stored threads
     * drop out of every {@code consent_state = 'ACTIVE'} projector, <em>and</em> promptly delete the channel's
     * ingested content — its {@code slack_message} rows (the raw message text) and its {@code slack_thread}
     * aggregates (which hold the {@code participant_member_ids} personal data) — rather than waiting for the
     * 180-day retention sweep, which covers messages only and would leave the thread aggregates behind. Every
     * write carries the {@code workspace_id} predicate; the whole method is transactional and idempotent (a
     * channel that was never allow-listed, or was already erased, deletes 0 rows).
     *
     * <p><strong>Derived CONVERSATION feedback is now hard-deleted too (true erasure, not inert-by-gate).</strong>
     * The observations/feedback composed from these threads carry {@code artifact_type = CONVERSATION_THREAD} +
     * {@code artifact_id = slack_thread.id} but hold no FK back to {@code slack_thread}, so dropping the aggregate
     * alone would only render them INERT (the mentor consent gate withholds them once the channel is non-ACTIVE).
     * To satisfy GDPR Art. 17 for the derived copies we collect the channel's thread ids first and call the
     * practices-owned {@link ConversationFeedbackErasure} port, which deletes exactly those CONVERSATION_THREAD
     * rows (cascading their {@code feedback_observation}/placement/reaction children) and nothing else — PR/ISSUE
     * rows and other tenants' rows are untouched. The port inverts the dependency ({@code integration.slack →
     * practices::spi}, implementation inside {@code practices}), so no Spring Modulith cycle forms.
     */
    @Transactional
    public void eraseChannel(long workspaceId, String channelId) {
        // Collect the channel's thread ids BEFORE dropping the aggregates — the derived practice rows are keyed by
        // slack_thread.id as their artifact_id, so we need the ids to erase the derived copies.
        List<Long> threadIds = threadRepository.findIdsByWorkspaceIdAndSlackChannelId(workspaceId, channelId);
        monitoredChannelRepository.revokeConsent(workspaceId, channelId);
        conversationFeedbackErasure.eraseForThreads(workspaceId, threadIds);
        messageRepository.deleteByWorkspaceIdAndSlackChannelId(workspaceId, channelId);
        threadRepository.deleteByWorkspaceIdAndSlackChannelId(workspaceId, channelId);
    }
}
