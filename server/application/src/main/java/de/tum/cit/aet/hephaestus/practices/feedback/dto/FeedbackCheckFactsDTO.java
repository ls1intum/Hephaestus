package de.tum.cit.aet.hephaestus.practices.feedback.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheckFacts;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record FeedbackCheckFactsDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String admittedRolloutRevision,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String evaluatedRolloutRevision,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean inScope,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean evidenceAllowed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean modelAllowed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean deliveryEnabled,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean incorrectBasis,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean currentVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean approvalCurrent,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean writeStarted) {

    public static FeedbackCheckFactsDTO from(FeedbackCheckFacts facts) {
        return new FeedbackCheckFactsDTO(
                Long.toString(facts.admittedRolloutRevision()),
                Long.toString(facts.evaluatedRolloutRevision()),
                facts.inScope(),
                facts.evidenceAllowed(),
                facts.modelAllowed(),
                facts.deliveryEnabled(),
                facts.incorrectBasis(),
                facts.currentVersion(),
                facts.approvalCurrent(),
                facts.writeStarted());
    }
}
