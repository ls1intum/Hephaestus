package de.tum.cit.aet.hephaestus.integration.slack.lifecycle;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationLifecycleListener;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.slack.refs.SlackChannelRepository;
import de.tum.cit.aet.hephaestus.integration.slack.refs.SlackMessageRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges Slack vendor lifecycle events to Slack-shaped persistence.
 *
 * <p>Two ToS-driven contracts are wired here, with the remainder deferred to #1204 /
 * #1205:
 * <ul>
 *   <li>{@link #onScopeChanged} removed-list → hard-delete the matching
 *       {@code slack_channel} rows for the connection. {@code slack_message} rows
 *       for those channels cascade physically through their FK on
 *       {@code slack_channel.id} only if a FK existed; today they cascade on
 *       {@code connection_id} (per the Liquibase schema), so we also explicitly
 *       purge messages by channel as part of the removal — this is the deletion
 *       guarantee the user-supplied ToS reading requires.</li>
 *   <li>{@link #onInstanceUninstalled} — the actual row deletion happens via the
 *       database-level {@code ON DELETE CASCADE} when the {@link Connection} is
 *       physically dropped by {@code WorkspaceIntegrationService}; this listener
 *       only logs for audit-trail clarity.</li>
 * </ul>
 *
 * <p>Webhook handling for {@code message_deleted} lives in
 * {@code SlackMessageDeletionHandler} — that path is not a lifecycle event in
 * the {@link IntegrationLifecycleListener} sense.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = true)
public class SlackLifecycleListener implements IntegrationLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(SlackLifecycleListener.class);

    private final ConnectionRepository connectionRepository;
    private final SlackChannelRepository channelRepository;
    private final SlackMessageRepository messageRepository;

    public SlackLifecycleListener(
        ConnectionRepository connectionRepository,
        SlackChannelRepository channelRepository,
        SlackMessageRepository messageRepository
    ) {
        this.connectionRepository = connectionRepository;
        this.channelRepository = channelRepository;
        this.messageRepository = messageRepository;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.SLACK;
    }

    /**
     * Slack scope change handler.
     *
     * <p>Resolves the connection by ref, then for each {@code removedExternalId} (Slack
     * channel id): deletes messages first (defensive — even if FK cascade were in place
     * we want a single transactional truth), then the channel row.
     *
     * <p>Idempotent: deleting an already-gone channel is a no-op. Logged so a redelivered
     * scope-change webhook does not flap audit logs.
     */
    @Override
    @Transactional
    public void onScopeChanged(IntegrationRef ref, ScopeDelta delta) {
        if (delta.removedExternalIds() == null || delta.removedExternalIds().isEmpty()) {
            return;
        }
        Optional<Connection> connectionOpt = resolveConnection(ref);
        if (connectionOpt.isEmpty()) {
            log.warn(
                "Slack scope-change for unknown connection ref={}, skipping deletion of {} channels",
                ref,
                delta.removedExternalIds().size()
            );
            return;
        }
        long connectionId = connectionOpt.get().getId();
        for (String channelId : delta.removedExternalIds()) {
            int messages = messageRepository.deleteByConnectionIdAndChannelId(connectionId, channelId);
            log.info(
                "Slack channel removal: connection={}, channel={}, messagesPurged={}",
                connectionId,
                channelId,
                messages
            );
        }
        int channels = channelRepository.deleteByConnectionIdAndChannelIdIn(
            connectionId,
            List.copyOf(delta.removedExternalIds())
        );
        log.info("Slack channel removal: connection={}, channelsPurged={}", connectionId, channels);
    }

    @Override
    public void onInstanceUninstalled(IntegrationRef ref) {
        // Physical cascade on FK(connection_id) ON DELETE CASCADE handles all slack_*
        // rows when the Connection is dropped. We log only — the deletion is a side
        // effect of WorkspaceIntegrationService.transition(UNINSTALLED) flushing the
        // owning row, NOT something this listener should drive.
        log.info("Slack instance uninstalled (cascade handled by Connection FK): ref={}", ref);
    }

    private Optional<Connection> resolveConnection(IntegrationRef ref) {
        if (ref.instanceKey() == null) {
            return Optional.empty();
        }
        return connectionRepository.findByWorkspaceIdAndKindAndInstanceKey(
            ref.workspaceId(),
            ref.kind(),
            ref.instanceKey()
        );
    }
}
