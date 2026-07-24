package de.tum.cit.aet.hephaestus.core.audit.spi;

/**
 * The kind of admin-configurable resource a {@code config_audit_event} row describes.
 *
 * <p>Mirrored by the {@code ck_config_audit_event_entity_type} CHECK constraint;
 * {@code ConfigAuditImmutabilityIntegrationTest} reads {@code pg_constraint} on the migrated schema and
 * fails if the two drift. Widening is a changeset that drops and
 * re-adds the constraint (the shape {@code 1782980500800-15} uses for {@code auth_event}).
 */
public enum ConfigAuditEntityType {
    /** Per-workspace practice-review trigger/delivery policy overrides. */
    PRACTICE_REVIEW_SETTINGS,
    /** Which agent config powers practice detection / the mentor for a workspace. */
    AI_CONFIG_BINDING,
    /** An agent config aggregate (model, endpoint, credential mode). */
    AGENT_CONFIG,
    /**
     * A member's role or roster visibility. Covers admin-initiated grants, changes and removals, and
     * role changes applied by org sync (actor {@code SYSTEM}). Deliberately excludes memberships
     * created or removed by org sync itself: that is roster churn driven by the upstream provider, at
     * a volume that would bury the admin-initiated rows this trail exists to surface.
     */
    WORKSPACE_ROLE,
    /** Workspace feature flags (practices, mentor, achievements, …) enabled/disabled. */
    WORKSPACE_FEATURES,
    /** Workspace lifecycle status (active / paused / purged). */
    WORKSPACE_STATUS,
    /** The workspace's stored SCM access token (rotation only — the value is never recorded). */
    WORKSPACE_TOKEN,
    /** Whether the workspace is publicly viewable. */
    WORKSPACE_VISIBILITY,

    /** A practice being activated or deactivated, which gates whether it is reviewed at all. */
    PRACTICE_ACTIVE,

    /**
     * The workspace's monthly LLM budget cap. Set by instance admins, and it gates whether detection
     * and mentor turns run at all once spend reaches it — so "who raised this workspace's cap, and
     * when" is exactly the accountability question this trail answers.
     */
    WORKSPACE_LLM_BUDGET,

    /**
     * A workspace's own "bring your own" LLM provider connection (#1368): the endpoint the workspace
     * owns the key and the bill for. Workspace-admin-owned and tenant-scoped, unlike the instance
     * catalog (which is GLOBAL and therefore audited on {@code auth_event} instead — this port cannot
     * carry a null {@code workspace_id}).
     */
    WORKSPACE_LLM_CONNECTION,
    /** A model on a workspace's own BYO connection (#1368), including its inline price and enablement. */
    WORKSPACE_LLM_MODEL,
}
