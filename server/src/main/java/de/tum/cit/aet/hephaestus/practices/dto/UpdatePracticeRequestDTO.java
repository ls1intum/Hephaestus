package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

@Schema(description = "Request to update a practice; omitted fields remain unchanged")
public record UpdatePracticeRequestDTO(
    @Size(min = 3, max = 128, message = "Name must be between 3 and 128 characters")
    @Pattern(regexp = ".*\\S.*", message = "Name must not be blank")
    @Schema(description = "Human-readable name", example = "PR Description Quality")
    @Nullable
    String name,

    @Size(max = 10, message = "Trigger events must contain at most 10 entries")
    @ValidTriggerEvents
    @Schema(description = "Domain events that trigger detection; empty for scheduled conversation reviews")
    @Nullable
    List<String> triggerEvents,

    @Size(max = 50000, message = "Criteria must be at most 50000 characters")
    @Pattern(regexp = ".*\\S.*", message = "Criteria must not be blank")
    @Schema(description = "Practice evaluation criteria")
    @Nullable
    String criteria,

    @Size(max = 100000, message = "Precompute script must be at most 100000 characters")
    @Schema(description = "TypeScript/Bun precompute script for static analysis before AI review")
    @Nullable
    String precomputeScript,

    @Schema(description = "Artifact this practice evaluates", example = "ISSUE") @Nullable WorkArtifact artifactType,

    @Size(max = 2000, message = "Why-it-matters must be at most 2000 characters")
    @Pattern(regexp = ".*\\S.*", message = "Why-it-matters must not be blank")
    @Schema(description = "Developer-facing rationale (learner layer); plain language, never the detection rubric")
    @Nullable
    String whyItMatters,

    @Size(max = 2000, message = "What-good-looks-like must be at most 2000 characters")
    @Pattern(regexp = ".*\\S.*", message = "What-good-looks-like must not be blank")
    @Schema(description = "Developer-facing exemplar (learner layer); a concrete instance, not the rubric")
    @Nullable
    String whatGoodLooksLike,

    @Valid
    @Schema(description = "Catalog placement to apply with the definition update; omit to leave unchanged")
    @Nullable
    BindPracticeAreaRequestDTO area,

    @Schema(description = "Optional fields to clear before applying supplied values")
    @Nullable
    Set<ClearablePracticeField> clear
) {}
