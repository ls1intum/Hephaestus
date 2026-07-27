package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@WorkspaceAgnostic("Instance LLM model catalog is global (app_admin-owned), not tenant-scoped.")
public interface LlmModelRepository extends JpaRepository<LlmModel, Long> {
    Optional<LlmModel> findByConnectionIdAndSlug(Long connectionId, String slug);

    /** Create-path conflict guard for {@code ux_llm_model_connection_upstream}. */
    boolean existsByConnectionIdAndUpstreamModelId(Long connectionId, String upstreamModelId);

    boolean existsByConnectionId(Long connectionId);

    /** Eager-fetches {@code connection} so the admin list view avoids one lazy load per row. */
    @Query("SELECT m FROM LlmModel m JOIN FETCH m.connection ORDER BY m.id")
    List<LlmModel> findAllWithConnection();

    /**
     * Eager-fetches {@code connection} for a single model — needed wherever the loaded entity outlives
     * the read transaction before being converted to {@link LlmModelDTO} (which reads
     * {@code connection.displayName}). Without this, {@code LlmModelAdminController}'s GET/update/price/
     * sharing endpoints throw {@code LazyInitializationException} once OSIV is off, since the plain
     * lazy {@code connection} proxy is never touched inside the owning {@code @Transactional} method.
     */
    @Query("SELECT m FROM LlmModel m JOIN FETCH m.connection WHERE m.id = :id")
    Optional<LlmModel> findByIdWithConnection(@Param("id") Long id);

    /** Serializes activation and repricing so an enabled model can never end a race unpriced. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM LlmModel m JOIN FETCH m.connection WHERE m.id = :id")
    Optional<LlmModel> findByIdForUpdate(@Param("id") Long id);

    /**
     * Available-models projection: instance models usable by a given workspace — active, on an active
     * connection, and either shared with every workspace ({@code PUBLIC}) or explicitly granted to this
     * one. Both {@code llm_model} and {@code llm_model_workspace_grant} are global tables, so the
     * {@code :workspaceId} parameter is a plain filter, not a tenancy predicate.
     */
    @Query(
        "SELECT m FROM LlmModel m JOIN FETCH m.connection c " +
            "WHERE m.enabled = true AND c.enabled = true " +
            "AND (m.visibility = de.tum.cit.aet.hephaestus.agent.catalog.ModelVisibility.PUBLIC " +
            "OR EXISTS (SELECT 1 FROM LlmModelWorkspaceGrant g " +
            "WHERE g.id.modelId = m.id AND g.id.workspaceId = :workspaceId)) " +
            "ORDER BY m.id"
    )
    List<LlmModel> findVisibleEnabledModels(@Param("workspaceId") Long workspaceId);
}
