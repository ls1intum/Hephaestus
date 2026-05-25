package de.tum.cit.aet.hephaestus.integration.registry;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationState;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates Connection lookups + state transitions.
 *
 * <p>Transitions are guarded by {@link IntegrationState#canTransitionTo}; same-state
 * calls are idempotent no-ops. Audit-row INSERT carries {@code correlation_id} with a
 * uniqueness constraint, so webhook redelivery never causes state flap.
 */
@Service
public class ConnectionService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionService.class);

    private final ConnectionRepository connectionRepository;
    private final ConnectionAuditRepository auditRepository;

    public ConnectionService(ConnectionRepository connectionRepository,
                             ConnectionAuditRepository auditRepository) {
        this.connectionRepository = connectionRepository;
        this.auditRepository = auditRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Connection> findActive(long workspaceId, IntegrationKind kind) {
        return connectionRepository.findFirstByWorkspaceIdAndKindAndStateOrderByCreatedAtDesc(
            workspaceId, kind, IntegrationState.ACTIVE);
    }

    @Transactional(readOnly = true)
    public Connection requireActive(long workspaceId, IntegrationKind kind) {
        return findActive(workspaceId, kind).orElseThrow(() ->
            new NoSuchElementException("No ACTIVE Connection for workspace=" + workspaceId + " kind=" + kind));
    }

    @Transactional(readOnly = true)
    public Optional<Connection> findByRef(IntegrationRef ref) {
        if (ref.instanceKey() == null) return Optional.empty();
        return connectionRepository.findByWorkspaceIdAndKindAndInstanceKey(
            ref.workspaceId(), ref.kind(), ref.instanceKey());
    }

    /**
     * Transition the Connection to {@code req.next()}. Idempotent: same-state is a no-op
     * (no audit row); invalid transitions throw; webhook redelivery is silenced via
     * the {@code uq_connection_audit_idempotency} constraint.
     *
     * <p>Side effect: transitioning to {@link IntegrationState#UNINSTALLED} clears the
     * credential ciphertext + algorithm tag inside the same transaction. The
     * {@code IntegrationState.UNINSTALLED} javadoc contract ("credentials cleared") is
     * enforced here — not in the entity, not in a downstream listener — so the purge is
     * atomic with the state change.
     */
    @Transactional
    public Connection transition(Connection connection, TransitionRequest req) {
        IntegrationState current = connection.getState();
        if (current == req.next()) {
            log.debug("Connection {} already in state {}, no-op", connection.getId(), req.next());
            return connection;
        }
        if (!current.canTransitionTo(req.next())) {
            throw new IllegalStateException(
                "Illegal transition for connection " + connection.getId() + ": " + current + " → " + req.next()
            );
        }
        ConnectionAudit audit = new ConnectionAudit(
            connection, req.eventType(), current, req.next(),
            req.actorKind(), req.actorRef(), req.correlationId(), req.detail()
        );
        try {
            auditRepository.save(audit);
        } catch (DataIntegrityViolationException e) {
            log.info("Idempotent {} for connection={} corr={} (already recorded)", req.eventType(),
                connection.getId(), req.correlationId());
            return connection;
        }
        connection.setState(req.next());
        connection.setStateReason(req.detail());
        if (req.next() == IntegrationState.UNINSTALLED && connection.getCredentialsEncrypted() != null) {
            connection.setCredentialsEncrypted(null);
            connection.setCredentialsAlg(null);
            log.info("Purged credentials on UNINSTALLED transition for connection={}", connection.getId());
        }
        return connectionRepository.save(connection);
    }

    /** Parameter object for {@link #transition} — collapses 7 params to one record. */
    public record TransitionRequest(
        IntegrationState next,
        String eventType,
        String actorKind,
        @Nullable String actorRef,
        @Nullable String correlationId,
        @Nullable String detail
    ) {
    }
}
