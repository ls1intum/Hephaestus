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
    /** Which model, with what limits, runs practice reviews / the mentor for a workspace. */
    AGENT_BINDING,
    /**
     * Historical only. {@code trg_config_audit_event_block_mutation} makes this table append-only, so
     * values no longer written still have to be readable: rewriting an old row to the current spelling is
     * the exact mutation that trigger exists to refuse.
     */
    AGENT_CONFIG,
    /** Historical only — the earlier spelling of {@link #AGENT_BINDING}. See {@link #AGENT_CONFIG}. */
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

    /** Historical only — the earlier spelling of {@link #PRACTICE_USAGE}. */
    PRACTICE_ACTIVE,
    /** Whether a practice is used in new reviews. */
    PRACTICE_USAGE,
    /** A practice's review definition, excluding its active state and catalog placement. */
    PRACTICE_DEFINITION,
    PRACTICE_AREA,
    CURATED_PRACTICE,
    CURATED_PRACTICE_AREA,

    /** A workspace's monthly cap on HOST-funded LLM spend. Set by instance admins. */
    WORKSPACE_INSTANCE_LLM_BUDGET,
    /** A workspace's own cap on spend through its own connected provider. Set by its own admins. */
    WORKSPACE_OWN_PROVIDER_LLM_BUDGET,
    /** Historical only — the earlier spellings of the two values above. See {@link #AGENT_CONFIG}. */
    WORKSPACE_LLM_BUDGET,
    /** Historical only — see {@link #WORKSPACE_LLM_BUDGET}. */
    WORKSPACE_BYO_LLM_BUDGET,

    /**
     * A campaign to review work that already existed, and the decision to let it spend.
     *
     * <p>On this trail rather than treated as ordinary activity because a backfill is the one action a
     * workspace admin can take that commits a month's LLM budget in a single request. "Who authorised
     * reviewing the last six months, and what were they told it would cost?" has to be answerable from
     * the record, not reconstructed from job rows.
     */
    REVIEW_BACKFILL_RUN,

    WORKSPACE_LLM_CONNECTION,
    /** A model on a workspace's own BYO connection, including its inline price and enablement. */
    WORKSPACE_LLM_MODEL,
}
