package de.tum.cit.aet.hephaestus.agent.settings;

import de.tum.cit.aet.hephaestus.workspace.settings.PracticeReviewField;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Set;

/**
 * PATCH body for per-workspace practice-review policy; {@code null} fields are unchanged. Fields
 * named in {@code reset} are cleared back to the inherited fleet default — reset is applied before
 * the value patch, so a field can be reset and re-set in one request.
 */
@Schema(description = "Update per-workspace practice-review policy. Null fields unchanged; 'reset' clears to inherit.")
public record UpdatePracticeReviewSettingsDTO(
    @Schema(description = "Run practice review for all contributors (vs only the run_practice_review role)")
    Boolean runForAllUsers,
    @Schema(description = "Skip practice review for draft PRs/MRs") Boolean skipDrafts,
    @Schema(description = "Deliver feedback to already-merged PRs/MRs") Boolean deliverToMerged,
    @Min(value = 0, message = "Cooldown must not be negative")
    @Max(value = 1440, message = "Cooldown must not exceed 1440 minutes")
    @Schema(description = "Minimum minutes between reviews for the same PR; 0 disables the cooldown")
    Integer cooldownMinutes,
    @Schema(description = "Fields to reset to the inherited fleet default") Set<PracticeReviewField> reset
) {}
