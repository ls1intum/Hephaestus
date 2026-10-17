package de.tum.cit.aet.hephaestus.agent.proxy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.spi.ResultCoverage;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdmittedResultDTO(
        String practiceSlug,
        String itemId,
        UUID resultId,
        ResultOutcome outcome,
        @Nullable String summary,
        @Nullable String explanation,
        JsonNode evidence,
        @Nullable JsonNode example,
        ResultCoverage coverage,
        List<AdmittedProblemDTO> problems,
        boolean summaryOnly,
        boolean publicEligible) {
    public AdmittedResultDTO {
        problems = List.copyOf(problems);
    }
}
