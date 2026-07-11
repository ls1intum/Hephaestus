package de.tum.cit.aet.hephaestus.integration.outline.webhook;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService.OutlineSubscription;
import de.tum.cit.aet.hephaestus.integration.core.handler.IntegrationMessageHandler;
import de.tum.cit.aet.hephaestus.integration.core.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineDocumentListResponse;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentEvent;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentEventRepository;
import de.tum.cit.aet.hephaestus.integration.outline.sync.OutlineDocumentSyncScheduler;
import io.nats.client.Message;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes a verified Outline event off the unified JetStream lane and triggers a <em>targeted</em>
 * refresh of the owning workspace's mirror:
 * <ul>
 *   <li>{@code documents.*} → {@link OutlineDocumentSyncScheduler#refreshDocumentNow} (tombstone for
 *       delete-shaped events, an in-place {@code archivedAt} stamp for {@code documents.archive},
 *       {@code documents.info} + export otherwise, ≤2 API calls). A delivery without a payload id falls
 *       back to a whole-workspace reconcile.</li>
 *   <li>{@code collections.*} → {@link OutlineDocumentSyncScheduler#refreshCollectionCatalogNow}
 *       (catalog fields; {@code collections.delete} tombstones the mirrored documents).</li>
 *   <li>Anything else is logged at debug and acked.</li>
 * </ul>
 *
 * <p><strong>Trust model.</strong> The webhook signature (verified upstream, before this handler runs)
 * covers the raw bytes of the WHOLE envelope — {@code event} + {@code payload.id} are trusted for routing,
 * and {@code payload.model} (the document's own metadata snapshot at delivery time) is equally
 * authenticated, so a document event carrying a usable model (id + collection id) is trusted as metadata:
 * {@link #parseModel} extracts it and the sync path skips its own {@code documents.info} round-trip,
 * roughly halving the webhook-path API calls. The document BODY never travels in the envelope, so
 * {@code documents.export} still runs whenever content is needed. A missing/incomplete model falls back to
 * a {@code documents.info} call instead.
 *
 * <p>Every {@code documents.*} delivery (including deletes) additionally appends one
 * {@link OutlineDocumentEvent} row from the envelope ({@code actorId} + {@code createdAt}) BEFORE
 * routing — the longitudinal editing-habit log. Best-effort by contract: an event-log failure must
 * never block the mirror refresh, so the append is isolated in its own try/catch.
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
    private final OutlineDocumentEventRepository documentEventRepository;
    private final ObjectMapper objectMapper;

    public OutlineWebhookMessageHandler(
        ConnectionService connectionService,
        OutlineDocumentSyncScheduler syncScheduler,
        OutlineDocumentEventRepository documentEventRepository,
        ObjectMapper objectMapper
    ) {
        this.connectionService = connectionService;
        this.syncScheduler = syncScheduler;
        this.documentEventRepository = documentEventRepository;
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
            appendDocumentEvent(workspaceId, delivery);
            if (delivery.payloadId().isBlank()) {
                // Cannot target without a document id — fall back to the authoritative reconcile.
                syncScheduler.syncWorkspaceNow(workspaceId);
            } else {
                syncScheduler.refreshDocumentNow(workspaceId, event, delivery.payloadId(), delivery.model());
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

    /**
     * Appends the delivery to the per-document event log. Best-effort: the log is telemetry, the
     * refresh is the product — a failed INSERT (or a missing ACTIVE connection in a teardown race)
     * must never stop the routing below, so every failure is caught and logged here.
     */
    private void appendDocumentEvent(long workspaceId, Delivery delivery) {
        if (delivery.payloadId().isBlank()) {
            return; // no document id to attribute the event to
        }
        try {
            Optional<Long> connectionId = connectionService
                .findActive(workspaceId, IntegrationKind.OUTLINE)
                .map(Connection::getId);
            if (connectionId.isEmpty()) {
                return;
            }
            documentEventRepository.save(
                new OutlineDocumentEvent(
                    workspaceId,
                    connectionId.get(),
                    delivery.payloadId(),
                    delivery.event(),
                    delivery.actorId().isBlank() ? null : delivery.actorId(),
                    delivery.occurredAt()
                )
            );
        } catch (RuntimeException e) {
            log.warn(
                "outline.consumer: failed to append document event '{}' for workspaceId={}: {}",
                delivery.event(),
                workspaceId,
                e.toString()
            );
        }
    }

    private Delivery parse(byte[] body) {
        if (body == null || body.length == 0) {
            return new Delivery("", "", "", "", null, null);
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String event = root.path("event").asString("");
            return new Delivery(
                root.path("webhookSubscriptionId").asString(""),
                event,
                root.path("payload").path("id").asString(""),
                root.path("actorId").asString(""),
                parseInstant(root.path("createdAt").asString("")),
                parseModel(event, root.path("payload").path("model"))
            );
        } catch (RuntimeException e) {
            return new Delivery("", "", "", "", null, null);
        }
    }

    /**
     * Parses the delivery's {@code payload.model} into document metadata when the event is a document
     * event carrying a usable model (an id and a collection id). The HMAC covers the whole envelope, so a
     * model that parses is trusted metadata, letting {@link OutlineDocumentSyncScheduler#refreshDocumentNow}
     * skip its own {@code documents.info} round-trip. Returns {@code null} on any parse failure or an
     * incomplete model, so the sync path falls back to a {@code documents.info} call.
     */
    private OutlineDocumentListResponse.@Nullable Meta parseModel(String event, JsonNode modelNode) {
        if (!event.startsWith("documents.") || modelNode == null || modelNode.isMissingNode() || modelNode.isNull()) {
            return null;
        }
        if (modelNode.path("id").asString("").isBlank() || modelNode.path("collectionId").asString("").isBlank()) {
            return null;
        }
        try {
            return objectMapper.treeToValue(modelNode, OutlineDocumentListResponse.Meta.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static @Nullable Instant parseInstant(String value) {
        if (value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * The routing- and audit-relevant fields of one delivery; blanks/null when absent/unparsable.
     * {@code model} is the pre-parsed {@code payload.model} (document events only, {@code null} when
     * absent/incomplete) — see {@link #parseModel}.
     */
    private record Delivery(
        String subscriptionId,
        String event,
        String payloadId,
        String actorId,
        @Nullable Instant occurredAt,
        OutlineDocumentListResponse.@Nullable Meta model
    ) {}
}
