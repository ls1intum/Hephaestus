package de.tum.cit.aet.hephaestus.integration.core.connection.api;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionAudit;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import java.time.Instant;
import org.springframework.lang.Nullable;

/**
 * Audit-log entry returned by {@code GET /api/v1/workspaces/{workspaceId}/connections/{id}/audit}.
 *
 * <p>Lean projection of {@link ConnectionAudit} — the entity carries a back-reference
 * to {@link de.tum.cit.aet.hephaestus.integration.core.connection.Connection} that we don't
 * want to serialize on every response.
 */
public record ConnectionAuditEntryDTO(
    String eventType,
    @Nullable IntegrationState fromState,
    @Nullable IntegrationState toState,
    String actorKind,
    @Nullable String actorRef,
    @Nullable String correlationId,
    Instant occurredAt
) {
    public static ConnectionAuditEntryDTO from(ConnectionAudit audit) {
        return new ConnectionAuditEntryDTO(
            audit.getEventType(),
            audit.getFromState(),
            audit.getToState(),
            audit.getActorKind(),
            audit.getActorRef(),
            audit.getCorrelationId(),
            audit.getOccurredAt()
        );
    }
}
