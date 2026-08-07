package de.tum.cit.aet.hephaestus.practices.curated.dto;

import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedReviewPolicy;
import de.tum.cit.aet.hephaestus.practices.PracticeBinding;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "A complete curated practice definition")
public record CuratedPracticeRequestDTO(
    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 128, message = "Name must be between 3 and 128 characters")
    @NonNull
    String name,

    @NotNull(message = "At least one binding is required")
    @Size(min = 1, max = 10, message = "A practice must declare between 1 and 10 bindings")
    @Valid
    @Schema(description = "Occasions this practice is reviewed on; the kind of work is read off the signals")
    @NonNull
    List<PracticeBinding> bindings,

    @NotBlank(message = "Criteria is required")
    @Size(max = 50000, message = "Criteria must be at most 50000 characters")
    @NonNull
    String criteria,

    @Size(
        max = PracticeDefinition.MAX_PRECOMPUTE_SCRIPT_LENGTH,
        message = "Precompute script must be at most 100000 characters"
    )
    @Nullable
    String precomputeScript,

    @Valid
    @Schema(description = "Evidence requirements; omit to use the recommended requirements for the selected work type")
    @Nullable
    PracticeAutomatedReviewPolicy automatedReviewPolicy,

    @Size(max = 2000, message = "Why it matters must be at most 2000 characters") @Nullable String whyItMatters,

    @Size(max = 2000, message = "What good looks like must be at most 2000 characters")
    @Nullable
    String whatGoodLooksLike,

    @Size(max = 64, message = "Area slug must be at most 64 characters") @Nullable String areaSlug
) {
    public PracticeDefinition definition(PracticeAutomatedReviewPolicy resolvedEvidence) {
        return new PracticeDefinition(
            name,
            bindings,
            criteria,
            precomputeScript,
            resolvedEvidence,
            whyItMatters,
            whatGoodLooksLike,
            areaSlug
        );
    }
}
