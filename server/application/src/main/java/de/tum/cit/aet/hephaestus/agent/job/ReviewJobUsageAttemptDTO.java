package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageEvent;
import de.tum.cit.aet.hephaestus.agent.usage.UsageFundingSource;
import de.tum.cit.aet.hephaestus.integration.core.spi.UsagePricingState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "The model usage and cost of one attempt of a job")
public record ReviewJobUsageAttemptDTO(
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The zero-based attempt number, 0 for the first run")
        int attempt,

        @NonNull @Schema(description = "When the usage was recorded")
        Instant occurredAt,

        @Schema(description = "The model the attempt called") @Nullable
        String model,

        @NonNull
        @Schema(
                description = "PRICED has a cost; NO_CHARGE is a real $0; UNPRICED means no price was known, so its"
                        + " cost is absent rather than $0")
        UsagePricingState pricingState,

        @NonNull @Schema(description = "Who pays: the instance's shared models, or the workspace's own provider")
        UsageFundingSource fundingSource,

        @Schema(description = "Cost in US dollars; absent while UNPRICED") @Nullable
        BigDecimal costUsd,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalCalls,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long inputTokens,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long outputTokens,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long reasoningTokens,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long cacheReadTokens,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long cacheWriteTokens) {

    static ReviewJobUsageAttemptDTO from(LlmUsageEvent event) {
        return new ReviewJobUsageAttemptDTO(
                event.getSourceAttempt(),
                event.getOccurredAt(),
                event.getModel(),
                event.getPricingState(),
                event.getFundingSource(),
                event.getPricingState() == UsagePricingState.UNPRICED ? null : event.getCostUsd(),
                event.getTotalCalls(),
                event.getInputTokens(),
                event.getOutputTokens(),
                event.getReasoningTokens(),
                event.getCacheReadTokens(),
                event.getCacheWriteTokens());
    }
}
