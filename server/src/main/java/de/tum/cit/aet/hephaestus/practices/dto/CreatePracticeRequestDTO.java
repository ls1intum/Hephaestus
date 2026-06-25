package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Request DTO for creating a new practice definition.
 */
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
    @Size(min = 1, max = 10, message = "Trigger events must contain between 1 and 10 entries")
    @ValidTriggerEvents
    @Schema(
        description = "Domain events that trigger detection",
        example = "[\"PullRequestCreated\", \"ReviewSubmitted\"]"
    )
    List<String> triggerEvents,

    @NotBlank(message = "Criteria is required")
    @Size(max = 50000, message = "Criteria must be at most 50000 characters")
    @Schema(description = "Practice evaluation criteria")
    String criteria,

    @Size(max = 100000, message = "Precompute script must be at most 100000 characters")
    @Schema(description = "TypeScript/Bun precompute script for static analysis before AI review")
    String precomputeScript,

    @Schema(
        description = "Artifact this practice evaluates. Defaults to PULL_REQUEST when omitted.",
        example = "PULL_REQUEST"
    )
    @Nullable
    WorkArtifact artifactType,

    @Size(max = 2000, message = "Why-it-matters must be at most 2000 characters")
    @Schema(description = "Developer-facing rationale (learner layer); plain language, never the detection rubric")
    @Nullable
    String whyItMatters,

    @Size(max = 2000, message = "What-good-looks-like must be at most 2000 characters")
    @Schema(description = "Developer-facing exemplar (learner layer); a concrete instance, not the rubric")
    @Nullable
    String whatGoodLooksLike
) {}
