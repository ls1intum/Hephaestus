package de.tum.cit.aet.hephaestus.agent.job;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** What a job spent on the model, attempt by attempt, and in total. */
@Schema(description = "The model usage and cost of a job, per attempt and in total")
public record AgentJobUsageDTO(
        @NonNull @Schema(description = "One entry per attempt that reached the model, first attempt first")
        List<AgentJobUsageAttemptDTO> attempts,

        @Schema(
                description = "The cost of every attempt in US dollars; absent when an attempt has no known price or"
                        + " none reached the model")
        @Nullable
        BigDecimal totalCostUsd,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Attempts whose price is not known")
        int unpricedAttempts) {

    static AgentJobUsageDTO of(List<AgentJobUsageAttemptDTO> attempts) {
        int unpriced = (int)
                attempts.stream().filter(attempt -> attempt.costUsd() == null).count();
        BigDecimal total = attempts.isEmpty() || unpriced > 0
                ? null
                : attempts.stream()
                        .map(attempt -> Objects.requireNonNullElse(attempt.costUsd(), BigDecimal.ZERO))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AgentJobUsageDTO(attempts, total, unpriced);
    }
}
