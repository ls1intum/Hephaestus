package de.tum.cit.aet.hephaestus.practices.scan.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.hibernate.validator.constraints.UniqueElements;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record StartScanRequestDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", pattern = "^[1-9][0-9]*$")
        @NotNull
        @Pattern(regexp = "[1-9][0-9]*")
        @DecimalMax("9223372036854775807")
        String repositoryItemId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable @Size(min = 1, max = 1024)
        String ref,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable @Size(min = 1, max = 1024)
        String expectedVersion,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable @Size(min = 1, max = 1000) @UniqueElements
        List<@NotBlank @Size(max = 128) String> practiceSlugs,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotNull
        @Digits(integer = 6, fraction = 4)
        @DecimalMin("0.0001")
        @DecimalMax("100000")
        BigDecimal budgetUsd,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Boolean reuse) {
    public StartScanRequestDTO {
        practiceSlugs = practiceSlugs == null ? null : List.copyOf(practiceSlugs);
    }
}
