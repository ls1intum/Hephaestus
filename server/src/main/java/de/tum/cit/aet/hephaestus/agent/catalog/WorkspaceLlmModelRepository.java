package de.tum.cit.aet.hephaestus.agent.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceLlmModelRepository extends JpaRepository<WorkspaceLlmModel, Long> {
    List<WorkspaceLlmModel> findByWorkspaceId(Long workspaceId);

    List<WorkspaceLlmModel> findByConnectionId(Long connectionId);

    /**
     * Ledger price-resolution lookup (#1368 slice 6): mirrors {@code LlmModelRepository}'s method of
     * the same name — see its Javadoc for why this returns a {@link List} rather than an
     * {@link Optional}.
     */
    List<WorkspaceLlmModel> findByConnectionIdAndUpstreamModelId(Long connectionId, String upstreamModelId);

    Optional<WorkspaceLlmModel> findByWorkspaceIdAndSlug(Long workspaceId, String slug);

    /** Tenancy-safe lookup for a client-supplied id (path variable) — never trust a bare {@code findById}. */
    Optional<WorkspaceLlmModel> findByIdAndWorkspaceId(Long id, Long workspaceId);

    /** Delete-conflict guard for {@code WorkspaceLlmConnectionService#delete}, scoped to the owning workspace. */
    boolean existsByConnectionIdAndWorkspaceId(Long connectionId, Long workspaceId);

    /** Available-models projection: this workspace's own usable (active, active-connection) BYO models. */
    @Query(
        "SELECT m FROM WorkspaceLlmModel m JOIN FETCH m.connection c " +
            "WHERE m.workspace.id = :workspaceId AND m.enabled = true AND c.enabled = true ORDER BY m.id"
    )
    List<WorkspaceLlmModel> findEnabledWithEnabledConnection(@Param("workspaceId") Long workspaceId);
}
