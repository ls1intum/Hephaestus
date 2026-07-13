package de.tum.cit.aet.hephaestus.integration.slack.lifecycle;

import de.tum.cit.aet.hephaestus.integration.core.consumer.IntegrationNatsConsumer;
import de.tum.cit.aet.hephaestus.integration.core.events.ConnectionLifecycleEvent;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Brings the workspace's NATS scope consumer in line with its Slack connection state: on the ACTIVE
 * boundary crossing (either direction) the scope's filter subjects are rebuilt so the per-workspace
 * {@code slack.<team>.>} filter is added or dropped. {@code startConsumingScope} no-ops when the scope
 * already has consumers and {@code updateScopeConsumer} no-ops when it has none yet — calling both
 * reconciles every state.
 *
 * <p>Runs async AFTER_COMMIT so the connect/disconnect transaction never waits on NATS; a failure here
 * only costs ingest freshness until the next scope reconcile, so it is logged, never rethrown.
 */
@Component
public class SlackScopeConsumerReconciler {

    private static final Logger log = LoggerFactory.getLogger(SlackScopeConsumerReconciler.class);

    /** Absent when {@code hephaestus.sync.nats.enabled=false} — every use goes through {@code ifAvailable}. */
    private final ObjectProvider<IntegrationNatsConsumer> natsConsumer;

    public SlackScopeConsumerReconciler(ObjectProvider<IntegrationNatsConsumer> natsConsumer) {
        this.natsConsumer = natsConsumer;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActivated(ConnectionLifecycleEvent.Activated event) {
        if (event.kind() != IntegrationKind.SLACK) {
            return;
        }
        reconcile(event.workspaceId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeactivated(ConnectionLifecycleEvent.Deactivated event) {
        if (event.kind() != IntegrationKind.SLACK) {
            return;
        }
        reconcile(event.workspaceId());
    }

    private void reconcile(long workspaceId) {
        try {
            natsConsumer.ifAvailable(consumer -> {
                consumer.startConsumingScope(workspaceId);
                consumer.updateScopeConsumer(workspaceId);
            });
        } catch (RuntimeException e) {
            log.warn(
                "slack.lifecycle: scope-consumer reconcile failed for workspaceId={}: {}",
                workspaceId,
                e.toString()
            );
        }
    }
}
