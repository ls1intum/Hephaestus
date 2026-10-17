package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record DeliveredWorkFeedbackItemDTO(
        UUID id,
        Instant deliveredAt,
        List<DeliveredFeedbackPracticeDTO> practices,
        List<DeliveredWorkFeedbackCopyDTO> copies) {
    public DeliveredWorkFeedbackItemDTO {
        practices = List.copyOf(practices);
        copies = List.copyOf(copies);
    }
}
