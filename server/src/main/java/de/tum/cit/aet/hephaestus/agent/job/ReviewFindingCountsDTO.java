package de.tum.cit.aet.hephaestus.agent.job;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Schema(description = "Counts of findings by assessment")
public record ReviewFindingCountsDTO(@NonNull Long strengths, @NonNull Long problems, @NonNull Long notApplicable) {
    public static ReviewFindingCountsDTO empty() {
        return new ReviewFindingCountsDTO(0L, 0L, 0L);
    }
}
