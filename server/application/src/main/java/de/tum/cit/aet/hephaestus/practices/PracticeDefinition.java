package de.tum.cit.aet.hephaestus.practices;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import de.tum.cit.aet.hephaestus.integration.core.spi.AboutRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PracticeDefinition(
        @JsonProperty(value = "name", required = true) String name,
        @JsonProperty(value = "reviewOn", required = true) ReviewOn reviewOn,

        @JsonProperty(value = "appliesWhen", required = true) @Nullable
        AppliesWhen appliesWhen,

        @JsonProperty(value = "about", required = true) @Nullable
        AboutRole about,

        @JsonProperty(value = "reads", required = true) PracticeReads reads,
        @JsonProperty(value = "reviewedBy", required = true) ReviewedBy reviewedBy,
        @JsonProperty(value = "criteria", required = true) String criteria,

        @JsonProperty(value = "script", required = true) @Nullable
        String script,

        @JsonProperty(value = "scan", required = true) @Nullable
        ScanDefinition scan,

        @JsonProperty(value = "lineNotes", required = true) @JsonSetter(nulls = Nulls.FAIL)
        boolean lineNotes,

        @JsonProperty(value = "guidance", required = true) PracticeGuidance guidance)
        implements ClosedPracticeInput {
    public static final int MAX_PRACTICE_SCRIPT_LENGTH = 100_000;

    public PracticeDefinition {
        Objects.requireNonNull(name);
        Objects.requireNonNull(reviewOn);
        Objects.requireNonNull(reads);
        Objects.requireNonNull(reviewedBy);
        Objects.requireNonNull(criteria);
        Objects.requireNonNull(guidance);
    }
}
