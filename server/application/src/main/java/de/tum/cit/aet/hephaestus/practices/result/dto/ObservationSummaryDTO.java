package de.tum.cit.aet.hephaestus.practices.result.dto;

import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

/** One problem of a result as a list names it: its weight and its one line, with no places and no explanation. */
@Schema(description = "One problem of a not met result, as a list names it")
public record ObservationSummaryDTO(
        @NonNull @Schema(description = "How much this problem weighs at its place alone")
        Severity severity,

        @NonNull @Schema(description = "One line") String summary) {

    public static ObservationSummaryDTO from(Observation observation) {
        return new ObservationSummaryDTO(observation.getSeverity(), observation.getSummary());
    }
}
