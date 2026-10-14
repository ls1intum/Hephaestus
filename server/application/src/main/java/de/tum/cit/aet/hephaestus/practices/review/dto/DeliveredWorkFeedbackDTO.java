package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record DeliveredWorkFeedbackDTO(@Nullable ItemRefDTO item, List<DeliveredWorkFeedbackItemDTO> feedback) {
    public DeliveredWorkFeedbackDTO {
        feedback = List.copyOf(feedback);
    }
}
