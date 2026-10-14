package de.tum.cit.aet.hephaestus.agent.proxy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.agent.handler.composition.ComposedFeedback;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PrepareFeedbackRequestDTO(
        UUID reviewId, UUID jobId, int attempt, List<UUID> basisResultIds, List<ComposedFeedback> composition) {
    public PrepareFeedbackRequestDTO {
        basisResultIds = List.copyOf(basisResultIds);
        composition = List.copyOf(composition);
    }
}
