package de.tum.in.www1.hephaestus.feature;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO containing the evaluation of all feature flags for the current user.
 * <p>
 * Each field corresponds to a {@link FeatureFlag} enum constant. The field names
 * use SCREAMING_SNAKE_CASE to match the enum constant names exactly, providing a
 * direct cross-language mapping between Java and the generated TypeScript types.
 * <p>
 * The OpenAPI codegen pipeline strips the "DTO" suffix, producing a TypeScript
 * type {@code FeatureFlags} with explicit boolean properties. Frontend code can
 * then use {@code keyof FeatureFlags} as the feature flag name type for
 * compile-time safety.
 * <p>
 * <strong>Adding a new flag:</strong> Add the enum constant to {@link FeatureFlag},
 * then add the corresponding field here and wire it in {@link #from(FeatureFlagService)}.
 *
 * @see FeatureFlag
 * @see FeatureFlagController
 */
@Schema(description = "Feature flags evaluated for the current user")
public record FeatureFlagsDTO(
    // ── Authorization flags (Keycloak realm roles) ──────────────────────
    @Schema(description = "User has access to the AI Mentor feature") boolean MENTOR_ACCESS,
    @Schema(description = "User can receive notifications") boolean NOTIFICATION_ACCESS,
    @Schema(description = "User's PRs trigger automatic detection") boolean RUN_AUTOMATIC_DETECTION,
    @Schema(description = "User's PRs trigger practice review") boolean RUN_PRACTICE_REVIEW,
    @Schema(description = "User has admin privileges") boolean ADMIN,

    // ── Operational/development flags (Spring Boot config) ──────────────
    @Schema(description = "Practice review runs for all users regardless of role") boolean PRACTICE_REVIEW_FOR_ALL,
    @Schema(description = "Automatic detection runs for all users regardless of role") boolean DETECTION_FOR_ALL,
    @Schema(description = "GitLab workspace creation feature is enabled") boolean GITLAB_WORKSPACE_CREATION
) {
    /**
     * Evaluate all feature flags against the given service and build the DTO.
     *
     * @param service the feature flag service (evaluates flags for the current user)
     * @return a fully populated DTO
     */
    public static FeatureFlagsDTO from(FeatureFlagService service) {
        return new FeatureFlagsDTO(
            service.isEnabled(FeatureFlag.MENTOR_ACCESS),
            service.isEnabled(FeatureFlag.NOTIFICATION_ACCESS),
            service.isEnabled(FeatureFlag.RUN_AUTOMATIC_DETECTION),
            service.isEnabled(FeatureFlag.RUN_PRACTICE_REVIEW),
            service.isEnabled(FeatureFlag.ADMIN),
            service.isEnabled(FeatureFlag.PRACTICE_REVIEW_FOR_ALL),
            service.isEnabled(FeatureFlag.DETECTION_FOR_ALL),
            service.isEnabled(FeatureFlag.GITLAB_WORKSPACE_CREATION)
        );
    }
}
