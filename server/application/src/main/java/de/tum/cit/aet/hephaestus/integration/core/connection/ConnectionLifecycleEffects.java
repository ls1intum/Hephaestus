package de.tum.cit.aet.hephaestus.integration.core.connection;

import de.tum.cit.aet.hephaestus.integration.core.events.ConnectionLifecycleEvent;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionStrategy;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

final class ConnectionLifecycleEffects {
    private static final Logger log = LoggerFactory.getLogger(ConnectionLifecycleEffects.class);
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate providerTeardownTemplate;

    ConnectionLifecycleEffects(
            ApplicationEventPublisher eventPublisher, PlatformTransactionManager transactionManager) {
        this.eventPublisher = eventPublisher;
        this.providerTeardownTemplate = new TransactionTemplate(transactionManager);
        this.providerTeardownTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    void revoke(Runnable revoke) {
        providerTeardownTemplate.executeWithoutResult(status -> revoke.run());
    }

    /**
     * The teardown is prepared with this transaction suspended, so a failed credential read cannot
     * mark the disconnect rollback-only; those reads see the committed row, credentials included,
     * since the lifecycle lock blocks writers, not readers.
     */
    void eraseAndTearDownProviderAfterCommit(Connection connection, ConnectionStrategy strategy) {
        IntegrationRef ref = new IntegrationRef(
                connection.getKind(),
                connection.getWorkspace().getId(),
                connection.getInstanceKey(),
                connection.getId());
        strategy.eraseLocalData(ref);
        Optional<Runnable> teardown;
        try {
            teardown = Objects.requireNonNull(
                    providerTeardownTemplate.execute(status -> strategy.prepareProviderTeardown(ref)));
        } catch (RuntimeException e) {
            log.warn(
                    "Provider teardown could not be prepared on disconnect of connection={} kind={}: {} — disconnecting without it",
                    ref.connectionId(),
                    ref.kind(),
                    e.toString());
            return;
        }
        teardown.ifPresent(call ->
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            call.run();
                        } catch (RuntimeException e) {
                            log.warn(
                                    "Provider teardown failed after disconnect of connection={} kind={}: {} — local data already erased",
                                    ref.connectionId(),
                                    ref.kind(),
                                    e.toString());
                        }
                    }
                }));
    }

    /**
     * Publishes an ACTIVE-boundary crossing to provider adapters inside the transition transaction.
     * A rollback drops the event, and the state machine calls this only after an actual state change.
     */
    void publishLifecycleEvent(Connection connection, IntegrationState previous, IntegrationState next) {
        long workspaceId = connection.getWorkspace().getId();
        if (next == IntegrationState.ACTIVE) {
            eventPublisher.publishEvent(
                    new ConnectionLifecycleEvent.Activated(connection.getId(), workspaceId, connection.getKind()));
        } else if (previous == IntegrationState.ACTIVE) {
            eventPublisher.publishEvent(
                    new ConnectionLifecycleEvent.Deactivated(connection.getId(), workspaceId, connection.getKind()));
        }
    }
}
