package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionField;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.NonNull;

/** One stored version of a practice's definition, and which parts of it differ from the version before. */
@Schema(description = "One version of a practice's definition and what changed in it")
public record PracticeRevisionDTO(
        @NonNull Long id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "1 for the first version, counting up")
        int revisionNumber,

        @NonNull Instant createdAt,

        @NonNull
        @Schema(
                description = "The parts of the definition that differ from the version before; empty for the first"
                        + " version and for a version recorded again without a change")
        List<PracticeDefinitionField> changed,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether what a review checks changed, so results recorded before it no longer reflect"
                        + " the practice. A change to the wording around it, or to when it runs, does not count.")
        boolean reviewRulesChanged) {}
