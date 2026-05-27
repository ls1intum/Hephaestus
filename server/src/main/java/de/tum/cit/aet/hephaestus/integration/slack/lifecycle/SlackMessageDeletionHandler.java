package de.tum.cit.aet.hephaestus.integration.slack.lifecycle;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.slack.refs.SlackMessageRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles Slack {@code message_deleted} webhook events.
 *
 * <p>Salesforce 2025-05 Slack API ToS contract (as relaxed by the May-2025 clarification
 * that workspace-internal persistence IS permitted): when Slack notifies us that a
 * message has been deleted in the source workspace, we must tombstone the corresponding
 * {@code slack_message} row within a reasonable window. Soft-delete (rather than physical
 * delete) preserves the audit trail "this message existed at this ts and was deleted
 * at that ts" while ensuring no future query returns the content.
 *
 * <p><b>Status:</b> skeleton. Wiring from the Slack webhook dispatcher to this handler
 * ships with #1205 (the Slack runtime epic). The bean shape + {@link #onMessageDeleted}
 * signature are stabilised here so #1205 can land as additive code without changing
 * the persistence boundary.
 *
 * <p>The bean is constructed (rather than left as a stub interface) because:
 * <ol>
 *   <li>It anchors the deletion contract in the same package as the entity and the
 *       lifecycle listener — colocation of the ToS-compliance surface.</li>
 *   <li>The implementation is real (one repository call, idempotent UPDATE);
 *       there is no behavioural risk to shipping it before the webhook dispatcher
 *       routes to it.</li>
 *   <li>Once the dispatcher wires up, integration tests can drop straight onto a
 *       persisted bean rather than blocking on a context-refresh extension.</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = true)
public class SlackMessageDeletionHandler {

    private static final Logger log = LoggerFactory.getLogger(SlackMessageDeletionHandler.class);

    private final ConnectionRepository connectionRepository;
    private final SlackMessageRepository messageRepository;
    private final Clock clock;

    public SlackMessageDeletionHandler(
        ConnectionRepository connectionRepository,
        SlackMessageRepository messageRepository,
        Clock clock
    ) {
        this.connectionRepository = connectionRepository;
        this.messageRepository = messageRepository;
        this.clock = clock;
    }

    /**
     * Tombstone a Slack message.
     *
     * <p>Resolves the {@link Connection} by (workspaceId, SLACK, teamId), then issues a
     * single-row UPDATE that sets {@code deleted_at} only when it was previously null
     * (idempotent under webhook redelivery). Returns the number of rows affected: 1
     * on first delivery, 0 on either (a) replayed delivery or (b) message we never
     * ingested in the first place. Both are non-errors.
     *
     * <p>An empty Optional return indicates the connection could not be resolved —
     * caller logs and drops (the webhook secret would have failed verification long
     * before we got here, so an unknown team_id is a sign of a stale subscription
     * not yet purged, not a security event).
     */
    @Transactional
    public Optional<Integer> onMessageDeleted(long workspaceId, String teamId, String channelId, String ts) {
        Optional<Connection> connectionOpt = connectionRepository.findByWorkspaceIdAndKindAndInstanceKey(
            workspaceId,
            IntegrationKind.SLACK,
            teamId
        );
        if (connectionOpt.isEmpty()) {
            log.warn("Slack message_deleted for unknown team workspace={} team={}", workspaceId, teamId);
            return Optional.empty();
        }
        long connectionId = connectionOpt.get().getId();
        Instant now = Instant.now(clock);
        int updated = messageRepository.softDelete(workspaceId, connectionId, channelId, ts, now);
        if (updated == 0) {
            log.debug(
                "Slack message_deleted noop (replayed or never ingested) connection={} channel={} ts={}",
                connectionId,
                channelId,
                ts
            );
        } else {
            log.info("Slack message tombstoned connection={} channel={} ts={}", connectionId, channelId, ts);
        }
        return Optional.of(updated);
    }
}
