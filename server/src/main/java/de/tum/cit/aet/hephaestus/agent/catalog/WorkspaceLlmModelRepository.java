package de.tum.cit.aet.hephaestus.agent.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceLlmModelRepository extends JpaRepository<WorkspaceLlmModel, Long> {
    List<WorkspaceLlmModel> findByWorkspaceId(Long workspaceId);

    /**
     * Like {@link #findByWorkspaceId}, but eager-fetches {@code connection} — needed for the admin list
     * view, which converts every row straight to {@link WorkspaceLlmModelDTO} (reads
     * {@code connection.displayName}) outside the owning transaction. See
     * {@link #findByIdAndWorkspaceIdWithConnection} for the single-row equivalent.
     */
    @Query(
        "SELECT m FROM WorkspaceLlmModel m JOIN FETCH m.connection WHERE m.workspace.id = :workspaceId ORDER BY m.id"
    )
    List<WorkspaceLlmModel> findByWorkspaceIdWithConnection(@Param("workspaceId") Long workspaceId);

    List<WorkspaceLlmModel> findByConnectionId(Long connectionId);

    /**
     * Ledger price-resolution lookup (#1368 slice 6): mirrors {@code LlmModelRepository}'s method of
     * the same name — see its Javadoc for why this returns a {@link List} rather than an
     * {@link Optional}.
     */
    List<WorkspaceLlmModel> findByConnectionIdAndUpstreamModelId(Long connectionId, String upstreamModelId);

    /** Create-path conflict guard for {@code ux_ws_llm_model_connection_upstream} (#1368). */
    boolean existsByConnectionIdAndUpstreamModelId(Long connectionId, String upstreamModelId);

    /** Update-path conflict guard: same uniqueness, excluding the row being updated (#1368). */
    boolean existsByConnectionIdAndUpstreamModelIdAndIdNot(Long connectionId, String upstreamModelId, Long id);

    Optional<WorkspaceLlmModel> findByWorkspaceIdAndSlug(Long workspaceId, String slug);

    /** Tenancy-safe lookup for a client-supplied id (path variable) — never trust a bare {@code findById}. */
    Optional<WorkspaceLlmModel> findByIdAndWorkspaceId(Long id, Long workspaceId);

    /**
     * Like {@link #findByIdAndWorkspaceId}, but eager-fetches {@code connection} — needed wherever the
     * loaded entity outlives the read transaction before being converted to {@link WorkspaceLlmModelDTO}
     * (which reads {@code connection.displayName}). Without this, {@code WorkspaceLlmModelController}'s
     * GET/PATCH endpoints throw {@code LazyInitializationException} once OSIV is off, since the plain
     * lazy {@code connection} proxy is never touched inside the owning {@code @Transactional} method.
     */
    @Query(
        "SELECT m FROM WorkspaceLlmModel m JOIN FETCH m.connection WHERE m.id = :id AND m.workspace.id = :workspaceId"
    )
    Optional<WorkspaceLlmModel> findByIdAndWorkspaceIdWithConnection(
        @Param("id") Long id,
        @Param("workspaceId") Long workspaceId
    );

    /** Delete-conflict guard for {@code WorkspaceLlmConnectionService#delete}, scoped to the owning workspace. */
    boolean existsByConnectionIdAndWorkspaceId(Long connectionId, Long workspaceId);

    /** Available-models projection: this workspace's own usable (active, active-connection) BYO models. */
    @Query(
        "SELECT m FROM WorkspaceLlmModel m JOIN FETCH m.connection c " +
            "WHERE m.workspace.id = :workspaceId AND m.enabled = true AND c.enabled = true ORDER BY m.id"
    )
    List<WorkspaceLlmModel> findEnabledWithEnabledConnection(@Param("workspaceId") Long workspaceId);
}
