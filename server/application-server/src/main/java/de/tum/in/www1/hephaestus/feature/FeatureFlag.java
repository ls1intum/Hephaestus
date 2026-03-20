package de.tum.in.www1.hephaestus.feature;

/**
 * Canonical registry of all feature flags in the system.
 * <p>
 * This enum is the single source of truth for feature flag names.
 * Each flag is tagged with its {@link Kind} to indicate whether it maps
 * to a Keycloak realm role or a Spring Boot configuration property.
 * <p>
 * <strong>Adding a new flag:</strong>
 * <ol>
 *   <li>Add the enum constant here with the correct kind and key</li>
 *   <li>Add a corresponding field to {@link FeatureFlagsDTO} and wire it in {@code from()}</li>
 *   <li>For {@code ROLE} flags: add the role to the Keycloak realm config
 *       and optionally to the {@code admin} composite role</li>
 *   <li>For {@code CONFIG} flags: add the property under
 *       {@code hephaestus.features.flags.<key>} in {@code application.yml}</li>
 *   <li>Run {@code npm run openapi-ts} to update the TypeScript client types</li>
 * </ol>
 *
 * @see FeatureFlagService
 * @see FeatureFlagsDTO
 */
public enum FeatureFlag {
    // ── Authorization flags (Keycloak realm roles) ──────────────────────
    MENTOR_ACCESS(Kind.ROLE, "mentor_access"),
    NOTIFICATION_ACCESS(Kind.ROLE, "notification_access"),
    RUN_AUTOMATIC_DETECTION(Kind.ROLE, "run_automatic_detection"),
    RUN_PRACTICE_REVIEW(Kind.ROLE, "run_practice_review"),
    ADMIN(Kind.ROLE, "admin"),

    // ── Operational/development flags (Spring Boot config) ──────────────
    PRACTICE_REVIEW_FOR_ALL(Kind.CONFIG, "practice-review-for-all"),
    DETECTION_FOR_ALL(Kind.CONFIG, "detection-for-all"),
    GITLAB_WORKSPACE_CREATION(Kind.CONFIG, "gitlab-workspace-creation");

    private final Kind kind;
    private final String key;

    FeatureFlag(Kind kind, String key) {
        this.kind = kind;
        this.key = key;
    }

    /**
     * The lookup key: role name for {@link Kind#ROLE} flags,
     * config property key for {@link Kind#CONFIG} flags.
     */
    public String key() {
        return key;
    }

    public Kind kind() {
        return kind;
    }

    /**
     * Indicates whether a flag is backed by a Keycloak realm role
     * or a Spring Boot configuration property.
     */
    public enum Kind {
        /** Flag backed by a Keycloak realm role on the user's JWT. */
        ROLE,
        /** Flag backed by a Spring Boot configuration property. */
        CONFIG,
    }
}
