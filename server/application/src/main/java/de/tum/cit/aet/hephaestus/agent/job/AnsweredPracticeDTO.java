package de.tum.cit.aet.hephaestus.agent.job;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

@Schema(
        description = "A ready practice this review did not ask, because a completed review had already answered it on "
                + "exactly the same code. Its result belongs to that review; this review recorded none for it.")
public record AnsweredPracticeDTO(
        @NonNull @Schema(description = "The practice left out")
        String practiceSlug,

        @NonNull @Schema(description = "The practice version the earlier answer was made under")
        Long practiceVersionId,

        @NonNull @Schema(description = "The completed review whose result answers it")
        UUID reviewId) {}
