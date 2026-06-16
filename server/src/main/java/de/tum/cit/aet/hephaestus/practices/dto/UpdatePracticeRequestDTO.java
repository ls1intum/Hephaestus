package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.model.Polarity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Request DTO for updating an existing practice definition.
 *
 * <p>Uses PATCH semantics: only non-null fields are applied.
 */
@Schema(description = "Request to update an existing practice definition (PATCH — only non-null fields applied)")
public record UpdatePracticeRequestDTO(
    @Size(min = 3, max = 128, message = "Name must be between 3 and 128 characters")
    @Pattern(regexp = ".*\\S.*", message = "Name must not be blank")
    @Schema(description = "Human-readable name", example = "PR Description Quality")
    String name,

    @Size(min = 1, max = 10, message = "Trigger events must contain between 1 and 10 entries")
    @ValidTriggerEvents
    @Schema(description = "Domain events that trigger detection")
    List<String> triggerEvents,

    @Size(max = 50000, message = "Criteria must be at most 50000 characters")
    @Pattern(regexp = ".*\\S.*", message = "Criteria must not be blank")
    @Schema(description = "Practice evaluation criteria")
    String criteria,

    @Size(max = 100000, message = "Precompute script must be at most 100000 characters")
    @Schema(description = "TypeScript/Bun precompute script for static analysis before AI review")
    String precomputeScript,

    @Schema(description = "Artifact this practice evaluates", example = "ISSUE") @Nullable WorkArtifact artifactType,

    @Schema(
        description = "Whether the practice is a desirable habit, an anti-pattern, or context-dependent",
        example = "UNDESIRABLE"
    )
    @Nullable
    Polarity polarity
) {}
