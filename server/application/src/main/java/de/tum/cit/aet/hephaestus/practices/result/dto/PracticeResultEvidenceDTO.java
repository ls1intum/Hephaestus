package de.tum.cit.aet.hephaestus.practices.result.dto;

import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.result.ResultCitations;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Verified, source-bound evidence for an observation: the quotes it rests on, and — at most one of them
 * — the warrant its outcome owes beyond those quotes. Which warrant is recorded follows the outcome: a
 * behaviour found absent records its search, a practice with nothing to judge records why, and an
 * undecided one records what it could not settle.
 */
@Schema(description = "Verified, source-bound evidence for a result")
public record PracticeResultEvidenceDTO(
        @NonNull @Schema(description = "The result's own citations; each problem carries its own places")
        List<EvidenceCitationDTO> citations,

        @Nullable
        @Schema(description = "Where the review looked when it found nothing; null unless it recorded a search")
        EvidenceSearchDTO search,

        @Nullable
        @Schema(description = "Why this practice had nothing to judge here; null unless the review recorded a reason")
        EvidenceInapplicabilityDTO inapplicability,

        @NonNull
        @Schema(description = "What the review could not settle, each where it arose; empty unless it recorded one")
        List<OpenQuestionDTO> undecidability) {
    public PracticeResultEvidenceDTO {
        citations = List.copyOf(citations);
        undecidability = List.copyOf(undecidability);
    }

    /** The result's evidence with its own citations: the places its problems carry are shown with them. */
    public static @Nullable PracticeResultEvidenceDTO from(@Nullable JsonNode evidence, List<Observation> problems) {
        if (evidence == null || !evidence.isObject()) return null;
        List<EvidenceCitationDTO> citations = ResultCitations.own(evidence, problems).stream()
                .map(EvidenceCitationDTO::from)
                .toList();
        return new PracticeResultEvidenceDTO(
                citations,
                EvidenceSearchDTO.from(evidence.path("search")),
                EvidenceInapplicabilityDTO.from(evidence.path("inapplicability")),
                OpenQuestionDTO.from(evidence.path("undecidability")));
    }
}
