package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

@WorkspaceAgnostic("Instance LLM connection catalog is global (app_admin-owned), not tenant-scoped.")
public interface LlmConnectionRepository extends JpaRepository<LlmConnection, Long> {
    Optional<LlmConnection> findBySlug(String slug);
}
