package de.tum.cit.aet.hephaestus.practices.observation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

@Schema(description = "Verified, source-bound evidence for an observation")
public record ObservationEvidenceDTO(@NonNull List<EvidenceCitationDTO> citations, @Nullable String detector) {
    public ObservationEvidenceDTO {
        citations = List.copyOf(citations);
        if (citations.isEmpty()) {
            throw new IllegalArgumentException("Observation evidence requires citations");
        }
    }

    public static @Nullable ObservationEvidenceDTO from(@Nullable JsonNode evidence) {
        if (evidence == null || !evidence.isObject()) return null;
        String detector = evidence.path("detector").asString(null);
        List<EvidenceCitationDTO> citations = evidence
            .path("citations")
            .valueStream()
            .map(EvidenceCitationDTO::from)
            .toList();
        return new ObservationEvidenceDTO(citations, detector);
    }
}
