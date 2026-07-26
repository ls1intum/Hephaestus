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
     * Locking variant for the PATCH path (mirrors {@code WorkspaceLlmModelRepository}). A partial
     * PATCH loads the row, changes a subset, and lets Hibernate write every column back, so two
     * workspace admins patching at once would each revert the other's field — including the case that
     * matters: a PATCH that clears the API key being undone by a concurrent PATCH that only toggles
     * {@code enabled}, leaving a credential the admin believes they deleted.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM WorkspaceLlmConnection c WHERE c.id = :id AND c.workspace.id = :workspaceId")
    Optional<WorkspaceLlmConnection> findByIdAndWorkspaceIdForUpdate(
        @Param("id") Long id,
        @Param("workspaceId") Long workspaceId
    );

    /**
     * Immutable probe target, loaded so the "test connection" call can leave the transaction behind
     * before it makes a network request. See {@code LlmConnectionProbeService}.
     */
    @Query(
        "SELECT new de.tum.cit.aet.hephaestus.agent.catalog.LlmProbeTarget(c.baseUrl, c.authMode, c.apiKey) " +
            "FROM WorkspaceLlmConnection c WHERE c.id = :id AND c.workspace.id = :workspaceId"
    )
    Optional<LlmProbeTarget> findProbeTargetByIdAndWorkspaceId(
        @Param("id") Long id,
        @Param("workspaceId") Long workspaceId
    );
}
