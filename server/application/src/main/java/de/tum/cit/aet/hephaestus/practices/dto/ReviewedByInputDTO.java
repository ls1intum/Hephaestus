package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.practices.ClosedPracticeInput;
import de.tum.cit.aet.hephaestus.practices.ReviewedBy;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewedByKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewedByInputDTO(
        @JsonProperty(required = true) @NotNull ReviewedByKind by,
        @JsonProperty(required = true) @Valid @Nullable ReviewLimitInputDTO humanReason,
        @JsonProperty(required = true) @NotNull @Size(max = 100) List<@Valid @NotNull ReviewLimitInputDTO> limits)
        implements ClosedPracticeInput {
    public ReviewedByInputDTO {
        limits = List.copyOf(limits);
    }

    public ReviewedBy toReviewedBy() {
        return new ReviewedBy(by, humanReason == null ? null : humanReason.toLimit(),
                limits.stream().map(ReviewLimitInputDTO::toLimit).toList());
    }
}
