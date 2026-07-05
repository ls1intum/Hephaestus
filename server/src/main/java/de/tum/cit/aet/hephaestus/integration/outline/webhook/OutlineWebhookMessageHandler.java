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
 * Consumes a verified Outline document event off the unified JetStream lane and triggers a targeted
 * reconcile of the owning workspace's mirror.
 *
 * <p>Outline exposes no per-document refresh API, so every document event (create/update/delete/…)
 * drives the same whole-workspace reconcile. The consumer therefore collapses the flat event space
 * onto a single logical event key ({@link #EVENT_TYPE}) — {@link OutlineSubjectParser} maps any
 * {@code outline.<sub>.<event>} subject to it — instead of registering identical per-event handlers.
 * The specific event still travels on the wire (subject + dedup key) for observability and future
 * per-event routing.
 *
 * <p>Resolution mirrors the secret source: the delivery names its subscription in the body, that id
 * resolves to the ACTIVE Outline Connection's workspace. Throwing propagates to the consumer's
 * {@code IntegrationPoisonHandler} (NAK + backoff, ACK-after-N) rather than being swallowed.
 */
@Component
@ConditionalOnServerRole
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
public class OutlineWebhookMessageHandler implements IntegrationMessageHandler {

    /** The single logical event key every Outline document event resolves to. */
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
        String subscriptionId = readSubscriptionId(msg.getData());
        Optional<OutlineSubscription> subscription = connectionService.findOutlineSubscription(subscriptionId);
        if (subscription.isEmpty()) {
            // The subscription was deleted/disconnected between publish and consume. Nothing to
            // reconcile; ACK-as-no-op (returning normally) rather than NAK-looping forever.
            log.debug("outline.consumer: no ACTIVE subscription for delivery, skipping");
            return;
        }
        syncScheduler.syncWorkspaceNow(subscription.get().workspaceId());
    }

    private String readSubscriptionId(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            return root.path("webhookSubscriptionId").asString("");
        } catch (RuntimeException e) {
            return "";
        }
    }
}
