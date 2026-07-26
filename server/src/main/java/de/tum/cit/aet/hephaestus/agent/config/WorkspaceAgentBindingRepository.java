package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Access to the per-purpose {@link WorkspaceAgentBinding}s of a workspace. */
public interface WorkspaceAgentBindingRepository extends JpaRepository<WorkspaceAgentBinding, Long> {
    List<WorkspaceAgentBinding> findByWorkspaceId(Long workspaceId);

    /**
     * Every binding of a workspace with its catalog model and that model's connection already
     * fetched. The listing endpoint reports each binding's readiness, which resolves model →
     * connection AFTER the loading transaction has closed (readiness must be judged outside a
     * transaction — resolve() signals a revoked model by throwing, which would mark a shared
     * transaction rollback-only). Without the fetch the detached rows would throw
     * LazyInitializationException instead of answering.
     */
    @Query(
        "SELECT b FROM WorkspaceAgentBinding b " +
            "LEFT JOIN FETCH b.instanceModel im LEFT JOIN FETCH im.connection " +
            "LEFT JOIN FETCH b.workspaceModel wm LEFT JOIN FETCH wm.connection " +
            "WHERE b.workspace.id = :workspaceId"
    )
    List<WorkspaceAgentBinding> findByWorkspaceIdWithModels(@Param("workspaceId") Long workspaceId);

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

    /**
     * Is ANY workspace bound to this instance-catalog model? Asked by the instance admin before
     * deleting the model, so it is deliberately cross-tenant: a binding in a workspace the operator
     * has never heard of is exactly the one that must block the delete. Returns a boolean, never a
     * row, so no tenant data crosses the boundary.
     */
    @WorkspaceAgnostic("Instance-admin delete guard: a catalog model is in use if ANY workspace binds it")
    boolean existsByInstanceModelId(Long instanceModelId);

    boolean existsByWorkspaceModelIdAndWorkspaceId(Long workspaceModelId, Long workspaceId);
}
