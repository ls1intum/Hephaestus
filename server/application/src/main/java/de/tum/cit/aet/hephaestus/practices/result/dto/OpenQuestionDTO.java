package de.tum.cit.aet.hephaestus.practices.result.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * A real question the evidence left open, located where it can be. Read from {@code evidence.undecidability}, a list;
 * a result recorded before questions were located holds one object, which reads as a list of one.
 */
@Schema(description = "A question the review could not settle, what would settle it, and where it arose")
public record OpenQuestionDTO(
        @NonNull @Schema(description = "The question this work left open")
        String openQuestion,

        @NonNull @Schema(description = "What would have answered it")
        String wouldSettleIt,

        @Nullable @Schema(description = "The place in the work the question is about; null when it has none")
        EvidenceCitationDTO citation) {

    static List<OpenQuestionDTO> from(JsonNode undecidability) {
        List<JsonNode> entries = new ArrayList<>();
        if (undecidability.isArray()) undecidability.forEach(entries::add);
        else if (undecidability.isObject()) entries.add(undecidability);
        List<OpenQuestionDTO> questions = new ArrayList<>();
        for (JsonNode entry : entries) {
            String openQuestion = EvidenceWarrants.text(entry, "openQuestion");
            String wouldSettleIt = EvidenceWarrants.text(entry, "wouldSettleIt");
            if (openQuestion == null || wouldSettleIt == null) continue;
            JsonNode citation = entry.path("citation");
            questions.add(new OpenQuestionDTO(
                    openQuestion, wouldSettleIt, citation.isObject() ? EvidenceCitationDTO.from(citation) : null));
        }
        return List.copyOf(questions);
    }
}
