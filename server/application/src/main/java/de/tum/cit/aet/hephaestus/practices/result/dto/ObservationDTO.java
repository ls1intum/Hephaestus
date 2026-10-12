package de.tum.cit.aet.hephaestus.practices.result.dto;

import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** One located problem of a practice result: what is wrong at its cited places, and how much it weighs alone. */
@Schema(description = "One located problem of a practice result")
public record ObservationDTO(
        @NonNull @Schema(description = "Observation ID") UUID observationId,

        @Schema(description = "Display order within the result, from 0", requiredMode = RequiredMode.REQUIRED)
        int position,

        @NonNull @Schema(description = "How much this problem weighs at its place alone")
        Severity severity,

        @NonNull @Schema(description = "One line") String summary,

        @NonNull String explanation,

        @NonNull @Schema(description = "The verified places of the problem, at least one")
        List<EvidenceCitationDTO> citations,

        @Nullable
        @Schema(description = "Why the problem belongs to this work; null when it was recorded before bases were kept")
        ClaimBasisDTO basis,

        @Nullable @Schema(description = "What would fix it; null when the review wrote none")
        String nextStep) {
    public ObservationDTO {
        citations = List.copyOf(citations);
    }

    public static ObservationDTO from(Observation observation) {
        return new ObservationDTO(
                observation.getId(),
                observation.getPosition(),
                observation.getSeverity(),
                observation.getSummary(),
                observation.getExplanation(),
                observation
                        .getCitations()
                        .valueStream()
                        .map(EvidenceCitationDTO::from)
                        .toList(),
                ClaimBasisDTO.from(observation.getBasis()),
                observation.getNextStep());
    }
}
