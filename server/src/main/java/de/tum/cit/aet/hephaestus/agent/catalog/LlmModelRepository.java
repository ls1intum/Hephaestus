package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

@WorkspaceAgnostic("Instance LLM model catalog is global (app_admin-owned), not tenant-scoped.")
public interface LlmModelRepository extends JpaRepository<LlmModel, Long> {
    List<LlmModel> findByConnectionId(Long connectionId);

    Optional<LlmModel> findByConnectionIdAndSlug(Long connectionId, String slug);

    boolean existsByConnectionId(Long connectionId);

    /** Eager-fetches {@code connection} so the admin list view avoids one lazy load per row. */
    @Query("SELECT m FROM LlmModel m JOIN FETCH m.connection ORDER BY m.id")
    List<LlmModel> findAllWithConnection();
}
