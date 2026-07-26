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
    /** Which model, with what limits, runs practice detection / the mentor for a workspace. */
    AGENT_BINDING,
    /**
     * Historical only. The named-agent-config aggregate this described was deleted in #1368; the value
     * survives so rows recording what an operator once did to it keep rendering. Nothing writes it.
     */
    AGENT_CONFIG,
    /**
     * Historical only — what {@link #AGENT_BINDING} was called before #1368. Rows written under the old
     * name keep it: {@code trg_config_audit_event_block_mutation} makes this table append-only, so
     * rewriting them to the current spelling is the exact mutation that trigger exists to refuse.
     * Nothing writes this value any more; the UI renders it with the same label as
     * {@link #AGENT_BINDING}.
     */
    AI_CONFIG_BINDING,
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
     * A workspace's monthly cap on HOST-funded LLM spend. Set by instance admins, and it gates whether
     * detection and mentor turns run at all once spend reaches it — so "who raised this workspace's
     * cap, and when" is exactly the accountability question this trail answers.
     */
    WORKSPACE_INSTANCE_LLM_BUDGET,
    /** A workspace's own cap on spend through its own connected provider. Set by its own admins. */
    WORKSPACE_OWN_PROVIDER_LLM_BUDGET,
    /**
     * Historical only — the pre-#1368 spellings of the two values above. Retained for the same reason as
     * {@link #AI_CONFIG_BINDING}: this trail is append-only at the database level, so old rows keep the
     * vocabulary they were written in. Nothing writes them.
     */
    WORKSPACE_LLM_BUDGET,
    /** Historical only — see {@link #WORKSPACE_LLM_BUDGET}. */
    WORKSPACE_BYO_LLM_BUDGET,

    /**
     * A workspace's own "bring your own" LLM provider connection: the endpoint the workspace
     * owns the key and the bill for. Workspace-admin-owned and tenant-scoped, unlike the instance
     * catalog (which is GLOBAL and therefore audited on {@code auth_event} instead — this port cannot
     * carry a null {@code workspace_id}).
     */
    WORKSPACE_LLM_CONNECTION,
    /** A model on a workspace's own BYO connection, including its inline price and enablement. */
    WORKSPACE_LLM_MODEL,
}
