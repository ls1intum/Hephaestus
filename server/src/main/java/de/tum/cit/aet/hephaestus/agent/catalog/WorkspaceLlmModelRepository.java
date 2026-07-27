package de.tum.cit.aet.hephaestus.agent.catalog;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceLlmModelRepository extends JpaRepository<WorkspaceLlmModel, Long> {
    List<WorkspaceLlmModel> findByWorkspaceId(Long workspaceId);

    @Query(
        "SELECT m FROM WorkspaceLlmModel m JOIN FETCH m.connection WHERE m.workspace.id = :workspaceId ORDER BY m.id"
    )
    List<WorkspaceLlmModel> findByWorkspaceIdWithConnection(@Param("workspaceId") Long workspaceId);

    boolean existsByConnectionIdAndUpstreamModelId(Long connectionId, String upstreamModelId);

    Optional<WorkspaceLlmModel> findByWorkspaceIdAndSlug(Long workspaceId, String slug);

    /** Tenancy-safe lookup for a client-supplied id (path variable) — never trust a bare {@code findById}. */
    Optional<WorkspaceLlmModel> findByIdAndWorkspaceId(Long id, Long workspaceId);

    /**
     * {@code connection} is fetched eagerly because callers map the entity to a DTO after the
     * transaction closes and OSIV is off.
     */
    @Query(
        "SELECT m FROM WorkspaceLlmModel m JOIN FETCH m.connection WHERE m.id = :id AND m.workspace.id = :workspaceId"
    )
    Optional<WorkspaceLlmModel> findByIdAndWorkspaceIdWithConnection(
        @Param("id") Long id,
        @Param("workspaceId") Long workspaceId
    );

    /** Serializes the workspace model's combined activation and price replacement. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "SELECT m FROM WorkspaceLlmModel m JOIN FETCH m.connection " +
            "WHERE m.id = :id AND m.workspace.id = :workspaceId"
    )
    Optional<WorkspaceLlmModel> findByIdAndWorkspaceIdForUpdate(
        @Param("id") Long id,
        @Param("workspaceId") Long workspaceId
    );

    boolean existsByConnectionIdAndWorkspaceId(Long connectionId, Long workspaceId);

    @Query(
        "SELECT m FROM WorkspaceLlmModel m JOIN FETCH m.connection c " +
            "WHERE m.workspace.id = :workspaceId AND m.enabled = true AND c.enabled = true ORDER BY m.id"
    )
    List<WorkspaceLlmModel> findEnabledWithEnabledConnection(@Param("workspaceId") Long workspaceId);
}
