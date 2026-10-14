package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.practices.CanonicalDigest;
import de.tum.cit.aet.hephaestus.practices.ClosedPracticeInput;
import de.tum.cit.aet.hephaestus.practices.ReviewLimit;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record ReviewLimitInputDTO(
        @Nullable String code,
        @JsonProperty(required = true) @NotBlank @NonNull String description) implements ClosedPracticeInput {
    public ReviewLimit toLimit() {
        return new ReviewLimit(
                code == null ? new CanonicalDigest().add(description).hex() : code, description);
    }
}
