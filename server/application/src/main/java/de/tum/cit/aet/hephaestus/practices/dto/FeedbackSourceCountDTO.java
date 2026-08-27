package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

/**
 * How many distinct work artifacts of one kind contributed feedback to a derived surface not a score. The source set is {@link ArtifactKind}, so a
 * new observable integration (e.g. chat threads) surfaces here without an API change.
 */
@Schema(description = "Distinct work artifacts of one kind that contributed feedback")
public record FeedbackSourceCountDTO(
        @NonNull @Schema(description = "The kind of work the feedback came from")
        ArtifactKind workKind,

        @NonNull @Schema(description = "Distinct artifacts of this kind in the selected time period")
        Long count) {}
