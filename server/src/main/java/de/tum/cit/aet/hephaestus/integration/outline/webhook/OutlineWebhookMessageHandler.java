package de.tum.cit.aet.hephaestus.integration.outline.webhook;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService.OutlineSubscription;
import de.tum.cit.aet.hephaestus.integration.core.handler.IntegrationMessageHandler;
import de.tum.cit.aet.hephaestus.integration.core.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.outline.sync.OutlineDocumentSyncScheduler;
import io.nats.client.Message;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes a verified Outline event off the unified JetStream lane and triggers a <em>targeted</em>
 * refresh of the owning workspace's mirror — the payload is trusted only for routing
 * ({@code event} + {@code payload.id}), never as content:
 * <ul>
 *   <li>{@code documents.*} → {@link OutlineDocumentSyncScheduler#refreshDocumentNow} (tombstone for
 *       delete-shaped events, {@code documents.info} + export otherwise, ≤2 API calls). A delivery
 *       without a payload id falls back to a whole-workspace reconcile.</li>
 *   <li>{@code collections.*} → {@link OutlineDocumentSyncScheduler#refreshCollectionCatalogNow}
 *       (catalog fields; {@code collections.delete} tombstones the mirrored documents).</li>
 *   <li>Anything else is logged at debug and acked.</li>
 * </ul>
 *
 * <p>All Outline events collapse onto a single logical event key ({@link #EVENT_TYPE}) —
 * {@link OutlineSubjectParser} maps any {@code outline.<sub>.<event>} subject to it — and the routing
 * above happens on the body's event name. The specific event still travels on the wire (subject +
 * dedup key) for observability.
 *
 * <p>Resolution mirrors the secret source: the delivery names its subscription in the body, that id
 * resolves to the ACTIVE Outline Connection's workspace. Throwing propagates to the consumer's
 * {@code IntegrationPoisonHandler} (NAK + backoff, ACK-after-N) rather than being swallowed.
 */
@Component
@ConditionalOnServerRole
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
public class OutlineWebhookMessageHandler implements IntegrationMessageHandler {

    /** The single logical event key every Outline event resolves to. */
    public static final String EVENT_TYPE = "document";

    private static final Logger log = LoggerFactory.getLogger(OutlineWebhookMessageHandler.class);

    private final ConnectionService connectionService;
    private final OutlineDocumentSyncScheduler syncScheduler;
    private final ObjectMapper objectMapper;

    public OutlineWebhookMessageHandler(
        ConnectionService connectionService,
        OutlineDocumentSyncScheduler syncScheduler,
        ObjectMapper objectMapper
    ) {
        this.connectionService = connectionService;
        this.syncScheduler = syncScheduler;
        this.objectMapper = objectMapper;
    }

    @Override
    public EventTypeKey key() {
        return new EventTypeKey(IntegrationKind.OUTLINE, EVENT_TYPE);
    }

    @Override
    public void onMessage(Message msg) {
        Delivery delivery = parse(msg.getData());
        Optional<OutlineSubscription> subscription = connectionService.findOutlineSubscription(
            delivery.subscriptionId()
        );
        if (subscription.isEmpty()) {
            // The subscription was deleted/disconnected between publish and consume. Nothing to
            // refresh; ACK-as-no-op (returning normally) rather than NAK-looping forever.
            log.debug("outline.consumer: no ACTIVE subscription for delivery, skipping");
            return;
        }
        long workspaceId = subscription.get().workspaceId();
        String event = delivery.event();
        if (event.startsWith("documents.")) {
            if (delivery.payloadId().isBlank()) {
                // Cannot target without a document id — fall back to the authoritative reconcile.
                syncScheduler.syncWorkspaceNow(workspaceId);
            } else {
                syncScheduler.refreshDocumentNow(workspaceId, event, delivery.payloadId());
            }
        } else if (event.startsWith("collections.")) {
            syncScheduler.refreshCollectionCatalogNow(
                workspaceId,
                event,
                delivery.payloadId().isBlank() ? null : delivery.payloadId()
            );
        } else {
            log.debug("outline.consumer: ignoring event '{}' for workspaceId={}", event, workspaceId);
        }
    }

    private Delivery parse(byte[] body) {
        if (body == null || body.length == 0) {
            return new Delivery("", "", "");
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            return new Delivery(
                root.path("webhookSubscriptionId").asString(""),
                root.path("event").asString(""),
                root.path("payload").path("id").asString("")
            );
        } catch (RuntimeException e) {
            return new Delivery("", "", "");
        }
    }

    /** The routing-relevant fields of one delivery; blanks when absent/unparsable. */
    private record Delivery(String subscriptionId, String event, String payloadId) {}
}
