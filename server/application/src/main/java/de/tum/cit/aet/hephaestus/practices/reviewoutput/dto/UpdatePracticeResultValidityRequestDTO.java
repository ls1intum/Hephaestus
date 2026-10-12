package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.practices.model.PracticeResultInvalidation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NonNull;

@Schema(description = "Invalidate a result that was wrong when recorded, or restore it")
public record UpdatePracticeResultValidityRequestDTO(
        @NonNull @NotNull @Schema(description = "false invalidates the result; true restores it")
        Boolean valid,

        @NonNull
        @NotBlank
        @Size(max = PracticeResultInvalidation.MAX_REASON_LENGTH)
        @Schema(description = "Why, kept with the correction; an invalidation reason is shown to the developer")
        String reason) {}
