package de.tum.in.www1.hephaestus.audit;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write-side helper for {@link DeletionAudit}. Callers invoke {@link #record} immediately
 * BEFORE the entity delete fires, in a {@code REQUIRES_NEW} transaction so the audit row
 * survives even when the actual delete rolls back. (A delete that throws AFTER the audit
 * write produces a "deletion attempted but failed" row — visible in an audit search — which
 * is preferable to a successful delete with no audit trail.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeletionAuditService {

    private final DeletionAuditRepository repository;

    /**
     * Persist a single audit row in a fresh transaction.
     *
     * @param entityType   logical kind of the entity being deleted
     * @param entityId     stringified id of the deleted entity
     * @param workspaceId  scoping workspace (nullable for global entities like USER)
     * @param actorUserId  user that initiated the delete; null for system-driven sweeps
     * @param reason       optional human-readable explanation (e.g. "user-initiated", "ttl-eviction")
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
        DeletionAudit.EntityType entityType,
        String entityId,
        @Nullable Long workspaceId,
        @Nullable Long actorUserId,
        @Nullable String reason
    ) {
        DeletionAudit row = new DeletionAudit();
        row.setId(UUID.randomUUID());
        row.setEntityType(entityType);
        row.setEntityId(entityId);
        row.setWorkspaceId(workspaceId);
        row.setActorUserId(actorUserId);
        row.setReason(reason);
        repository.save(row);
        log.debug(
            "deletion_audit recorded: type={} entityId={} workspaceId={} actorUserId={} reason={}",
            entityType,
            entityId,
            workspaceId,
            actorUserId,
            reason
        );
    }
}
