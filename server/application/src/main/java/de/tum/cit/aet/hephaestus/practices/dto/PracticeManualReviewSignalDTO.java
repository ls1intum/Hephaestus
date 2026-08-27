package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

/**
 * The way in a person opens by asking for a review of this work type, carried apart from the occasions
 * because it is not one of them: it reviews every practice on the work type whatever state the work is
 * in, so there is nothing for an author to decide about it.
 */
@Schema(description = "The signal a person raises by asking for a review of this work type by hand")
public record PracticeManualReviewSignalDTO(
        @NonNull @Schema(example = "scm.pull_request.manual_review")
        SignalName signal,

        @NonNull String displayName) {}
