package de.tum.cit.aet.hephaestus.integration.registry;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ConnectionAuditRepository extends JpaRepository<ConnectionAudit, Long> {

    List<ConnectionAudit> findByConnectionIdOrderByOccurredAtDesc(long connectionId);

    boolean existsByConnectionIdAndEventTypeAndCorrelationId(long connectionId, String eventType, String correlationId);

    @Query(
        "SELECT a FROM ConnectionAudit a "
            + "WHERE a.connection.workspace.id = :workspaceId "
            + "ORDER BY a.occurredAt DESC"
    )
    List<ConnectionAudit> findByWorkspaceId(@Param("workspaceId") long workspaceId);
}
