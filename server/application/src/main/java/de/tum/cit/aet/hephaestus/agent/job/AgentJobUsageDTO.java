package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageEvent;
import de.tum.cit.aet.hephaestus.agent.usage.PricingState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** What a job spent on the model, attempt by attempt, and in total. */
@Schema(description = "The model usage and cost of a job, per attempt and in total")
public record AgentJobUsageDTO(
        @NonNull @Schema(description = "One entry per attempt with recorded usage, first attempt first")
        List<AgentJobUsageAttemptDTO> attempts,

        @Schema(
                description = "The cost of every attempt in US dollars, the models its practice scripts called"
                        + " included; absent when an attempt or a script call has no known price, or no usage is"
                        + " recorded")
        @Nullable
        BigDecimal totalCostUsd,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Attempts whose price is not known")
        int unpricedAttempts,

        @Schema(
                description = "What the models its practice scripts called cost in US dollars, already in"
                        + " totalCostUsd; absent when no script called a model or a call has no known price")
        @Nullable
        BigDecimal practiceScriptCostUsd) {

    /**
     * @param attempts the review model's usage, one entry per attempt
     * @param practiceScriptRows the ledger rows of the practice scripts' own model calls in any attempt
     */
    static AgentJobUsageDTO of(List<AgentJobUsageAttemptDTO> attempts, List<LlmUsageEvent> practiceScriptRows) {
        int unpriced = (int)
                attempts.stream().filter(attempt -> attempt.costUsd() == null).count();
        boolean scriptsUnpriced =
                practiceScriptRows.stream().anyMatch(row -> row.getPricingState() == PricingState.UNPRICED);
        BigDecimal scripts = practiceScriptRows.isEmpty() || scriptsUnpriced
                ? null
                : practiceScriptRows.stream()
                        .map(row -> Objects.requireNonNullElse(row.getCostUsd(), BigDecimal.ZERO))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = attempts.isEmpty() || unpriced > 0 || scriptsUnpriced
                ? null
                : attempts.stream()
                        .map(attempt -> Objects.requireNonNullElse(attempt.costUsd(), BigDecimal.ZERO))
                        .reduce(Objects.requireNonNullElse(scripts, BigDecimal.ZERO), BigDecimal::add);
        return new AgentJobUsageDTO(attempts, total, unpriced, scripts);
    }
}
