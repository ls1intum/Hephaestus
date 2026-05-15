package de.tum.in.www1.hephaestus.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeletionAuditRepository extends JpaRepository<DeletionAudit, UUID> {
    /**
     * Look up every audit row for a specific entity. The {@code (entity_type, entity_id)} pair
     * has a covering b-tree index; this is the GDPR Art. 15/30 disclosure path.
     */
    List<DeletionAudit> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
        DeletionAudit.EntityType entityType,
        String entityId
    );
}
