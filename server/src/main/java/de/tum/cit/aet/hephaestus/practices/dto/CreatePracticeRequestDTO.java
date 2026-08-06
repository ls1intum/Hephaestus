package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedReviewPolicy;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

@Schema(description = "Request to create a new practice definition")
public record CreatePracticeRequestDTO(
    @NotBlank(message = "Slug is required")
    @Size(min = 3, max = 64, message = "Slug must be between 3 and 64 characters")
    @Pattern(
        regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
        message = "Slug must contain only lowercase alphanumeric characters and hyphens," +
            " must not start or end with a hyphen, and must not contain consecutive hyphens"
    )
    @Schema(description = "URL-safe identifier unique within the workspace", example = "pr-description-quality")
    String slug,

    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 128, message = "Name must be between 3 and 128 characters")
    @Schema(description = "Human-readable name", example = "PR Description Quality")
    String name,

    @NotNull(message = "Trigger events are required")
    @Size(max = 10, message = "Trigger events must contain at most 10 entries")
    @ValidTriggerEvents
    @Schema(
        description = "Events that start a practice review; empty for scheduled conversation reviews",
        example = "[\"PullRequestCreated\", \"ReviewSubmitted\"]"
    )
    List<String> triggerEvents,

    @NotBlank(message = "Criteria is required")
    @Size(max = 50000, message = "Criteria must be at most 50000 characters")
    @Schema(description = "Practice review criteria")
    String criteria,

    @Size(
        max = PracticeDefinition.MAX_PRECOMPUTE_SCRIPT_LENGTH,
        message = "Precompute script must be at most 100000 characters"
    )
    @Schema(description = "TypeScript/Bun static analysis run before automated review")
    String precomputeScript,

    @Valid
    @Schema(
        description = "Versioned evidence required before Hephaestus may review work; " +
            "omit to use the recommended requirements for the selected work type"
    )
    @Nullable
    PracticeAutomatedReviewPolicy automatedReviewPolicy,

    @Schema(description = "Type of reviewed work. Defaults to PULL_REQUEST when omitted.", example = "PULL_REQUEST")
    @Nullable
    ArtifactKind artifactKind,

    @Size(max = 2000, message = "Why-it-matters must be at most 2000 characters")
    @Schema(description = "Plain-language rationale shown to the developer")
    @Nullable
    String whyItMatters,

    @Size(max = 2000, message = "What-good-looks-like must be at most 2000 characters")
    @Schema(description = "Developer-facing exemplar; a concrete instance, not the review criteria")
    @Nullable
    String whatGoodLooksLike,

    @Schema(description = "Practice area to add the practice to. Omit or set to null for Unassigned.", nullable = true)
    @Nullable
    String areaSlug
) {}
