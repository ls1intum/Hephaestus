package de.tum.cit.aet.hephaestus.agent.proxy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.agent.handler.composition.ComposedFeedback;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PrepareFeedbackRequestDTO(
        UUID reviewId,
        UUID jobId,
        int attempt,
        List<UUID> basisResultIds,
        List<ComposedFeedback> composition,
        @JsonProperty(required = true) boolean publicComposition,
        @Nullable String publicationContext) {
    public PrepareFeedbackRequestDTO {
        basisResultIds = List.copyOf(basisResultIds);
        composition = List.copyOf(composition);
    }
}
