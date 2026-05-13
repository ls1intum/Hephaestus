package de.tum.in.www1.hephaestus.agent.mentor.pricing;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Pricing lookup is a global registry keyed by model id; there is no workspace dimension by
 * design (vendors charge per model, not per tenant). Annotated {@link WorkspaceAgnostic} so
 * {@code MultiTenancyArchitectureTest} skips the workspace-scoping requirement.
 */
@Repository
@WorkspaceAgnostic("Pricing registry is global — vendors charge per model, not per workspace")
public interface ModelPricingRepository extends JpaRepository<ModelPricing, String> {
    /**
     * Find pricing for the given model. We currently treat the table as a single-row-per-model
     * registry; {@code validFrom / validTo} columns exist for future price-history rollovers,
     * but until that tooling lands we always read the single row by primary key.
     */
    Optional<ModelPricing> findByModelId(String modelId);
}
