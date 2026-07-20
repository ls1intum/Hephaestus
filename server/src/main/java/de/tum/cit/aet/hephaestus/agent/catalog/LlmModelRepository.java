package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@WorkspaceAgnostic("Instance LLM model catalog is global (app_admin-owned), not tenant-scoped.")
public interface LlmModelRepository extends JpaRepository<LlmModel, Long> {
    List<LlmModel> findByConnectionId(Long connectionId);

    Optional<LlmModel> findByConnectionIdAndSlug(Long connectionId, String slug);

    /**
     * Ledger price-resolution lookup (#1368 slice 6): the catalog entry a job's frozen
     * {@code (connectionId, upstreamModelId)} pair resolves to. Returns a {@link List} rather than
     * {@link Optional} because {@code upstream_model_id} carries no uniqueness constraint — an admin
     * may register two catalog entries pointing at the same upstream id (different slugs/capabilities).
     * The recorder picks the first enabled match.
     */
    List<LlmModel> findByConnectionIdAndUpstreamModelId(Long connectionId, String upstreamModelId);

    boolean existsByConnectionId(Long connectionId);

    /** Eager-fetches {@code connection} so the admin list view avoids one lazy load per row. */
    @Query("SELECT m FROM LlmModel m JOIN FETCH m.connection ORDER BY m.id")
    List<LlmModel> findAllWithConnection();

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
