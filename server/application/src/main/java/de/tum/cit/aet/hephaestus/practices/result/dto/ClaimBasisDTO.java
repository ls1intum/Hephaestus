package de.tum.cit.aet.hephaestus.practices.result.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Why a problem belongs to the reviewed work: the work introduced it, made it worse, or left a duty the practice
 * states undone. The indices name the problem's own citations.
 */
@Schema(description = "Why a problem belongs to this work")
public record ClaimBasisDTO(
        @NonNull
        @Schema(
                description = "introduced, worsened, duty or state",
                allowableValues = {"introduced", "worsened", "duty", "state"})
        String type,

        @Nullable @Schema(description = "The citation on the reviewed side; introduced and worsened only")
        Integer head,

        @Nullable @Schema(description = "The citation of the earlier state; worsened only")
        Integer base,

        @Nullable @Schema(description = "The citation of the event that owed the duty; duty only")
        Integer event,

        @Nullable @Schema(description = "The words of the practice's criteria that state the duty; duty only")
        String criterionQuote,

        @NonNull @Schema(description = "The basis in words", requiredMode = RequiredMode.REQUIRED)
        String explanation) {

    static @Nullable ClaimBasisDTO from(@Nullable JsonNode basis) {
        if (basis == null || !basis.isObject()) return null;
        String type = EvidenceWarrants.text(basis, "type");
        String explanation = EvidenceWarrants.text(basis, "explanation");
        if (type == null || explanation == null) return null;
        return new ClaimBasisDTO(
                type,
                index(basis, "head"),
                index(basis, "base"),
                index(basis, "event"),
                EvidenceWarrants.text(basis, "criterionQuote"),
                explanation);
    }

    private static @Nullable Integer index(JsonNode basis, String field) {
        JsonNode value = basis.path(field);
        return value.isIntegralNumber() ? value.asInt() : null;
    }
}
