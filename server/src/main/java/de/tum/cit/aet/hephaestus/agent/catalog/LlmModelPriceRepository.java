package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

@WorkspaceAgnostic("Instance model price history is global (app_admin-owned), not tenant-scoped.")
public interface LlmModelPriceRepository extends JpaRepository<LlmModelPrice, Long> {
    List<LlmModelPrice> findByModelId(Long modelId);
}
