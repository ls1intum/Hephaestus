package de.tum.in.www1.hephaestus.practices.model;

import org.springframework.lang.NonNull;

/**
 * DTO for exposing bad practice data to the API.
 *
 * @param id          the unique identifier
 * @param title       short description of the bad practice
 * @param description detailed explanation and remediation guidance
 * @param state       the effective state (user override takes precedence over detection)
 */
public record PullRequestBadPracticeDTO(
    @NonNull Long id,
    @NonNull String title,
    @NonNull String description,
    @NonNull PullRequestBadPracticeState state
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
