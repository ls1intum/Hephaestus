package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@WorkspaceAgnostic("Instance LLM connection catalog is global (app_admin-owned), not tenant-scoped.")
public interface LlmConnectionRepository extends JpaRepository<LlmConnection, Long> {
    Optional<LlmConnection> findBySlug(String slug);

    /**
     * Immutable probe target, loaded so the "test connection" call can leave the transaction behind
     * before it makes a network request. See {@link LlmConnectionProbeService}.
     */
    @Query(
        "SELECT new de.tum.cit.aet.hephaestus.agent.catalog.LlmProbeTarget(c.baseUrl, c.authMode, c.apiKey) " +
            "FROM LlmConnection c WHERE c.id = :id"
    )
    Optional<LlmProbeTarget> findProbeTargetById(@Param("id") Long id);
}
