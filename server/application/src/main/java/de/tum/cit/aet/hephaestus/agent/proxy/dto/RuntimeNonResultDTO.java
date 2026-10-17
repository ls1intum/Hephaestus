package de.tum.cit.aet.hephaestus.agent.proxy.dto;

import de.tum.cit.aet.hephaestus.practices.spi.ReviewStopReason;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.NonNull;

/** An assigned practice that runtime execution could not complete, without an outcome about the work. */
public record RuntimeNonResultDTO(
        @NonNull String practiceSlug, @NonNull ReviewStopReason reason) {
    public RuntimeNonResultDTO {
        Objects.requireNonNull(practiceSlug, "practiceSlug");
        Objects.requireNonNull(reason, "reason");
        if (!practiceSlug.matches("[a-z0-9][a-z0-9-]{0,63}"))
            throw new IllegalArgumentException("Invalid practice slug");
        if (!Set.of(
                        ReviewStopReason.BUDGET,
                        ReviewStopReason.DEADLINE,
                        ReviewStopReason.CANCELLED,
                        ReviewStopReason.MODEL_UNAVAILABLE,
                        ReviewStopReason.EXECUTION_FAILED,
                        ReviewStopReason.EXECUTION_INCOMPLETE)
                .contains(reason))
            throw new IllegalArgumentException("A runtime non-result requires an execution stop reason");
    }
}
