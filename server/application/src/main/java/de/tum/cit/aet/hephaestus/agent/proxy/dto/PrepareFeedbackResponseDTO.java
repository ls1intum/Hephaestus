package de.tum.cit.aet.hephaestus.agent.proxy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PrepareFeedbackResponseDTO(List<UUID> feedbackIds, String contentDigest, Instant preparedAt) {
    public PrepareFeedbackResponseDTO {
        feedbackIds = List.copyOf(feedbackIds);
    }
}
