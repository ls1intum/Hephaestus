package de.tum.in.www1.hephaestus.agent.pricing;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Pricing lookup is a global registry keyed by model id; there is no workspace dimension by
 * design (vendors charge per model, not per tenant). Annotated {@link WorkspaceAgnostic} so
 * {@code MultiTenancyArchitectureTest} skips the workspace-scoping requirement.
 *
 * <p>Inherits {@code findById(String)} from {@link JpaRepository} — that's the single-row PK
 * lookup the service uses on the hot path. The {@link #findActive} variant honours the
 * {@code validFrom / validTo} window so a future rollover (insert a new row with the future
 * effective date, set {@code valid_to} on the prior row) takes effect transparently.
 */
@Repository
@WorkspaceAgnostic("Pricing registry is global — vendors charge per model, not per workspace")
public interface ModelPricingRepository extends JpaRepository<ModelPricing, String> {
    /**
     * Find pricing for the given model that is effective at {@code at}. Returns
     * {@link Optional#empty()} when no row's window covers the instant, OR when the model id
     * is unknown. Used by {@code ModelPricingService.computeCost} so a wrongly-dated row in the
     * registry cannot silently apply outside its window.
     */
    @Query(
        """
        SELECT p FROM ModelPricing p
         WHERE p.modelId = :modelId
           AND p.validFrom <= :at
           AND (p.validTo IS NULL OR p.validTo > :at)
        """
    )
    Optional<ModelPricing> findActive(@Param("modelId") String modelId, @Param("at") Instant at);
}
