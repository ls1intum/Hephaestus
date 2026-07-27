package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

@WorkspaceAgnostic("Instance model price history is global (app_admin-owned), not tenant-scoped.")
public interface LlmModelPriceRepository extends JpaRepository<LlmModelPrice, Long> {
    /** {@code ux_llm_model_price_open} guarantees at most one open row per model. */
    Optional<LlmModelPrice> findByModelIdAndEffectiveToIsNull(Long modelId);

    List<LlmModelPrice> findByModelIdInAndEffectiveToIsNull(Collection<Long> modelIds);
}
