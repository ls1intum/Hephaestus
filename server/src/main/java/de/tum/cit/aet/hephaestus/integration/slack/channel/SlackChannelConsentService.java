package de.tum.cit.aet.hephaestus.integration.slack.channel;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackChannelConsentEvent;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackChannelConsentEventRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannelRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackParticipantConsentRepository;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackIngestService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackSendException;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single authority for the per-channel Slack consent lifecycle — the write-side counterpart to the read-side
 * {@code SlackChannelConsentGate}. It owns the guarded state machine that makes {@code consent_state = ACTIVE}
 * reachable, posts the in-channel consent announcement on activation, stamps {@code consent_announced_at} (the
 * forward-only ingestion boundary), erases the channel's raw + derived data on revocation, and writes an immutable
 * audit row for every transition.
 *
 * <p><strong>Mentoring-only, minimal state machine</strong> (no research gate, no forced wait window):
 * <pre>
 *   PENDING ──activate──▶ ACTIVE ⇄ PAUSED
 *      │                    │        │
 *      └────────── any → REVOKED (terminal: stop + ERASE) ──────────┘
 * </pre>
 * <ul>
 *   <li>{@code PENDING → ACTIVE}: post the announcement (what is read, purpose = AI practice mentoring, how to opt
 *       out via App Home) and stamp {@code consent_announced_at = now()}. Ingestion is forward-only —
 *       {@code SlackIngestService} only stores messages whose {@code ts} is strictly after this stamp.</li>
 *   <li>{@code ACTIVE ⇄ PAUSED}: stop / resume ingestion, keeping stored data. Resuming an already-announced
 *       channel does NOT re-announce or re-stamp (the original boundary stands).</li>
 *   <li>{@code * → REVOKED}: terminal. Stops ingestion and erases the channel's {@code slack_message} rows, its
 *       {@code slack_thread} aggregates, and the derived {@code CONVERSATION_THREAD} observations/feedback (via the
 *       already-built {@link SlackIngestService#eraseChannel}).</li>
 * </ul>
 * A same-state PATCH is an idempotent no-op (no side effect, no audit row). Every other edge not drawn above is a
 * violation → {@link SlackChannelConsentViolationException} (409).
 */
@Service
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackChannelConsentService {

    private static final Logger log = LoggerFactory.getLogger(SlackChannelConsentService.class);

    /**
     * The in-channel consent notice posted on activation. States what is read, the purpose, and how to opt out — the
     * transparency step members see before any of their (forward-only) messages are processed.
     */
    private static final String ANNOUNCEMENT_TEXT =
        "Hephaestus is now reading new messages in this channel to provide AI-powered software-practice mentoring. " +
        "Only messages sent from now on are used — earlier history is never read. If you would prefer your own " +
        "messages not be included, open the Hephaestus app, go to the *Home* tab, and choose *Opt out of ingestion*.";

    private final SlackMonitoredChannelRepository monitoredChannelRepository;
    private final SlackChannelConsentEventRepository consentEventRepository;
    private final SlackParticipantConsentRepository participantConsentRepository;
    private final SlackIngestService ingestService;
    private final SlackMessageService slackMessageService;
    private final ConnectionService connectionService;
    private final UserRepository userRepository;

    public SlackChannelConsentService(
        SlackMonitoredChannelRepository monitoredChannelRepository,
        SlackChannelConsentEventRepository consentEventRepository,
        SlackParticipantConsentRepository participantConsentRepository,
        SlackIngestService ingestService,
        SlackMessageService slackMessageService,
        ConnectionService connectionService,
        UserRepository userRepository
    ) {
        this.monitoredChannelRepository = monitoredChannelRepository;
        this.consentEventRepository = consentEventRepository;
        this.participantConsentRepository = participantConsentRepository;
        this.ingestService = ingestService;
        this.slackMessageService = slackMessageService;
        this.connectionService = connectionService;
        this.userRepository = userRepository;
    }

    /** All allow-listed channels for the workspace with their consent state + the workspace-wide opt-out count. */
    @Transactional(readOnly = true)
    public List<SlackMonitoredChannelDTO> listChannels(long workspaceId) {
        long optedOutMemberCount = participantConsentRepository.countByWorkspaceIdAndIngestionOptedOutTrue(workspaceId);
        return monitoredChannelRepository
            .findByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
            .stream()
            .map(channel -> SlackMonitoredChannelDTO.from(channel, optedOutMemberCount))
            .toList();
    }

    /** The immutable consent-transition audit trail of one channel (oldest first). */
    @Transactional(readOnly = true)
    public List<SlackChannelConsentEventDTO> listConsentEvents(long workspaceId, String slackChannelId) {
        // 404 if the channel is not allow-listed in this workspace (also enforces workspace isolation).
        monitoredChannelRepository
            .findByWorkspaceIdAndSlackChannelId(workspaceId, slackChannelId)
            .orElseThrow(() -> new EntityNotFoundException("Slack channel", slackChannelId));
        return consentEventRepository
            .findByWorkspaceIdAndSlackChannelIdOrderByCreatedAtAscIdAsc(workspaceId, slackChannelId)
            .stream()
            .map(SlackChannelConsentEventDTO::from)
            .toList();
    }

    /**
     * Allow-list a channel (idempotent on the natural key). Creates a {@code PENDING} row on first registration and
     * returns the existing row on the conflict, backfilling a previously-unknown {@code channelName} if one is now
     * supplied.
     *
     * @return the registration outcome, carrying whether a new row was created (201) or an existing one returned (200)
     */
    @Transactional
    public RegistrationOutcome register(long workspaceId, String slackChannelId, @Nullable String channelName) {
        var existing = monitoredChannelRepository.findByWorkspaceIdAndSlackChannelId(workspaceId, slackChannelId);
        if (existing.isPresent()) {
            SlackMonitoredChannel channel = existing.get();
            if (channelName != null && !channelName.isBlank() && (channel.getChannelName() == null)) {
                channel.setChannelName(channelName);
                monitoredChannelRepository.save(channel);
            }
            return new RegistrationOutcome(toDTO(workspaceId, channel), false);
        }

        String teamId = connectionService
            .findSlackNotificationConfig(workspaceId)
            .map(ConnectionConfig.SlackConfig::teamId)
            .filter(t -> t != null && !t.isBlank())
            // Discovery normally stamps the team id from the inbound event; a purely-admin registration needs an
            // ACTIVE Slack connection to know which Slack team this channel belongs to.
            .orElseThrow(() -> new EntityNotFoundException("Slack connection", Long.toString(workspaceId)));

        SlackMonitoredChannel channel = new SlackMonitoredChannel();
        channel.setWorkspaceId(workspaceId);
        channel.setSlackTeamId(teamId);
        channel.setSlackChannelId(slackChannelId);
        channel.setChannelName((channelName == null || channelName.isBlank()) ? null : channelName);
        channel.setConsentState(ConsentState.PENDING);
        SlackMonitoredChannel saved = monitoredChannelRepository.save(channel);
        return new RegistrationOutcome(toDTO(workspaceId, saved), true);
    }

    /**
     * Transition a channel to {@code target}, running the guarded state machine: validate the edge, perform the side
     * effect (announce on activate; erase on revoke), and append the immutable audit row. A same-state request is an
     * idempotent no-op; an illegal edge throws {@link SlackChannelConsentViolationException}.
     *
     * @param workspaceId    the acting workspace (tenant scope + isolation)
     * @param slackChannelId the channel's Slack {@code C…} id (the natural key / path var)
     * @param target         the requested consent state
     * @param reason         optional free-text reason recorded in the audit trail
     * @return the channel's DTO after the transition
     * @throws EntityNotFoundException            if the channel is not allow-listed in this workspace (404)
     * @throws SlackChannelConsentViolationException if the edge is not permitted (409)
     */
    @Transactional
    public SlackMonitoredChannelDTO transition(
        long workspaceId,
        String slackChannelId,
        ConsentState target,
        @Nullable String reason
    ) {
        SlackMonitoredChannel channel = monitoredChannelRepository
            .findByWorkspaceIdAndSlackChannelId(workspaceId, slackChannelId)
            .orElseThrow(() -> new EntityNotFoundException("Slack channel", slackChannelId));

        ConsentState from = channel.getConsentState();
        if (from == target) {
            return toDTO(workspaceId, channel); // idempotent no-op
        }
        requireAllowed(from, target, slackChannelId);

        switch (target) {
            case ACTIVE -> activate(channel);
            case PAUSED -> {
                channel.setConsentState(ConsentState.PAUSED);
                monitoredChannelRepository.save(channel);
            }
            case REVOKED -> {
                // Single erasure choke point: flips consent to REVOKED (bulk UPDATE) and hard-deletes the raw
                // messages, thread aggregates, and derived CONVERSATION_THREAD feedback. Reflect REVOKED on the
                // loaded entity for the returned DTO (the bulk update already persisted the row).
                ingestService.eraseChannel(workspaceId, slackChannelId);
                channel.setConsentState(ConsentState.REVOKED);
            }
            case PENDING -> throw new SlackChannelConsentViolationException(
                "PENDING is only reachable via discovery/registration, not a consent transition."
            );
        }

        recordAudit(workspaceId, slackChannelId, from, target, reason);
        return toDTO(workspaceId, channel);
    }

    private void activate(SlackMonitoredChannel channel) {
        if (channel.getConsentAnnouncedAt() == null) {
            // First activation: stamp the forward-only boundary, then post the transparency notice (best-effort).
            channel.setConsentAnnouncedAt(Instant.now());
            postAnnouncement(channel.getWorkspaceId(), channel.getSlackChannelId());
        }
        channel.setConsentState(ConsentState.ACTIVE);
        monitoredChannelRepository.save(channel);
    }

    private void postAnnouncement(long workspaceId, String channelId) {
        try {
            slackMessageService.sendForWorkspace(workspaceId, channelId, List.of(), ANNOUNCEMENT_TEXT);
        } catch (SlackSendException e) {
            // Best-effort: a Slack-side failure (e.g. not_in_channel, no token) must not block activation. The
            // announcement can be re-driven, and forward-only ingestion is anchored by the stamped timestamp.
            log.warn(
                "Slack consent announcement failed to post: workspaceId={}, channelId={}, error={}",
                workspaceId,
                channelId,
                e.slackError()
            );
        }
    }

    private void recordAudit(
        long workspaceId,
        String slackChannelId,
        ConsentState from,
        ConsentState to,
        @Nullable String reason
    ) {
        Long actorUserId = userRepository.getCurrentUser().map(User::getId).orElse(null);
        consentEventRepository.save(
            new SlackChannelConsentEvent(workspaceId, slackChannelId, from, to, actorUserId, reason)
        );
    }

    /** The mentoring-only transition guard. {@code from != target} is already handled (idempotent no-op) by callers. */
    private static void requireAllowed(ConsentState from, ConsentState target, String slackChannelId) {
        boolean allowed = switch (from) {
            case PENDING -> target == ConsentState.ACTIVE || target == ConsentState.REVOKED;
            case ACTIVE -> target == ConsentState.PAUSED || target == ConsentState.REVOKED;
            case PAUSED -> target == ConsentState.ACTIVE || target == ConsentState.REVOKED;
            case REVOKED -> false; // terminal
        };
        if (!allowed) {
            throw new SlackChannelConsentViolationException(
                "Illegal Slack channel consent transition " + from + " → " + target + " for channel " + slackChannelId
            );
        }
    }

    private SlackMonitoredChannelDTO toDTO(long workspaceId, SlackMonitoredChannel channel) {
        long optedOutMemberCount = participantConsentRepository.countByWorkspaceIdAndIngestionOptedOutTrue(workspaceId);
        return SlackMonitoredChannelDTO.from(channel, optedOutMemberCount);
    }

    /** Whether {@link #register} created a new allow-list row (201) or returned an existing one (200). */
    public record RegistrationOutcome(SlackMonitoredChannelDTO channel, boolean created) {}
}
