package de.tum.cit.aet.hephaestus.agent.usage;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * The body for both monthly caps. Which purse a request governs is carried by the path it is sent to,
 * so the field name does not encode it a second time.
 */
@Schema(description = "Set or clear a monthly LLM budget cap")
public record UpdateLlmBudgetRequestDTO(
    @Nullable
    @DecimalMin(value = "0.00")
    @Digits(integer = 8, fraction = 2)
    @Schema(
        description = "Cap in USD; 0 pauses the affected work immediately, null removes the cap. The purse " +
            "it governs is the one the path names, and only that one: /admin/workspaces/{workspaceSlug}/" +
            "llm/budget caps spend on shared (instance) models, /workspaces/{workspaceSlug}/llm/budget caps " +
            "the workspace's spend on its own connected provider. The two are never added together."
    )
    BigDecimal monthlyBudgetUsd
) {}
