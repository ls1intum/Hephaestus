package de.tum.cit.aet.hephaestus.agent.catalog;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceLlmConnectionRepository extends JpaRepository<WorkspaceLlmConnection, Long> {
    List<WorkspaceLlmConnection> findByWorkspaceId(Long workspaceId);

    Optional<WorkspaceLlmConnection> findByWorkspaceIdAndSlug(Long workspaceId, String slug);

    /** Tenancy-safe lookup for a client-supplied id (path variable) — never trust a bare {@code findById}. */
    Optional<WorkspaceLlmConnection> findByIdAndWorkspaceId(Long id, Long workspaceId);

    /**
     * A partial PATCH writes every column back, so without this lock a concurrent PATCH can undo one
     * that cleared the API key, leaving live a credential the admin believes they deleted.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM WorkspaceLlmConnection c WHERE c.id = :id AND c.workspace.id = :workspaceId")
    Optional<WorkspaceLlmConnection> findByIdAndWorkspaceIdForUpdate(
        @Param("id") Long id,
        @Param("workspaceId") Long workspaceId
    );

    @Query(
        "SELECT new de.tum.cit.aet.hephaestus.agent.catalog.LlmProbeTarget(c.baseUrl, c.authMode, c.apiKey) " +
            "FROM WorkspaceLlmConnection c WHERE c.id = :id AND c.workspace.id = :workspaceId"
    )
    Optional<LlmProbeTarget> findProbeTargetByIdAndWorkspaceId(
        @Param("id") Long id,
        @Param("workspaceId") Long workspaceId
    );
}
