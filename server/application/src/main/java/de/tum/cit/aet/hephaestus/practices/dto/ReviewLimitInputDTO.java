package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.practices.CanonicalDigest;
import de.tum.cit.aet.hephaestus.practices.ClosedPracticeInput;
import de.tum.cit.aet.hephaestus.practices.ReviewLimit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record ReviewLimitInputDTO(
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        @Size(max = ReviewLimit.CODE_MAX_LENGTH)
        @Nullable
        String code,

        @JsonProperty(required = true) @NotBlank @Size(max = ReviewLimit.DESCRIPTION_MAX_LENGTH) @NonNull
        String description)
        implements ClosedPracticeInput {
    public ReviewLimit toLimit() {
        return new ReviewLimit(
                code == null ? new CanonicalDigest().add(description).hex() : code, description);
    }
}
