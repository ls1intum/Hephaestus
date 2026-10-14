package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import de.tum.cit.aet.hephaestus.integration.core.spi.AboutRole;
import de.tum.cit.aet.hephaestus.practices.AppliesWhen;
import de.tum.cit.aet.hephaestus.practices.ClosedPracticeInput;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeGuidance;
import de.tum.cit.aet.hephaestus.practices.PracticeReads;
import de.tum.cit.aet.hephaestus.practices.ReviewOn;
import de.tum.cit.aet.hephaestus.practices.ScanDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PracticeDefinitionInputDTO(
        @JsonProperty(required = true) @NotBlank String name,
        @JsonProperty(required = true) @NotNull @Valid ReviewOn reviewOn,
        @JsonProperty(required = true) @Valid @Nullable AppliesWhen appliesWhen,
        @JsonProperty(required = true) @Nullable AboutRole about,
        @JsonProperty(required = true) @NotNull @Valid PracticeReads reads,
        @JsonProperty(required = true) @NotNull @Valid ReviewedByInputDTO reviewedBy,
        @JsonProperty(required = true) @NotBlank String criteria,
        @JsonProperty(required = true) @Nullable String script,
        @JsonProperty(required = true) @Valid @Nullable ScanDefinition scan,

        @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL)
        boolean lineNotes,

        @JsonProperty(required = true) @NotNull @Valid PracticeGuidance guidance)
        implements ClosedPracticeInput {
    public PracticeDefinition toDefinition() {
        return new PracticeDefinition(
                name,
                reviewOn,
                appliesWhen,
                about,
                reads,
                reviewedBy.toReviewedBy(),
                criteria,
                script,
                scan,
                lineNotes,
                guidance);
    }
}
