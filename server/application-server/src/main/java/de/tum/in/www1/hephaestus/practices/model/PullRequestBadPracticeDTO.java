package de.tum.in.www1.hephaestus.practices.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.NonNull;

/**
 * DTO for exposing bad practice data to the API.
 *
 * @param id          the unique identifier
 * @param title       short description of the bad practice
 * @param description detailed explanation and remediation guidance
 * @param state       the effective state (user override takes precedence over detection)
 */
@Schema(description = "A detected bad practice in a pull request")
public record PullRequestBadPracticeDTO(
    @NonNull @Schema(description = "Unique identifier of the bad practice") Long id,
    @NonNull @Schema(description = "Short description of the bad practice") String title,
    @NonNull @Schema(description = "Detailed explanation and remediation guidance") String description,
    @NonNull
    @Schema(description = "Current state of the bad practice (DETECTED, RESOLVED, DISMISSED, etc.)")
    PullRequestBadPracticeState state
) {
    /**
     * Creates a DTO from the entity, using the effective state (user override if set).
     *
     * @param badPractice the entity to convert
     * @return the DTO representation
     */
    public static PullRequestBadPracticeDTO fromPullRequestBadPractice(PullRequestBadPractice badPractice) {
        return new PullRequestBadPracticeDTO(
            badPractice.getId(),
            badPractice.getTitle(),
            badPractice.getDescription(),
            badPractice.getEffectiveState()
        );
    }
}
