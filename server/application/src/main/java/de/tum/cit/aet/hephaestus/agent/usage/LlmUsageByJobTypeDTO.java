package de.tum.cit.aet.hephaestus.agent.usage;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import org.jspecify.annotations.NonNull;

/** One job type's slice of a month, inside {@link WorkspaceLlmUsageReportDTO}. */
@Schema(description = "Month spend aggregated by job type")
public record LlmUsageByJobTypeDTO(
        @NonNull LlmUsageJobType jobType,

        @NonNull @Schema(description = "Confirmed spend on shared (instance) models for this job type, in USD.")
        BigDecimal instanceTotalCostUsd,

        @NonNull @Schema(description = "Spend on this workspace's own connected provider(s) for this job type, in USD.")
        BigDecimal ownProviderTotalCostUsd,

        @NonNull
        @Schema(
                description = "Ledger usage records for this job type with unavailable cost because rates are missing "
                        + "or measurements are unverified. Excluded from both totals above.")
        Long unpricedEventCount,

        @NonNull Long inputTokens,
        @NonNull Long outputTokens,
        @NonNull Long cacheReadTokens,
        @NonNull Long cacheWriteTokens,

        @NonNull
        @Schema(
                description = "LLM API calls, as reported by the runtime. Practice reviews and mentor turns both "
                        + "include every assistant call in an internal tool loop.")
        Long totalCalls,

        @NonNull @Schema(description = "Ledger events (jobs / mentor turns)")
        Long events) {}
