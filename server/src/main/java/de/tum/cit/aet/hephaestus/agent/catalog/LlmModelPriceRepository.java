package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

@WorkspaceAgnostic("Instance model price history is global (app_admin-owned), not tenant-scoped.")
public interface LlmModelPriceRepository extends JpaRepository<LlmModelPrice, Long> {
    List<LlmModelPrice> findByModelId(Long modelId);

    /** The one open (temporally-current) price row for a model, if any — {@code ux_llm_model_price_open}. */
    Optional<LlmModelPrice> findByModelIdAndEffectiveToIsNull(Long modelId);

    /** Batched current-price lookup for the admin list view. */
    List<LlmModelPrice> findByModelIdInAndEffectiveToIsNull(Collection<Long> modelIds);
}
