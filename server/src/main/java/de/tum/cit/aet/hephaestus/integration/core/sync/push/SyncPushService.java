package de.tum.cit.aet.hephaestus.integration.core.sync.push;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncStateChangedEvent;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

/**
 * Bridges {@link SyncStateChangedEvent} to {@link SyncEventHub} — the only consumer of that event
 * today (design doc §3.5). Multi-replica fan-out uses a plain (non-JetStream) NATS subject,
 * {@code hephaestus.syncstatus.<workspaceId>}: at-most-once is fine here, clients poll the REST
 * surface as a fallback if a hint is dropped.
 *
 * <p>The shared NATS {@link Connection} bean may be entirely ABSENT (feature disabled,
 * {@code hephaestus.sync.nats.enabled=false} — no bean method registered at all) or present but
 * {@code null} ({@code NatsConfig#natsConnection} explicitly returns {@code null} under the
 * {@code specs} profile). {@link ObjectProvider#getIfAvailable()} handles both: empty provider and
 * a null bean instance both come back as {@code null} here.
 *
 * <p>When NATS is available, a workspace-changed event is published to NATS ONLY — never fanned out
 * to the local {@link SyncEventHub} directly from this listener. Delivery to local emitters happens
 * exclusively via {@link #onMessage} on the {@code hephaestus.syncstatus.>} subscription, which every
 * replica (including the origin) receives exactly once. Publishing AND locally delivering here would
 * double-deliver on the origin replica.
 */
@Component
@ConditionalOnServerRole
public class SyncPushService {

    private static final Logger log = LoggerFactory.getLogger(SyncPushService.class);

    private static final String SUBJECT_PREFIX = "hephaestus.syncstatus.";
    private static final String WILDCARD_SUBJECT = SUBJECT_PREFIX + ">";

    private final SyncEventHub hub;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<Connection> natsConnectionProvider;
    private volatile Dispatcher dispatcher;

    public SyncPushService(
        SyncEventHub hub,
        ObjectMapper objectMapper,
        ObjectProvider<Connection> natsConnectionProvider
    ) {
        this.hub = hub;
        this.objectMapper = objectMapper;
        this.natsConnectionProvider = natsConnectionProvider;
        subscribeIfNatsAvailable();
    }

    /**
     * Subscribes once, at construction: the {@link Connection} bean (when present and non-null) is
     * already connected by the time Spring finishes constructing it (its {@code @Bean} method blocks
     * on {@code Nats.connect}), so there is no readiness race to wait out here.
     */
    private void subscribeIfNatsAvailable() {
        Connection connection = natsConnectionProvider.getIfAvailable();
        if (connection == null) {
            log.info("Sync push: NATS unavailable, using in-process delivery only");
            return;
        }
        dispatcher = connection.createDispatcher(this::onMessage);
        dispatcher.subscribe(WILDCARD_SUBJECT);
        log.info("Sync push: subscribed to {}", WILDCARD_SUBJECT);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSyncStateChanged(SyncStateChangedEvent event) {
        SyncEventHint hint = new SyncEventHint(wireScope(event.scope()), event.connectionId(), event.kind().name());
        Connection connection = natsConnectionProvider.getIfAvailable();
        if (connection == null) {
            hub.publish(event.workspaceId(), hint);
            return;
        }
        publishToNats(connection, event.workspaceId(), hint);
    }

    private void publishToNats(Connection connection, long workspaceId, SyncEventHint hint) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(hint);
            connection.publish(SUBJECT_PREFIX + workspaceId, payload);
        } catch (Exception e) {
            // At-most-once by design (class javadoc) — log and move on; the REST surface is the
            // source of truth and clients poll it as a fallback.
            log.warn("Sync push: NATS publish failed for workspaceId={}: {}", workspaceId, e.toString());
        }
    }

    /** Package-private so unit tests can drive an inbound message without a live NATS broker. */
    void onMessage(Message message) {
        String subject = message.getSubject();
        try {
            long workspaceId = parseWorkspaceId(subject);
            SyncEventHint hint = objectMapper.readValue(message.getData(), SyncEventHint.class);
            hub.publish(workspaceId, hint);
        } catch (Exception e) {
            log.warn("Sync push: failed to handle inbound message on subject '{}': {}", subject, e.toString());
        }
    }

    private static long parseWorkspaceId(String subject) {
        if (!subject.startsWith(SUBJECT_PREFIX)) {
            throw new IllegalArgumentException("Unexpected subject: " + subject);
        }
        return Long.parseLong(subject.substring(SUBJECT_PREFIX.length()));
    }

    private static String wireScope(SyncStateChangedEvent.Scope scope) {
        return switch (scope) {
            case JOB -> SyncEventHint.Scope.JOB.wireValue();
            case RESOURCES -> SyncEventHint.Scope.RESOURCES.wireValue();
            case CONNECTION -> SyncEventHint.Scope.CONNECTION.wireValue();
            case ACTIVITY -> SyncEventHint.Scope.ACTIVITY.wireValue();
        };
    }

    @PreDestroy
    void shutdown() {
        Dispatcher current = dispatcher;
        if (current != null) {
            try {
                current.unsubscribe(WILDCARD_SUBJECT);
            } catch (Exception e) {
                log.debug("Sync push: failed to unsubscribe cleanly: {}", e.toString());
            }
        }
    }
}
