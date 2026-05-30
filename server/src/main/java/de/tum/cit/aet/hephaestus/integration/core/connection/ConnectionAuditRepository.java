package de.tum.cit.aet.hephaestus.integration.core.connection;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ConnectionAuditRepository extends JpaRepository<ConnectionAudit, Long> {
    List<ConnectionAudit> findByConnectionIdOrderByOccurredAtDesc(long connectionId);

    /**
     * Workspace-scoped audit lookup. Retained as the tenancy-compliant query surface required by
     * {@code MultiTenancyArchitectureTest.repositoriesHaveWorkspaceScopedAlternatives}: connection
     * audit rows are workspace-scoped data, so the repository must expose a way to read them bounded
     * to a single workspace (e.g. a future workspace-wide audit view), not only by connection id.
     */
    @Query(
        "SELECT a FROM ConnectionAudit a " +
            "WHERE a.connection.workspace.id = :workspaceId " +
            "ORDER BY a.occurredAt DESC"
    )
    List<ConnectionAudit> findByWorkspaceId(@Param("workspaceId") long workspaceId);
}
