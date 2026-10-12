package de.tum.cit.aet.hephaestus.practices.result.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** One place of the reviewed work that shows the practice done well. It teaches; it is never a problem. */
@Schema(description = "One place of the work that shows the practice done well")
public record PracticeResultExampleDTO(
        @NonNull EvidenceCitationDTO citation,

        @NonNull @Schema(description = "About the work, at most 280 characters")
        String note) {

    public static @Nullable PracticeResultExampleDTO from(@Nullable JsonNode example) {
        if (example == null || !example.isObject() || !example.path("citation").isObject()) return null;
        String note = EvidenceWarrants.text(example, "note");
        return note == null
                ? null
                : new PracticeResultExampleDTO(EvidenceCitationDTO.from(example.path("citation")), note);
    }
}
