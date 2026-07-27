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

    boolean existsByConnectionIdAndUpstreamModelId(Long connectionId, String upstreamModelId);

    boolean existsByConnectionId(Long connectionId);

    @Query("SELECT m FROM LlmModel m JOIN FETCH m.connection ORDER BY m.id")
    List<LlmModel> findAllWithConnection();

    /**
     * {@code connection} is fetched eagerly because callers map the entity to a DTO after the
     * transaction closes and OSIV is off.
     */
    @Query("SELECT m FROM LlmModel m JOIN FETCH m.connection WHERE m.id = :id")
    Optional<LlmModel> findByIdWithConnection(@Param("id") Long id);

    /** Serializes activation and repricing so an enabled model can never end a race unpriced. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM LlmModel m JOIN FETCH m.connection WHERE m.id = :id")
    Optional<LlmModel> findByIdForUpdate(@Param("id") Long id);

    /**
     * Both {@code llm_model} and {@code llm_model_workspace_grant} are global tables, so
     * {@code :workspaceId} is a plain filter here, not a tenancy predicate.
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
