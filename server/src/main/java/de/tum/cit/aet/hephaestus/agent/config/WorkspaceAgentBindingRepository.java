package de.tum.cit.aet.hephaestus.agent.config;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Access to the per-purpose {@link WorkspaceAgentBinding}s of a workspace (#1368). */
public interface WorkspaceAgentBindingRepository extends JpaRepository<WorkspaceAgentBinding, Long> {
    List<WorkspaceAgentBinding> findByWorkspaceId(Long workspaceId);

    Optional<WorkspaceAgentBinding> findByWorkspaceIdAndPurpose(Long workspaceId, AgentPurpose purpose);

    /**
     * The binding with its catalog model AND that model's connection already fetched, for callers that
     * run OUTSIDE a transaction. Both model associations are {@code LAZY}, and resolving a binding
     * walks model → connection, so a plain lookup followed by {@code LlmModelResolver.resolve} throws
     * {@code LazyInitializationException} once the session closes with the row. Fetching the whole
     * graph in one query lets the readiness check stay non-transactional (which it must: resolve()
     * signals revocation with an exception, and catching that inside a shared transaction would still
     * mark it rollback-only).
     */
    @Query(
        "SELECT b FROM WorkspaceAgentBinding b " +
            "LEFT JOIN FETCH b.instanceModel im LEFT JOIN FETCH im.connection " +
            "LEFT JOIN FETCH b.workspaceModel wm LEFT JOIN FETCH wm.connection " +
            "WHERE b.workspace.id = :workspaceId AND b.purpose = :purpose"
    )
    Optional<WorkspaceAgentBinding> findByWorkspaceIdAndPurposeWithModels(
        @Param("workspaceId") Long workspaceId,
        @Param("purpose") AgentPurpose purpose
    );

    /** Row-lock the binding for admission's re-resolve/re-price, mirroring the model row lock order. */
    @Query("SELECT b FROM WorkspaceAgentBinding b WHERE b.workspace.id = :workspaceId AND b.purpose = :purpose")
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<WorkspaceAgentBinding> findByWorkspaceIdAndPurposeForUpdate(
        @Param("workspaceId") Long workspaceId,
        @Param("purpose") AgentPurpose purpose
    );

    boolean existsByInstanceModelId(Long instanceModelId);

    boolean existsByWorkspaceModelIdAndWorkspaceId(Long workspaceModelId, Long workspaceId);
}
