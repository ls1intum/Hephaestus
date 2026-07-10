package de.tum.cit.aet.hephaestus.integration.outline.lifecycle;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.events.ConnectionLifecycleEvent;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.outline.sync.OutlineDocumentSyncScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to a Connection crossing the ACTIVE boundary (published by {@code ConnectionService.transition}):
 * activation registers the change-notification subscription and kicks an initial recency sync the moment
 * an admin connects — instead of waiting for the next scheduler tick — and deactivation tears the
 * upstream subscription down.
 *
 * <p>Runs async AFTER_COMMIT (model: {@code CommitEnrichmentEventListener}), so the connect transaction
 * never waits on Outline and a failure here can only cost freshness — every path is wrapped and logged,
 * never rethrown. Sync work is routed through {@link OutlineDocumentSyncScheduler} because the async
 * event thread carries no tenancy-bypass scope of its own.
 */
@Component
@ConditionalOnServerRole
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
public class OutlineConnectionStateListener {

    private static final Logger log = LoggerFactory.getLogger(OutlineConnectionStateListener.class);

    private final OutlineWebhookRegistrar webhookRegistrar;
    private final OutlineDocumentSyncScheduler syncScheduler;

    public OutlineConnectionStateListener(
        OutlineWebhookRegistrar webhookRegistrar,
        OutlineDocumentSyncScheduler syncScheduler
    ) {
        this.webhookRegistrar = webhookRegistrar;
        this.syncScheduler = syncScheduler;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActivated(ConnectionLifecycleEvent.Activated event) {
        if (event.kind() != IntegrationKind.OUTLINE) {
            return;
        }
        try {
            webhookRegistrar.ensureSubscription(event.workspaceId());
            // Initial recency sync — a harmless no-op while no collections are registered yet.
            syncScheduler.syncWorkspaceNow(event.workspaceId());
        } catch (RuntimeException e) {
            // Async listener: rethrowing reaches nobody. The periodic reconcile is the safety net.
            log.warn(
                "outline.lifecycle: connect-time setup failed for workspaceId={}: {}",
                event.workspaceId(),
                e.toString()
            );
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeactivated(ConnectionLifecycleEvent.Deactivated event) {
        if (event.kind() != IntegrationKind.OUTLINE) {
            return;
        }
        try {
            // By connection id: the row already left ACTIVE, so the ACTIVE-scoped lookup cannot see it.
            webhookRegistrar.deregister(event.workspaceId(), event.connectionId());
        } catch (RuntimeException e) {
            log.warn(
                "outline.lifecycle: deactivation teardown failed for connectionId={}: {}",
                event.connectionId(),
                e.toString()
            );
        }
    }
}
